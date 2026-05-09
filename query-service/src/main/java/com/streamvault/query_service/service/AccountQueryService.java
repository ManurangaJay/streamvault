package com.streamvault.query_service.service;

import com.streamvault.query_service.domain.AccountProjection;
import com.streamvault.query_service.dto.AccountSummaryResponse;
import com.streamvault.query_service.repository.AccountProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountQueryService {

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
}
