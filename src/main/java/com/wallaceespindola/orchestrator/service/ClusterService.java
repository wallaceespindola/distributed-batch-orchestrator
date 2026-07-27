package com.wallaceespindola.orchestrator.service;

import com.wallaceespindola.orchestrator.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Knows the other identical instances (local mode) and can probe them over HTTP.
 */
@Service
@Slf4j
public class ClusterService {

    public record PeerInfo(String url, boolean up, String instanceId, Integer port) {
    }

    private final AppProperties properties;
    private final RestClient probeClient;

    public ClusterService(AppProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        this.probeClient = RestClient.builder().requestFactory(factory).build();
    }

    public List<PeerInfo> peers() {
        List<PeerInfo> result = new ArrayList<>();
        for (String url : properties.peers()) {
            result.add(probe(url));
        }
        return result;
    }

    public List<PeerInfo> healthyPeers() {
        return peers().stream().filter(PeerInfo::up).toList();
    }

    @SuppressWarnings("unchecked")
    private PeerInfo probe(String url) {
        try {
            Map<String, Object> info = probeClient.get().uri(url + "/api/info")
                    .retrieve().body(Map.class);
            return new PeerInfo(url, true,
                    String.valueOf(info.get("instanceId")),
                    info.get("port") instanceof Number n ? n.intValue() : null);
        } catch (Exception e) {
            log.debug("Peer {} not reachable: {}", url, e.getMessage());
            return new PeerInfo(url, false, null, null);
        }
    }
}
