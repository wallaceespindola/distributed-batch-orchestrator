package com.wallaceespindola.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallaceespindola.orchestrator.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class MasterElectionServiceTest {

    @Mock
    private MasterLockHolder lockHolder;
    @Mock
    private JobLauncher jobLauncher;
    @Mock
    private Job job;

    private MasterElectionService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("instance-test", "local", null,
                new AppProperties.Data(25, false), new AppProperties.Batch(0, 60));
        service = new MasterElectionService(lockHolder, jobLauncher, job, properties);
    }

    @Test
    void firstReceiverBecomesMasterAndLaunchesJob() throws Exception {
        when(lockHolder.tryAcquire()).thenReturn(true);
        JobExecution execution = new JobExecution(42L, new JobParameters());
        when(jobLauncher.run(any(), any())).thenReturn(execution);

        MasterElectionService.RunStarted run = service.startRun();

        assertThat(run.jobExecutionId()).isEqualTo(42L);
        assertThat(run.masterId()).isEqualTo("instance-test");
    }

    @Test
    void rejectsWhenAnotherMasterIsActive() {
        when(lockHolder.tryAcquire()).thenReturn(false);

        assertThatThrownBy(service::startRun)
                .isInstanceOf(MasterElectionService.RunAlreadyActiveException.class);
    }

    @Test
    void releasesLockWhenLaunchFails() throws Exception {
        when(lockHolder.tryAcquire()).thenReturn(true);
        when(jobLauncher.run(any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(service::startRun).isInstanceOf(IllegalStateException.class);

        verify(lockHolder).release();
    }

    @Test
    void masterIdInJobParametersMatchesElectedInstance() throws Exception {
        when(lockHolder.tryAcquire()).thenReturn(true);
        when(jobLauncher.run(any(), any())).thenAnswer(invocation -> {
            JobParameters params = invocation.getArgument(1);
            assertThat(params.getString("masterId")).isEqualTo("instance-test");
            return new JobExecution(1L, params);
        });

        service.startRun();
    }

    @Test
    void lockNotReleasedOnSuccessfulLaunch() throws Exception {
        when(lockHolder.tryAcquire()).thenReturn(true);
        when(jobLauncher.run(any(), any())).thenReturn(new JobExecution(7L, new JobParameters()));

        service.startRun();

        verify(lockHolder, org.mockito.Mockito.never()).release();
    }
}
