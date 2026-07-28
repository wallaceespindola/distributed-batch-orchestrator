package com.wallaceespindola.orchestrator.batch.local;

import com.wallaceespindola.orchestrator.batch.RoundRobin;
import com.wallaceespindola.orchestrator.config.AppProperties;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.service.ClusterService;
import com.wallaceespindola.orchestrator.web.dto.PartitionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Local-mode distribution (no Kafka): the elected Master splits accounts round-robin
 * across all healthy instances (itself included) and dispatches each partition over
 * HTTP. Workers record their own partition assignment, so attribution comes from the
 * worker that actually did the work.
 */
@RequiredArgsConstructor
@Slf4j
public class LocalDispatchTasklet implements Tasklet {

    private final AccountRepository accountRepository;
    private final ClusterService clusterService;
    private final AppProperties properties;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long jobExecutionId = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getId();
        String masterId = properties.instanceId();

        List<Long> accountIds = accountRepository.findAllIds();
        if (accountIds.isEmpty()) {
            throw new IllegalStateException("No accounts in database — generate data first");
        }

        List<ClusterService.PeerInfo> workers = clusterService.healthyPeers();
        if (workers.isEmpty()) {
            throw new IllegalStateException("No healthy instances available");
        }
        List<List<Long>> buckets = RoundRobin.split(accountIds, workers.size());
        log.info("[{}] MASTER dispatching {} accounts across {} workers (job {})",
                masterId, accountIds.size(), workers.size(), jobExecutionId);

        RestClient client = dispatchClient();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(workers.size());
        try {
            for (int i = 0; i < workers.size(); i++) {
                ClusterService.PeerInfo worker = workers.get(i);
                PartitionRequest request = new PartitionRequest(jobExecutionId,
                        "partition-" + i, masterId, buckets.get(i));
                futures.add(CompletableFuture.runAsync(() -> {
                    log.info("Dispatching {} ({} accounts) to {}", request.partitionKey(),
                            request.accountIds().size(), worker.url());
                    client.post().uri(worker.url() + "/internal/partitions/execute")
                            .header("X-Internal-Token", properties.internalToken())
                            .body(request)
                            .retrieve()
                            .toBodilessEntity();
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(properties.batch().dispatchTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Partition dispatch failed: " + e.getMessage(), e);
        } finally {
            executor.shutdownNow();
        }
        return RepeatStatus.FINISHED;
    }

    private RestClient dispatchClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout((int) TimeUnit.SECONDS.toMillis(
                properties.batch().dispatchTimeoutSeconds()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
