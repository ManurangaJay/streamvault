package com.streamvault.command_service.repository;

import com.streamvault.command_service.domain.entity.DomainEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainEventRepository extends JpaRepository<DomainEventRecord, Long> {
    Optional<DomainEventRecord> findTopByAggregateIdOrderByEventVersionDesc(UUID aggregateId);
    List<DomainEventRecord> findByAggregateIdOrderByEventVersionAsc(UUID aggregateId);
}
