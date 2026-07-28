package com.wallaceespindola.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * Full local-mode flow against a single running instance (a 1-node cluster): generate
 * data, elect master, dispatch over HTTP, produce reports, verify attribution/history.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {
        "server.port=18080",
        "spring.datasource.url=jdbc:h2:mem:e2e-test;DB_CLOSE_DELAY=-1",
        "app.peers=http://localhost:18080",
        "app.instance-id=instance-e2e",
        "app.data.generate-on-startup=false",
        "app.batch.simulated-work-ms-per-account=0"
})
class LocalModeEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void fullBatchFlowProducesReportsWithWorkerAttribution() throws Exception {
        // generate data
        ResponseEntity<Map> generated = rest.postForEntity("/api/data/generate",
                Map.of("accounts", 12), Map.class);
        assertThat(generated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) generated.getBody().get("accounts")).intValue()).isEqualTo(12);

        // start batch — this instance becomes master for the run
        ResponseEntity<Map> started = rest.postForEntity("/api/batch/start", null, Map.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(started.getBody().get("masterId")).isEqualTo("instance-e2e");

        // poll status until the run completes
        Map<String, Object> status = null;
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<Map> response = rest.getForEntity("/api/batch/status", Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                status = response.getBody();
                if ("COMPLETED".equals(status.get("status")) || "FAILED".equals(status.get("status"))) {
                    break;
                }
            }
            Thread.sleep(250);
        }

        assertThat(status).isNotNull();
        assertThat(status.get("status")).isEqualTo("COMPLETED");
        assertThat(status.get("masterId")).isEqualTo("instance-e2e");
        assertThat(((Number) status.get("reportsGenerated")).longValue()).isEqualTo(12);

        List<Map<String, Object>> partitions = (List<Map<String, Object>>) status.get("partitions");
        assertThat(partitions).hasSize(1);
        assertThat(partitions.get(0).get("workerId")).isEqualTo("instance-e2e");
        assertThat(partitions.get(0).get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) partitions.get(0).get("accountCount")).intValue()).isEqualTo(12);

        // history shows the completed run
        ResponseEntity<Map> history = rest.getForEntity("/api/batch/history", Map.class);
        List<Map<String, Object>> runs = (List<Map<String, Object>>) history.getBody().get("runs");
        assertThat(runs).isNotEmpty();
        assertThat(runs.get(0).get("status")).isEqualTo("COMPLETED");
        assertThat(runs.get(0).get("masterId")).isEqualTo("instance-e2e");

        // reports endpoint returns one report per account
        long jobExecutionId = ((Number) status.get("jobExecutionId")).longValue();
        ResponseEntity<Map> reports = rest.getForEntity(
                "/api/reports?jobExecutionId=" + jobExecutionId, Map.class);
        assertThat((List<?>) reports.getBody().get("reports")).hasSize(12);
    }

    @Test
    void internalEndpointRejectsMissingToken() {
        ResponseEntity<Map> response = rest.postForEntity("/internal/partitions/execute",
                Map.of("jobExecutionId", 1, "partitionKey", "partition-0", "masterId", "x",
                        "accountIds", List.of(1)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void infoAndHealthEndpointsRespond() {
        Map<String, Object> info = rest.getForObject("/api/info", Map.class);
        assertThat(info.get("instanceId")).isEqualTo("instance-e2e");
        assertThat(info.get("mode")).isEqualTo("local");

        Map<String, Object> health = rest.getForObject("/api/health", Map.class);
        assertThat(health.get("status")).isEqualTo("UP");
    }
}
