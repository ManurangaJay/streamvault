package com.streamvault.query_service.controller;

import com.streamvault.query_service.dto.AccountSummaryResponse;
import com.streamvault.query_service.service.AccountQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}
