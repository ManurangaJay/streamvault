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
        transaction.setDescription("Withdrawal via Command API");
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

        UUID aggregateId = event.getAggregateId();
        boolean isDebit = aggregateId.equals(event.getSourceAccountId());

        AccountProjection account = accountProjectionRepository.findById(aggregateId)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + aggregateId));

        BigDecimal newBalance;
        TransactionProjection transaction = new TransactionProjection();
        transaction.setId(UUID.randomUUID());
        transaction.setAccountId(aggregateId);
        transaction.setEventType("MoneyTransferred");
        transaction.setAmount(event.getAmount());
        transaction.setCreatedAt(event.getOccurredAt());
        transaction.setCorrelationId(event.getCorrelationId());

        if (isDebit) {
            // Process the Source (Debit) Side
            newBalance = account.getBalance().subtract(event.getAmount());
            transaction.setDirection("DEBIT");
            transaction.setBalanceAfter(newBalance);
            transaction.setDescription("Transferred to: " + event.getTargetAccountId());
        } else {
            // Process the Target (Credit) Side
            newBalance = account.getBalance().add(event.getAmount());
            transaction.setDirection("CREDIT");
            transaction.setBalanceAfter(newBalance);
            transaction.setDescription("Transferred from: " + event.getSourceAccountId());
        }

        // Update Account Projection
        account.setBalance(newBalance);
        account.setTransactionCount(account.getTransactionCount() + 1);
        account.setLastUpdatedAt(event.getOccurredAt());
        accountProjectionRepository.save(account);

        // Save Transaction History
        transactionProjectionRepository.save(transaction);

        // 3. Mark Event as Processed (Idempotency)
        processedEventRepository.save(new ProcessedEvent(event.getEventId(), Instant.now()));

        // Update Redis Cache
        String redisKey = "balance::" + aggregateId;
        Map<String, Object> cacheData = Map.of(
                "balance", newBalance,
                "currency", account.getCurrency(),
                "lastUpdate", event.getOccurredAt()
        );
        redisTemplate.opsForValue().set(redisKey, cacheData);

        log.info("Successfully projected MoneyTransferred ({} side) for account {}. New balance: {}",
                isDebit ? "Source" : "Target", aggregateId, newBalance);
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
