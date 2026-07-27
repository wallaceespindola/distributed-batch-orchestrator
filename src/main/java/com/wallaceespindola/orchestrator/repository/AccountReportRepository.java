package com.wallaceespindola.orchestrator.repository;

import com.wallaceespindola.orchestrator.domain.AccountReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountReportRepository extends JpaRepository<AccountReport, Long> {

    List<AccountReport> findByJobExecutionIdOrderByAccountId(Long jobExecutionId);

    long countByJobExecutionId(Long jobExecutionId);
}
