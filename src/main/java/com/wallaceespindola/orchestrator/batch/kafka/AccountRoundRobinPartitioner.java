package com.wallaceespindola.orchestrator.batch.kafka;

import com.wallaceespindola.orchestrator.batch.RoundRobin;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

/**
 * Splits accounts round-robin into {@code gridSize} partitions; each partition's
 * execution context carries its account ids to the remote worker.
 */
@RequiredArgsConstructor
public class AccountRoundRobinPartitioner implements Partitioner {

    public static final String ACCOUNT_IDS_KEY = "accountIds";
    public static final String PARTITION_KEY = "partitionKey";
    public static final String MASTER_ID_KEY = "masterId";

    private final AccountRepository accountRepository;
    private final String masterId;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        List<Long> ids = accountRepository.findAllIds();
        if (ids.isEmpty()) {
            throw new IllegalStateException("No accounts in database — generate data first");
        }
        List<List<Long>> buckets = RoundRobin.split(ids, gridSize);
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (int i = 0; i < buckets.size(); i++) {
            if (buckets.get(i).isEmpty()) {
                continue;
            }
            ExecutionContext context = new ExecutionContext();
            context.put(ACCOUNT_IDS_KEY, new ArrayList<>(buckets.get(i)));
            context.putString(PARTITION_KEY, "partition-" + i);
            context.putString(MASTER_ID_KEY, masterId);
            partitions.put("partition-" + i, context);
        }
        return partitions;
    }
}
