package com.wallaceespindola.orchestrator.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Component;

/**
 * Holds the ShedLock lock that guarantees a single active master per run.
 * Split from {@link MasterElectionService} so the batch job-completion listener can
 * release the lock without a circular bean dependency.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterLockHolder {

    public static final String LOCK_NAME = "account-report-run";

    private final LockProvider lockProvider;
    private final AtomicReference<SimpleLock> activeLock = new AtomicReference<>();

    /** @return true if this instance won the master role for the upcoming run. */
    public boolean tryAcquire() {
        return lockProvider.lock(new LockConfiguration(Instant.now(), LOCK_NAME,
                        Duration.ofMinutes(30), Duration.ofSeconds(2)))
                .map(lock -> {
                    activeLock.set(lock);
                    return true;
                })
                .orElse(false);
    }

    public void release() {
        SimpleLock lock = activeLock.getAndSet(null);
        if (lock != null) {
            lock.unlock();
            log.info("Master lock released");
        }
    }
}
