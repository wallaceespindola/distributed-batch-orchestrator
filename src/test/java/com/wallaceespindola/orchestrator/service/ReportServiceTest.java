package com.wallaceespindola.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.orchestrator.domain.AccountReport;
import com.wallaceespindola.orchestrator.domain.BankTransaction;
import com.wallaceespindola.orchestrator.domain.PartitionAssignment;
import com.wallaceespindola.orchestrator.repository.AccountReportRepository;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.repository.BankTransactionRepository;
import com.wallaceespindola.orchestrator.repository.PartitionAssignmentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:report-test;DB_CLOSE_DELAY=-1",
        "app.data.generate-on-startup=false",
        "app.batch.simulated-work-ms-per-account=0",
        "app.instance-id=worker-under-test"
})
class ReportServiceTest {

    @Autowired
    private ReportService reportService;
    @Autowired
    private DataGeneratorService dataGeneratorService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private BankTransactionRepository transactionRepository;
    @Autowired
    private AccountReportRepository reportRepository;
    @Autowired
    private PartitionAssignmentRepository assignmentRepository;

    @Test
    void generatesOneReportPerAccountAndRecordsWorkerAttribution() {
        dataGeneratorService.generate(10);
        List<Long> accountIds = accountRepository.findAllIds();
        long jobExecutionId = 999L;

        reportService.processPartition(jobExecutionId, "partition-0", "master-x", accountIds);

        List<AccountReport> reports = reportRepository
                .findByJobExecutionIdOrderByAccountId(jobExecutionId);
        assertThat(reports).hasSize(10);
        assertThat(reports).allSatisfy(report -> {
            assertThat(report.getWorkerId()).isEqualTo("worker-under-test");
            assertThat(report.getPartitionKey()).isEqualTo("partition-0");
        });

        PartitionAssignment assignment = assignmentRepository
                .findByJobExecutionIdAndPartitionKey(jobExecutionId, "partition-0").orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo(PartitionAssignment.Status.COMPLETED);
        assertThat(assignment.getWorkerId()).isEqualTo("worker-under-test");
        assertThat(assignment.getMasterId()).isEqualTo("master-x");
        assertThat(assignment.getAccountCount()).isEqualTo(10);
        assertThat(assignment.getStartedAt()).isNotNull();
        assertThat(assignment.getFinishedAt()).isNotNull();
    }

    @Test
    void reportTotalsMatchTransactions() {
        dataGeneratorService.generate(3);
        Long accountId = accountRepository.findAllIds().get(0);

        reportService.processPartition(1000L, "partition-0", "master-x", List.of(accountId));

        AccountReport report = reportRepository.findByJobExecutionIdOrderByAccountId(1000L).get(0);
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        for (BankTransaction tx : transactionRepository.findByAccountId(accountId)) {
            if (tx.getTxType() == BankTransaction.TxType.CREDIT) {
                credits = credits.add(tx.getAmount());
            } else {
                debits = debits.add(tx.getAmount());
            }
        }
        assertThat(report.getTotalCredits()).isEqualByComparingTo(credits);
        assertThat(report.getTotalDebits()).isEqualByComparingTo(debits);
        assertThat(report.getTransactionCount())
                .isEqualTo(transactionRepository.findByAccountId(accountId).size());
        assertThat(report.getEndingBalance()).isEqualByComparingTo(
                accountRepository.findById(accountId).orElseThrow().getBalance()
                        .add(credits).subtract(debits));
    }

    @Test
    void failedPartitionIsMarkedFailed() {
        dataGeneratorService.generate(2);
        List<Long> bogus = List.of(999_999L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        reportService.processPartition(2000L, "partition-0", "master-x", bogus))
                .isInstanceOf(IllegalStateException.class);

        PartitionAssignment assignment = assignmentRepository
                .findByJobExecutionIdAndPartitionKey(2000L, "partition-0").orElseThrow();
        assertThat(assignment.getStatus()).isEqualTo(PartitionAssignment.Status.FAILED);
    }
}
