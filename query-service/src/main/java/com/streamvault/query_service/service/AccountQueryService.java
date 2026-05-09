package com.streamvault.query_service.service;

import com.streamvault.query_service.domain.AccountProjection;
import com.streamvault.query_service.domain.TransactionProjection;
import com.streamvault.query_service.dto.AccountSummaryResponse;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import com.streamvault.query_service.repository.TransactionProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final TransactionProjectionRepository transactionProjectionRepository;
    private final AccountProjectionRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<AccountSummaryResponse> getUserAccounts(UUID userId) {

        List<AccountProjection> projections = repository.findByOwnerIdOrderByLastUpdatedAtDesc(userId);

        return projections.stream().map(account -> {
            BigDecimal currentBalance = account.getBalance();
            Instant lastUpdated = account.getLastUpdatedAt();

            String redisKey = "balance::" + account.getId();

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cache = (Map<String, Object>) redisTemplate.opsForValue().get(redisKey);

                if (cache != null && cache.containsKey("balance")) {
                    currentBalance = new BigDecimal(cache.get("balance").toString());

                    if (cache.containsKey("lastUpdated")) {
                        lastUpdated = Instant.parse(cache.get("lastUpated").toString());
                    }
                }
            } catch (Exception e) {
                log.warn("Redis cache read failed for key {}. Falling back to DB projection.", redisKey, e);
            }
            return new AccountSummaryResponse(
                    account.getId(),
                    currentBalance,
                    account.getCurrency(),
                    account.getAccountType(),
                    account.getStatus(),
                    account.getTransactionCount(),
                    lastUpdated
            );
        }).toList();
    }

    public AccountSummaryResponse getAccountById(UUID accountId, UUID userId) {
        AccountProjection account = repository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getOwnerId().equals(userId)) {
            log.warn("Security Event: User {} attempted to access account {} owned by {}", userId, accountId, account.getOwnerId());
        }

        String redisKey = "balance::" + account.getId();
        BigDecimal currentBalance = account.getBalance();
        Instant lastUpdated = account.getLastUpdatedAt();
        boolean cacheHit = false;

        try {
            @SuppressWarnings("unchecked")
                    Map<String, Object> cache = (Map<String, Object>) redisTemplate.opsForValue().get(redisKey);

            if (cache != null && cache.containsKey("balance")) {
                currentBalance = new BigDecimal(cache.get("balance").toString());
                if (cache.containsKey("lastUpdated")) {
                    lastUpdated = Instant.parse(cache.get("lastUpdated").toString());
                }
                cacheHit = true;
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {}. Falling bath to DB projection.", redisKey, e);
        }

        if (!cacheHit) {
            Map<String, Object> balanceCache = Map.of(
                    "balance", account.getBalance(),
                    "currency", account.getCurrency(),
                    "lastUpdated", account.getLastUpdatedAt()
            );

            redisTemplate.opsForValue().set(redisKey, balanceCache, Duration.ofMinutes(5));
            log.info("Cache miss for account {}. State re-cached from DB.", accountId);
        }

        return new AccountSummaryResponse(
                account.getId(),
                currentBalance,
                account.getCurrency(),
                account.getAccountType(),
                account.getStatus(),
                account.getTransactionCount(),
                lastUpdated
        );
    }

    public Page<TransactionProjection> getAccountTransactions(UUID accountId, UUID userId, Pageable pageable) {

        AccountProjection account = repository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getOwnerId().equals(userId)) {
            log.warn("Security Event: User {} attempted to access transaction history for account {}", userId, accountId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        return transactionProjectionRepository.findByAccountId(accountId, pageable);
    }
}
