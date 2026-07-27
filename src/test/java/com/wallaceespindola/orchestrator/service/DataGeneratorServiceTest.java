package com.wallaceespindola.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.repository.BankTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:datagen-test;DB_CLOSE_DELAY=-1",
        "app.data.generate-on-startup=false",
        "app.batch.simulated-work-ms-per-account=0"
})
class DataGeneratorServiceTest {

    @Autowired
    private DataGeneratorService service;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private BankTransactionRepository transactionRepository;

    @Test
    void generatesRequestedNumberOfAccountsWithTransactions() {
        DataGeneratorService.GenerationResult result = service.generate(15);

        assertThat(result.accounts()).isEqualTo(15);
        assertThat(accountRepository.count()).isEqualTo(15);
        long txCount = transactionRepository.count();
        assertThat(txCount).isEqualTo(result.transactions());
        // 5..50 transactions per account
        assertThat(txCount).isBetween(15L * 5, 15L * 50);
        accountRepository.findAll().forEach(account -> {
            assertThat(account.getAccountNumber()).startsWith("ACC-");
            assertThat(account.getOwnerName()).isNotBlank();
            assertThat(transactionRepository.findByAccountId(account.getId()))
                    .hasSizeBetween(5, 50);
        });
    }

    @Test
    void regenerationResetsPreviousData() {
        service.generate(20);
        DataGeneratorService.GenerationResult second = service.generate(10);

        assertThat(second.accounts()).isEqualTo(10);
        assertThat(accountRepository.count()).isEqualTo(10);
        assertThat(transactionRepository.count()).isEqualTo(second.transactions());
    }

    @Test
    void summaryReflectsCurrentCounts() {
        service.generate(12);

        DataGeneratorService.GenerationResult summary = service.summary();

        assertThat(summary.accounts()).isEqualTo(12);
        assertThat(summary.transactions()).isEqualTo(transactionRepository.count());
    }
}
