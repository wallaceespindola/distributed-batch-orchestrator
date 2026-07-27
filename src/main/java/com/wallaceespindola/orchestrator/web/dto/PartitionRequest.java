package com.wallaceespindola.orchestrator.web.dto;

import java.util.List;

public record PartitionRequest(long jobExecutionId, String partitionKey, String masterId,
                               List<Long> accountIds) {
}
