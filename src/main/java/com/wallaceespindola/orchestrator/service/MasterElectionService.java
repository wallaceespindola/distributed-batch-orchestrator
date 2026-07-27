package com.wallaceespindola.orchestrator.service;

import com.wallaceespindola.orchestrator.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

/**
 * Dynamic master election: all 4 instances are identical, and the first one to receive
 * a processing request grabs the ShedLock lock and acts as Master for that run. The
 * lock guarantees a single active master; it is released by the job-completion
 * listener, so the master role naturally rotates between runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterElectionService {

    private final MasterLockHolder lockHolder;
    private final JobLauncher jobLauncher;
    private final Job accountReportJob;
    private final AppProperties properties;

    public record RunStarted(long jobExecutionId, String masterId, String status) {
    }

    public RunStarted startRun() {
        if (!lockHolder.tryAcquire()) {
            throw new RunAlreadyActiveException("A batch run is already active on another instance");
        }
        String masterId = properties.instanceId();
        log.info("[{}] elected MASTER for this run", masterId);
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("requestedAt", System.currentTimeMillis())
                    .addString("masterId", masterId)
                    .toJobParameters();
            JobExecution execution = jobLauncher.run(accountReportJob, params);
            return new RunStarted(execution.getId(), masterId, execution.getStatus().name());
        } catch (Exception e) {
            lockHolder.release();
            throw new IllegalStateException("Failed to launch batch job", e);
        }
    }

    public static class RunAlreadyActiveException extends RuntimeException {
        public RunAlreadyActiveException(String message) {
            super(message);
        }
    }
}
