package com.wallaceespindola.orchestrator.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ACCOUNT_REPORTS")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jobExecutionId;
    private Long accountId;
    private String accountNumber;
    private String ownerName;
    private String workerId;
    private String partitionKey;
    private int transactionCount;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal endingBalance;
    private Instant generatedAt;
}
