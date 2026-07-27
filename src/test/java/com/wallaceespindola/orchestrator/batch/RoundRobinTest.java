package com.wallaceespindola.orchestrator.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class RoundRobinTest {

    @Test
    void splitsEvenlyAcrossBuckets() {
        List<Long> ids = LongStream.rangeClosed(1, 10).boxed().toList();

        List<List<Long>> buckets = RoundRobin.split(ids, 4);

        assertThat(buckets).hasSize(4);
        assertThat(buckets.get(0)).containsExactly(1L, 5L, 9L);
        assertThat(buckets.get(1)).containsExactly(2L, 6L, 10L);
        assertThat(buckets.get(2)).containsExactly(3L, 7L);
        assertThat(buckets.get(3)).containsExactly(4L, 8L);
    }

    @Test
    void bucketSizesDifferByAtMostOne() {
        List<Long> ids = LongStream.rangeClosed(1, 97).boxed().toList();

        List<List<Long>> buckets = RoundRobin.split(ids, 4);

        int min = buckets.stream().mapToInt(List::size).min().orElseThrow();
        int max = buckets.stream().mapToInt(List::size).max().orElseThrow();
        assertThat(max - min).isLessThanOrEqualTo(1);
        assertThat(buckets.stream().mapToInt(List::size).sum()).isEqualTo(97);
    }

    @Test
    void singleBucketGetsEverything() {
        List<Long> ids = List.of(1L, 2L, 3L);

        assertThat(RoundRobin.split(ids, 1)).containsExactly(ids);
    }

    @Test
    void moreBucketsThanIdsLeavesEmptyBuckets() {
        List<List<Long>> buckets = RoundRobin.split(List.of(1L, 2L), 4);

        assertThat(buckets.get(0)).containsExactly(1L);
        assertThat(buckets.get(1)).containsExactly(2L);
        assertThat(buckets.get(2)).isEmpty();
        assertThat(buckets.get(3)).isEmpty();
    }

    @Test
    void rejectsInvalidBucketCount() {
        assertThatThrownBy(() -> RoundRobin.split(List.of(1L), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
