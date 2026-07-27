package com.wallaceespindola.orchestrator.web;

import com.wallaceespindola.orchestrator.repository.AccountReportRepository;
import com.wallaceespindola.orchestrator.service.BatchStatusService;
import com.wallaceespindola.orchestrator.service.MasterElectionService;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BatchController {

    private final MasterElectionService masterElectionService;
    private final BatchStatusService batchStatusService;
    private final AccountReportRepository reportRepository;

    @PostMapping("/batch/start")
    public ResponseEntity<Map<String, Object>> start() {
        MasterElectionService.RunStarted run = masterElectionService.startRun();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "jobExecutionId", run.jobExecutionId(),
                "masterId", run.masterId(),
                "status", run.status(),
                "timestamp", Instant.now()));
    }

    @GetMapping("/batch/status")
    public ResponseEntity<Map<String, Object>> status() {
        return batchStatusService.latestRun()
                .map(this::statusResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No batch run yet", "timestamp", Instant.now())));
    }

    @GetMapping("/batch/status/{jobExecutionId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable long jobExecutionId) {
        return batchStatusService.run(jobExecutionId)
                .map(this::statusResponse)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Unknown job execution: " + jobExecutionId,
                                "timestamp", Instant.now())));
    }

    @GetMapping("/batch/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "50") int limit) {
        return Map.of("runs", batchStatusService.history(limit), "timestamp", Instant.now());
    }

    @GetMapping("/reports")
    public Map<String, Object> reports(@RequestParam long jobExecutionId) {
        return Map.of(
                "reports", reportRepository.findByJobExecutionIdOrderByAccountId(jobExecutionId),
                "timestamp", Instant.now());
    }

    private ResponseEntity<Map<String, Object>> statusResponse(BatchStatusService.RunStatus run) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("jobExecutionId", run.jobExecutionId());
        body.put("status", run.status());
        body.put("masterId", run.masterId());
        body.put("startTime", run.startTime());
        body.put("endTime", run.endTime());
        body.put("reportsGenerated", run.reportsGenerated());
        body.put("partitions", run.partitions());
        body.put("timestamp", Instant.now());
        return ResponseEntity.ok(body);
    }
}
