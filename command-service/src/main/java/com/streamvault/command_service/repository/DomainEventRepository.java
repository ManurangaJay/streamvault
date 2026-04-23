package com.streamvault.command_service.repository;

import com.streamvault.command_service.domain.entity.DomainEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainEventRepository extends JpaRepository<DomainEventRecord, Long> {
}
