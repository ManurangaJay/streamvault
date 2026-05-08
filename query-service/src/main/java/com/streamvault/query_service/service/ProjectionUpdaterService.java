package com.streamvault.query_service.service;

import com.streamvault.query_service.domain.AccountProjection;
import com.streamvault.query_service.domain.ProcessedEvent;
import com.streamvault.query_service.domain.TransactionProjection;
import com.streamvault.query_service.event.*;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import com.streamvault.query_service.repository.ProcessedEventRepository;
import com.streamvault.query_service.repository.TransactionProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionUpdaterService {

    private final AccountProjectionRepository accountProjectionRepository;
    private final TransactionProjectionRepository transactionProjectionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void processMoneyDeposited(MoneyDeposited event) {

        if (processedEventRepository.existsById(event.getEventId())) {
            log.warn("Idempotency triggered: MoneyDeposited event {} already processed. Skipping.", event.getEventId());
            return;
        }

        UUID accountId = event.getAggregateId();

        AccountProjection account = accountProjectionRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account projection not found for ID: " + accountId));

        BigDecimal newBalance = account.getBalance().add(event.getAmount());

        account.setBalance(newBalance);
        account.setTransactionCount(account.getTransactionCount() + 1);
        account.setLastUpdatedAt(event.getOccurredAt());

        accountProjectionRepository.save(account);

        TransactionProjection transaction = new TransactionProjection();

        transaction.setId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID());
        transaction.setAccountId(accountId);
        transaction.setEventType("MoneyDeposited");
        transaction.setAmount(event.getAmount());
        transaction.setDirection("CREDIT");
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription("Deposit via Command API");
        transaction.setCreatedAt(event.getOccurredAt());
        transaction.setCorrelationId(event.getCorrelationId());

        transactionProjectionRepository.save(transaction);

        processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now()));

        String redisKey = "balance::" + accountId;
        Map<String, Object> balanceCache = Map.of(
                "balance", newBalance,
                "currency", account.getCurrency(),
                "lastUpdate", event.getOccurredAt()
        );
        redisTemplate.opsForValue().set(redisKey, balanceCache);
        log.info("Successfully projected MoneyDeposited for account {}. New balance: {} ", accountId, newBalance);
    }

    @Transactional
    public void processMoneyWithdrawn(MoneyWithdrawn event) {

        if (processedEventRepository.existsById(event.getEventId())) {
            log.warn("Idempotency triggered: MoneyWithdrawn event {} already processed. Skipping.", event.getEventId());
            return;
        }

        UUID accountId = event.getAggregateId();

        AccountProjection account = accountProjectionRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account projection not found for ID: " + accountId));

        BigDecimal newBalance = account.getBalance().subtract(event.getAmount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Projection Error: MoneyWithdrawn event " + event.getEventId() +
                            "would cause a negative balance for account " + accountId
            );
        }

        account.setBalance(newBalance);
        account.setTransactionCount(account.getTransactionCount() + 1);
        account.setLastUpdatedAt(event.getOccurredAt());
        accountProjectionRepository.save(account);

        TransactionProjection transaction = new TransactionProjection();
        transaction.setId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID());
        transaction.setAccountId(accountId);
        transaction.setEventType("MoneyWithdrawnEvent");
        transaction.setAmount(event.getAmount());
        transaction.setDirection("DEBIT");
        transaction.setBalanceAfter(newBalance);
        transaction.setDirection("Withdrawal via Command API");
        transaction.setCreatedAt(event.getOccurredAt());
        transaction.setCorrelationId(event.getCorrelationId());

        transactionProjectionRepository.save(transaction);

        processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now()));

        String redisKey = "balance::" + accountId;
        Map<String, Object> balanceCache = Map.of(
                "balance", newBalance,
                "currency", account.getCurrency(),
                "lastUpdated", event.getOccurredAt()
        );
        redisTemplate.opsForValue().set(redisKey, balanceCache);

        log.info("Successfully projected MoneyWithdrawn for account {}. New balance: {}", accountId, newBalance);
    }

    @Transactional
    public void processMoneyTransferred(MoneyTransferred event) {

        if (processedEventRepository.existsById(event.getEventId())) {
            log.warn("Idempotency triggered: MoneyTransferred event {} already processed. Skipping.", event.getEventId());
            return;
        }

        UUID sourceAccountId = event.getAggregateId();
        UUID targetAccountId = event.getTargetAccountId();

        AccountProjection sourceAccount = accountProjectionRepository.findById(sourceAccountId)
                .orElseThrow(() -> new IllegalStateException("Source account not found: " + sourceAccountId));

        AccountProjection targetAccount = accountProjectionRepository.findById(targetAccountId)
                .orElseThrow(() -> new IllegalStateException("Target account not found: " + targetAccountId));

        BigDecimal newSourceBalance = sourceAccount.getBalance().subtract(event.getAmount());
        BigDecimal newTargetBalance = targetAccount.getBalance().add(event.getAmount());

        if (newSourceBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Projection Error: Transfer event " + event.getEventId() +
                            "would cause a negative for source account " + sourceAccountId
            );
        }

        sourceAccount.setBalance(newSourceBalance);
        sourceAccount.setTransactionCount(sourceAccount.getTransactionCount() + 1);
        sourceAccount.setLastUpdatedAt(event.getOccurredAt());

        targetAccount.setBalance(newTargetBalance);
        targetAccount.setTransactionCount(targetAccount.getTransactionCount() + 1);
        targetAccount.setLastUpdatedAt(event.getOccurredAt());

        accountProjectionRepository.saveAll(List.of(sourceAccount, targetAccount));

        TransactionProjection sourceTx = new TransactionProjection();
        sourceTx.setId(UUID.randomUUID());
        sourceTx.setAccountId(sourceAccountId);
        sourceTx.setEventType("MoneyTransferred");
        sourceTx.setAmount(event.getAmount());
        sourceTx.setDirection("DEBIT");
        sourceTx.setBalanceAfter(newSourceBalance);
        sourceTx.setDescription("Transferred to: " + targetAccountId);
        sourceTx.setCreatedAt(event.getOccurredAt());
        sourceTx.setCorrelationId(event.getCorrelationId());

        TransactionProjection targetTx = new TransactionProjection();
        targetTx.setId(UUID.randomUUID());
        targetTx.setAccountId(targetAccountId);
        targetTx.setEventType("MoneyTransferred");
        targetTx.setAmount(event.getAmount());
        targetTx.setDirection("CREDIT");
        targetTx.setBalanceAfter(newTargetBalance);
        targetTx.setDescription("Transferred from: " + targetAccountId);
        targetTx.setCreatedAt(event.getOccurredAt());
        targetTx.setCorrelationId(event.getCorrelationId());

        transactionProjectionRepository.saveAll(List.of(sourceTx, targetTx));

        processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now()));

        String sourceRedisKey = "balance::" + sourceAccountId;
        String targetRedisKey = "balance::" + targetAccountId;

        Map<String, Object> sourceCache = Map.of(
                "balance", newSourceBalance,
                "currency", sourceAccount.getCurrency(),
                "lastUpdate", event.getOccurredAt()
        );
        Map<String, Object> targetCache = Map.of(
                "balance", newTargetBalance,
                "currency", targetAccount.getCurrency(),
                "lastUpdate", event.getOccurredAt()
        );

        redisTemplate.opsForValue().multiSet(Map.of(
                sourceRedisKey, sourceCache,
                targetRedisKey, targetCache
        ));

        log.info("Successfully projected MoneyTransferred. Source {}. Target {}", sourceAccountId, targetAccountId);
    }

    @Transactional
    public void processAccountClosed(AccountClosed event) {

        if (processedEventRepository.existsById(event.getEventId())) {
            log.warn("Idempotency triggered: AccountClosed event {} already processed. Skipping.", event.getEventId());
            return;
        }

        UUID accountId = event.getAggregateId();

        AccountProjection account = accountProjectionRepository.findById(accountId).orElseThrow(
                () -> new IllegalStateException("Account projection not found for ID: " + accountId)
        );

        account.setStatus("CLOSED");
        account.setLastUpdatedAt(event.getOccurredAt());

        accountProjectionRepository.save(account);

        processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now()));

        String redisKey = "balance::" + accountId;

        redisTemplate.delete(redisKey);

        log.info("Successfully projected AccountClosed for account {}. Cache evicted.", accountId);
    }

    @Transactional
    public void processAccountCreated(AccountCreated event) {

        if (processedEventRepository.existsById(event.getEventId())) {
            log.warn("Idempotency triggered: AccountCreated event {} already processed. Skipping.", event.getEventId());
            return;
        }

        AccountProjection projection = new AccountProjection();
        projection.setId(event.getAggregateId());
        projection.setOwnerId(event.getOwnerId());
        projection.setBalance(BigDecimal.ZERO);
        projection.setCurrency(event.getCurrency());
        projection.setAccountType(event.getAccountType());
        projection.setStatus("ACTIVE");
        projection.setTransactionCount(0L);
        projection.setLastUpdatedAt(event.getOccurredAt());

        accountProjectionRepository.save(projection);
        log.info("PostgreSQL projection created for account: {}", event.getAggregateId());

        processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now()));

        String redisKey = "balance::" + event.getAggregateId();

        Map<String, Object> balanceCache = Map.of(
                "balance", BigDecimal.ZERO,
                "currency", projection.getCurrency(),
                "lastUpdated", event.getOccurredAt()
        );

        redisTemplate.opsForValue().set(redisKey, balanceCache);
        log.info("Redis cache initialized at {} for account: {}", redisKey, event.getAggregateId());
    }
}
