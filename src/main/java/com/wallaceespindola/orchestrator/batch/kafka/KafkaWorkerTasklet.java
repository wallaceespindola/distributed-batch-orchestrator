package com.wallaceespindola.orchestrator.batch.kafka;

import com.wallaceespindola.orchestrator.service.ReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Worker side of Kafka remote partitioning: consumes a partition's execution context
 * and delegates to the same {@link ReportService} used by local mode.
 */
@RequiredArgsConstructor
public class KafkaWorkerTasklet implements Tasklet {

    private final ReportService reportService;
    private final List<Long> accountIds;
    private final String partitionKey;
    private final String masterId;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long jobExecutionId = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getId();
        reportService.processPartition(jobExecutionId, partitionKey, masterId, accountIds);
        return RepeatStatus.FINISHED;
    }
}
