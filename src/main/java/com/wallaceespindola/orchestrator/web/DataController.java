package com.wallaceespindola.orchestrator.web;

import com.wallaceespindola.orchestrator.config.AppProperties;
import com.wallaceespindola.orchestrator.service.DataGeneratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataController {

    private final DataGeneratorService dataGeneratorService;
    private final AppProperties properties;

    public record GenerateRequest(@Min(1) @Max(10_000) Integer accounts) {
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@Valid @RequestBody(required = false) GenerateRequest request) {
        int count = request != null && request.accounts() != null
                ? request.accounts()
                : properties.data().defaultAccounts();
        DataGeneratorService.GenerationResult result = dataGeneratorService.generate(count);
        return summaryResponse(result);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return summaryResponse(dataGeneratorService.summary());
    }

    private Map<String, Object> summaryResponse(DataGeneratorService.GenerationResult result) {
        return Map.of(
                "accounts", result.accounts(),
                "transactions", result.transactions(),
                "timestamp", Instant.now());
    }
}
