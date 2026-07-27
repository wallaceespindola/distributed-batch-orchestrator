package com.wallaceespindola.orchestrator.config;

import com.wallaceespindola.orchestrator.batch.local.LocalDispatchTasklet;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.service.ClusterService;
import com.wallaceespindola.orchestrator.service.MasterLockHolder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    public static final String JOB_NAME = "accountReportJob";

    /** Async launcher so POST /api/batch/start returns immediately. */
    @Bean
    @Primary
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-run-"));
        launcher.afterPropertiesSet();
        return launcher;
    }

    /** Releases the master lock when the run ends, so the role rotates between runs. */
    @Bean
    public JobExecutionListener masterLockReleaseListener(MasterLockHolder lockHolder) {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                lockHolder.release();
            }
        };
    }

    @Bean
    @Profile("local")
    public Step localDispatchStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  AccountRepository accountRepository,
                                  ClusterService clusterService,
                                  AppProperties properties) {
        return new StepBuilder("localDispatchStep", jobRepository)
                .tasklet(new LocalDispatchTasklet(accountRepository, clusterService, properties),
                        transactionManager)
                .build();
    }

    @Bean
    @Profile("local")
    public Job accountReportJob(JobRepository jobRepository, Step localDispatchStep,
                                JobExecutionListener masterLockReleaseListener) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(masterLockReleaseListener)
                .start(localDispatchStep)
                .build();
    }
}
