package com.wallaceespindola.orchestrator.web;

import com.wallaceespindola.orchestrator.config.AppProperties;
import com.wallaceespindola.orchestrator.service.ReportService;
import com.wallaceespindola.orchestrator.web.dto.PartitionRequest;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instance-to-instance API used by local mode: the Master posts a partition here and
 * this instance processes it as a Worker.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final ReportService reportService;
    private final AppProperties properties;

    @PostMapping("/partitions/execute")
    public Map<String, Object> execute(@RequestBody PartitionRequest request) {
        reportService.processPartition(request.jobExecutionId(), request.partitionKey(),
                request.masterId(), request.accountIds());
        return Map.of(
                "partitionKey", request.partitionKey(),
                "workerId", properties.instanceId(),
                "status", "COMPLETED",
                "timestamp", Instant.now());
    }
}
