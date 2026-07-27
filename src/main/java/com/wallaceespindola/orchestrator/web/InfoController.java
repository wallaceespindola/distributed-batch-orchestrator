package com.wallaceespindola.orchestrator.web;

import com.wallaceespindola.orchestrator.config.AppProperties;
import com.wallaceespindola.orchestrator.service.ClusterService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InfoController {

    private final AppProperties properties;
    private final ClusterService clusterService;

    @Value("${server.port}")
    private int port;

    @Value("${spring.application.name}")
    private String applicationName;

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "instanceId", properties.instanceId(),
                "port", port,
                "mode", properties.mode(),
                "application", applicationName,
                "version", "1.0.0",
                "timestamp", Instant.now());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "instanceId", properties.instanceId(),
                "timestamp", Instant.now());
    }

    @GetMapping("/cluster")
    public Map<String, Object> cluster() {
        List<ClusterService.PeerInfo> peers = clusterService.peers();
        return Map.of("instances", peers, "timestamp", Instant.now());
    }
}
