package com.streamvault.query_service.controller;

import com.streamvault.query_service.domain.TransactionProjection;
import com.streamvault.query_service.dto.AccountSummaryResponse;
import com.streamvault.query_service.service.AccountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountQueryController {

    private final AccountQueryService queryService;

    @GetMapping
    public ResponseEntity<List<AccountSummaryResponse>> getUserAccounts(@AuthenticationPrincipal Principal principal) {
        UUID userId = UUID.fromString(principal.getName());

        List<AccountSummaryResponse> accounts = queryService.getUserAccounts(userId);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountSummaryResponse> getAccountById(
            @PathVariable UUID id,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());

        AccountSummaryResponse account = queryService.getAccountById(id, userId);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionProjection>> getAccountTransactions(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)Pageable pageable,
            Principal principal
            ) {
        UUID userId = UUID.fromString(principal.getName());

        Page<TransactionProjection> transactions = queryService.getAccountTransactions(id, userId, pageable);

        return ResponseEntity.ok(transactions);
    }
}
