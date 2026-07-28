package com.wallaceespindola.orchestrator.config;

import com.wallaceespindola.orchestrator.batch.kafka.AccountRoundRobinPartitioner;
import com.wallaceespindola.orchestrator.batch.kafka.KafkaWorkerTasklet;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.service.ReportService;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.config.annotation.EnableBatchIntegration;
import org.springframework.batch.integration.partition.RemotePartitioningManagerStepBuilderFactory;
import org.springframework.batch.integration.partition.RemotePartitioningWorkerStepBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.kafka.dsl.Kafka;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Kubernetes mode: Kafka remote partitioning. Every pod is identical — each one carries
 * both the manager beans (used only by the pod elected Master for a run) and the worker
 * beans (a Kafka consumer in the {@code batch-workers} consumer group). The manager
 * tracks worker completion by polling the shared job repository.
 */
@Configuration
@Profile("kubernetes")
@EnableBatchIntegration
public class KafkaBatchConfig {

    public static final String REQUESTS_TOPIC = "batch-partition-requests";
    private static final int GRID_SIZE = 6;

    @Bean
    public NewTopic partitionRequestsTopic() {
        // One topic partition per pod (GRID_SIZE == replica count) so each consumer in
        // the group owns exactly one.
        return new NewTopic(REQUESTS_TOPIC, GRID_SIZE, (short) 1);
    }

    // ---- Manager (master) side ----

    @Bean
    public DirectChannel partitionRequestsOut() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow outboundPartitionFlow(KafkaTemplate<String, Object> kafkaTemplate) {
        // Route each StepExecutionRequest to a distinct topic partition; Kafka's default
        // sticky partitioner would otherwise batch all null-key requests onto one
        // partition and a single worker would process everything.
        return IntegrationFlow.from(partitionRequestsOut())
                .handle(Kafka.outboundChannelAdapter(kafkaTemplate)
                        .topic(REQUESTS_TOPIC)
                        .partitionIdExpression(new org.springframework.expression.spel.standard.SpelExpressionParser()
                                .parseExpression("payload.stepExecutionId % " + GRID_SIZE)))
                .get();
    }

    @Bean
    @StepScope
    public AccountRoundRobinPartitioner accountPartitioner(
            AccountRepository accountRepository,
            @Value("#{jobParameters['masterId']}") String masterId) {
        return new AccountRoundRobinPartitioner(accountRepository, masterId);
    }

    @Bean
    public Step managerStep(RemotePartitioningManagerStepBuilderFactory managerStepBuilderFactory,
                            AccountRoundRobinPartitioner accountPartitioner) {
        return managerStepBuilderFactory.get("managerStep")
                .partitioner("workerStep", accountPartitioner)
                .gridSize(GRID_SIZE)
                .outputChannel(partitionRequestsOut())
                .pollInterval(3_000)
                .build();
    }

    @Bean
    public Job accountReportJob(JobRepository jobRepository, Step managerStep,
                                JobExecutionListener masterLockReleaseListener) {
        return new JobBuilder(BatchConfig.JOB_NAME, jobRepository)
                .listener(masterLockReleaseListener)
                .start(managerStep)
                .build();
    }

    // ---- Worker side ----

    @Bean
    public DirectChannel partitionRequestsIn() {
        return new DirectChannel();
    }

    @Bean
    public IntegrationFlow inboundPartitionFlow(ConsumerFactory<String, Object> consumerFactory) {
        return IntegrationFlow
                .from(Kafka.messageDrivenChannelAdapter(consumerFactory, REQUESTS_TOPIC))
                .channel(partitionRequestsIn())
                .get();
    }

    @Bean
    @StepScope
    public KafkaWorkerTasklet kafkaWorkerTasklet(
            ReportService reportService,
            @Value("#{stepExecutionContext['accountIds']}") List<Long> accountIds,
            @Value("#{stepExecutionContext['partitionKey']}") String partitionKey,
            @Value("#{stepExecutionContext['masterId']}") String masterId) {
        return new KafkaWorkerTasklet(reportService, accountIds, partitionKey, masterId);
    }

    @Bean
    public Step workerStep(RemotePartitioningWorkerStepBuilderFactory workerStepBuilderFactory,
                           KafkaWorkerTasklet kafkaWorkerTasklet,
                           PlatformTransactionManager transactionManager) {
        return workerStepBuilderFactory.get("workerStep")
                .inputChannel(partitionRequestsIn())
                .tasklet(kafkaWorkerTasklet, transactionManager)
                .build();
    }
}
