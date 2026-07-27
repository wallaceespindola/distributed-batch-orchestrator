package com.wallaceespindola.orchestrator.repository;

import com.wallaceespindola.orchestrator.domain.PartitionAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartitionAssignmentRepository extends JpaRepository<PartitionAssignment, Long> {

    List<PartitionAssignment> findByJobExecutionIdOrderByPartitionKey(Long jobExecutionId);

    Optional<PartitionAssignment> findByJobExecutionIdAndPartitionKey(Long jobExecutionId, String partitionKey);

    long countByJobExecutionId(Long jobExecutionId);
}
