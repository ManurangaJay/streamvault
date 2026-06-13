package com.streamvault.websocket_gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionRegistryService {

    private final StringRedisTemplate redisTemplate;

    private static final String SESSIONS_BY_ACCOUNT_PREFIX = "ws:sessions:";
    private static final String ACCOUNT_BY_SESSION_PREFIX = "ws:session:";

    public void registerSession(String accountId, String sessionId) {
        String accountKey = SESSIONS_BY_ACCOUNT_PREFIX + accountId;
        String sessionKey = ACCOUNT_BY_SESSION_PREFIX + sessionId;

        redisTemplate.opsForValue().set(accountKey, sessionId);

        redisTemplate.opsForValue().set(sessionKey, accountId);

        log.info("Registered WS session [{}] for account [{}]", sessionId, accountId);
    }

    public void unregisterSession(String sessionId) {
        String sessionKey = ACCOUNT_BY_SESSION_PREFIX + sessionId;

        String accountId = redisTemplate.opsForValue().get(sessionKey);

        if (accountId != null) {
            String accountKey = SESSIONS_BY_ACCOUNT_PREFIX + accountId;

            redisTemplate.opsForSet().remove(accountKey, sessionId);

            redisTemplate.delete(sessionKey);

            log.info("Unregistered WS session [{}] for account [{}]", sessionId, accountId);
        } else {
            log.warn("Attempted to unregister untracked WS session [{}]", sessionId);
        }
    }
}
