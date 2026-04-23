package com.streamvault.command_service.repository;

import com.streamvault.command_service.domain.aggregate.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}