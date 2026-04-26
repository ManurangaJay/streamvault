package com.streamvault.command_service.controller;

import com.streamvault.command_service.domain.command.*;
import com.streamvault.command_service.handler.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.MalformedParameterizedTypeException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final CreateAccountHandler createAccountHandler;
    private final DepositMoneyHandler depositMoneyHandler;
    private final WithdrawMoneyHandler withdrawMoneyHandler;
    private final TransferMoneyHandler transferMoneyHandler;
    private final CloseAccountHandler closeAccountHandler;

    @PostMapping
    public ResponseEntity<Map<String, UUID>> createAccount(@Valid @RequestBody CreateAccountCommand command) {
        UUID accountId = createAccountHandler.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("accountId", accountId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<Void> depositMoney(@Valid @RequestBody DepositMoneyCommand command) {
        depositMoneyHandler.handle(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("withdraw")
    public ResponseEntity<Void> withdrawMoney(@Valid @RequestBody WithdrawMoneyCommand command) {
        withdrawMoneyHandler.handle(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    public ResponseEntity<Void> transferMoney(@Valid @RequestBody TransferMoneyCommand command) {
        transferMoneyHandler.handle(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/close")
    public ResponseEntity<Void> closeAccount(@Valid @RequestBody CloseAccountCommand command) {
        closeAccountHandler.handle(command);
        return ResponseEntity.ok().build();
    }
}
