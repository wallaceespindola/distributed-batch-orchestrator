package com.wallaceespindola.orchestrator.batch;

import java.util.ArrayList;
import java.util.List;

/** Round-robin split of account ids into N even buckets. */
public final class RoundRobin {

    private RoundRobin() {
    }

    public static List<List<Long>> split(List<Long> ids, int buckets) {
        if (buckets < 1) {
            throw new IllegalArgumentException("buckets must be >= 1");
        }
        List<List<Long>> result = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            result.add(new ArrayList<>());
        }
        for (int i = 0; i < ids.size(); i++) {
            result.get(i % buckets).add(ids.get(i));
        }
        return result;
    }
}
