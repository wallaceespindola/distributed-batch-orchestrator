package com.wallaceespindola.orchestrator.service;

import com.wallaceespindola.orchestrator.config.AppProperties;
import com.wallaceespindola.orchestrator.domain.Account;
import com.wallaceespindola.orchestrator.domain.AccountReport;
import com.wallaceespindola.orchestrator.domain.BankTransaction;
import com.wallaceespindola.orchestrator.domain.PartitionAssignment;
import com.wallaceespindola.orchestrator.repository.AccountReportRepository;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.repository.BankTransactionRepository;
import com.wallaceespindola.orchestrator.repository.PartitionAssignmentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Worker-side processing: generates one report per account and records which worker
 * handled the partition. Shared by both execution modes (local HTTP dispatch and
 * Kafka remote partitioning).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final AccountReportRepository reportRepository;
    private final PartitionAssignmentRepository assignmentRepository;
    private final AppProperties properties;

    public void processPartition(long jobExecutionId, String partitionKey, String masterId,
                                 List<Long> accountIds) {
        String workerId = properties.instanceId();
        PartitionAssignment assignment = assignmentRepository.save(PartitionAssignment.builder()
                .jobExecutionId(jobExecutionId)
                .partitionKey(partitionKey)
                .workerId(workerId)
                .masterId(masterId)
                .accountCount(accountIds.size())
                .status(PartitionAssignment.Status.RUNNING)
                .startedAt(Instant.now())
                .build());
        log.info("[{}] processing partition {} of job {} ({} accounts)",
                workerId, partitionKey, jobExecutionId, accountIds.size());
        try {
            for (Long accountId : accountIds) {
                reportForAccount(jobExecutionId, partitionKey, workerId, accountId);
                simulateWork();
            }
            assignment.setStatus(PartitionAssignment.Status.COMPLETED);
        } catch (RuntimeException e) {
            assignment.setStatus(PartitionAssignment.Status.FAILED);
            throw e;
        } finally {
            assignment.setFinishedAt(Instant.now());
            assignmentRepository.save(assignment);
        }
    }

    private void reportForAccount(long jobExecutionId, String partitionKey, String workerId,
                                  Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));
        List<BankTransaction> transactions = transactionRepository.findByAccountId(accountId);

        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        for (BankTransaction tx : transactions) {
            if (tx.getTxType() == BankTransaction.TxType.CREDIT) {
                credits = credits.add(tx.getAmount());
            } else {
                debits = debits.add(tx.getAmount());
            }
        }

        reportRepository.save(AccountReport.builder()
                .jobExecutionId(jobExecutionId)
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .workerId(workerId)
                .partitionKey(partitionKey)
                .transactionCount(transactions.size())
                .totalCredits(credits)
                .totalDebits(debits)
                .endingBalance(account.getBalance().add(credits).subtract(debits))
                .generatedAt(Instant.now())
                .build());
    }

    private void simulateWork() {
        long delay = properties.batch().simulatedWorkMsPerAccount();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Partition processing interrupted", e);
        }
    }
}
