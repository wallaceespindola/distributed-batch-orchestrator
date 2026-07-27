package com.wallaceespindola.orchestrator.service;

import com.wallaceespindola.orchestrator.domain.PartitionAssignment;
import com.wallaceespindola.orchestrator.repository.AccountReportRepository;
import com.wallaceespindola.orchestrator.repository.PartitionAssignmentRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BatchStatusService {

    public static final String JOB_NAME = "accountReportJob";

    private final JobExplorer jobExplorer;
    private final PartitionAssignmentRepository assignmentRepository;
    private final AccountReportRepository reportRepository;

    public record PartitionView(String partitionKey, String workerId, String masterId,
                                int accountCount, String status,
                                java.time.Instant startedAt, java.time.Instant finishedAt) {
    }

    public record RunStatus(long jobExecutionId, String status, String masterId,
                            LocalDateTime startTime, LocalDateTime endTime,
                            long reportsGenerated, List<PartitionView> partitions) {
    }

    public record RunSummary(long jobExecutionId, String status, String masterId,
                             LocalDateTime startTime, LocalDateTime endTime,
                             long partitionCount, long reportCount) {
    }

    public Optional<RunStatus> latestRun() {
        return latestExecution().map(this::toStatus);
    }

    public Optional<RunStatus> run(long jobExecutionId) {
        return Optional.ofNullable(jobExplorer.getJobExecution(jobExecutionId)).map(this::toStatus);
    }

    public List<RunSummary> history(int limit) {
        List<RunSummary> result = new ArrayList<>();
        for (JobInstance instance : jobExplorer.getJobInstances(JOB_NAME, 0, limit)) {
            for (JobExecution execution : jobExplorer.getJobExecutions(instance)) {
                result.add(new RunSummary(execution.getId(), execution.getStatus().name(),
                        masterId(execution), execution.getStartTime(), execution.getEndTime(),
                        assignmentRepository.countByJobExecutionId(execution.getId()),
                        reportRepository.countByJobExecutionId(execution.getId())));
            }
        }
        result.sort(Comparator.comparingLong(RunSummary::jobExecutionId).reversed());
        return result;
    }

    private Optional<JobExecution> latestExecution() {
        return jobExplorer.getJobInstances(JOB_NAME, 0, 1).stream()
                .flatMap(instance -> jobExplorer.getJobExecutions(instance).stream())
                .max(Comparator.comparingLong(JobExecution::getId));
    }

    private RunStatus toStatus(JobExecution execution) {
        List<PartitionView> partitions = assignmentRepository
                .findByJobExecutionIdOrderByPartitionKey(execution.getId()).stream()
                .map(this::toView)
                .toList();
        return new RunStatus(execution.getId(), execution.getStatus().name(), masterId(execution),
                execution.getStartTime(), execution.getEndTime(),
                reportRepository.countByJobExecutionId(execution.getId()), partitions);
    }

    private PartitionView toView(PartitionAssignment a) {
        return new PartitionView(a.getPartitionKey(), a.getWorkerId(), a.getMasterId(),
                a.getAccountCount(), a.getStatus().name(), a.getStartedAt(), a.getFinishedAt());
    }

    private String masterId(JobExecution execution) {
        String masterId = execution.getJobParameters().getString("masterId");
        return masterId != null ? masterId : "unknown";
    }
}
