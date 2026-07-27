package com.wallaceespindola.orchestrator.repository;

import com.wallaceespindola.orchestrator.domain.BankTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByAccountId(Long accountId);
}
