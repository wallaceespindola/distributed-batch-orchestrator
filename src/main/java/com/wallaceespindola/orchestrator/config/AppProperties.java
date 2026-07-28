package com.wallaceespindola.orchestrator.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String instanceId,
        @DefaultValue("local") String mode,
        @DefaultValue List<String> peers,
        @DefaultValue Data data,
        @DefaultValue Batch batch,
        // Shared secret for instance-to-instance calls; override via APP_INTERNAL_TOKEN
        // in any non-local deployment.
        @DefaultValue("local-dev-internal-token") String internalToken) {

    public record Data(
            @DefaultValue("25") int defaultAccounts,
            @DefaultValue("true") boolean generateOnStartup) {
    }

    public record Batch(
            @DefaultValue("150") long simulatedWorkMsPerAccount,
            @DefaultValue("600") long dispatchTimeoutSeconds) {
    }
}
