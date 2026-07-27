package com.wallaceespindola.orchestrator.repository;

import com.wallaceespindola.orchestrator.domain.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("select a.id from Account a order by a.id")
    List<Long> findAllIds();
}
