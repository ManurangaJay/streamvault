package com.streamvault.query_service.service;

import com.streamvault.query_service.domain.AccountProjection;
import com.streamvault.query_service.domain.TransactionProjection;
import com.streamvault.query_service.event.MoneyDeposited;
import com.streamvault.query_service.event.MoneyWithdrawn;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import com.streamvault.query_service.repository.TransactionProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectionUpdaterService {

    private final AccountProjectionRepository accountProjectionRepository;
    private final TransactionProjectionRepository transactionProjectionRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void processMoneyDeposited(MoneyDeposited event) {
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

        String redisKey = "balance::" + accountId;
        Map<String, Object> balanceCache = Map.of(
                "balance", newBalance,
                "currency", account.getCurrency(),
                "lastUpdated", event.getOccurredAt()
        );
        redisTemplate.opsForValue().set(redisKey, balanceCache);

        log.info("Successfully projected MoneyWithdrawn for account {}. New balance: {}", accountId, newBalance);
    }
}
