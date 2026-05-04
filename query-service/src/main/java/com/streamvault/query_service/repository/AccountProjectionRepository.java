package com.streamvault.query_service.repository;

import com.streamvault.query_service.domain.AccountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountProjectionRepository  extends JpaRepository<AccountProjection, UUID> {
}
