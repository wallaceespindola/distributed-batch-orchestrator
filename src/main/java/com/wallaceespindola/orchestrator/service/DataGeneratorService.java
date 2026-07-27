package com.wallaceespindola.orchestrator.service;

import com.wallaceespindola.orchestrator.config.AppProperties;
import com.wallaceespindola.orchestrator.domain.Account;
import com.wallaceespindola.orchestrator.domain.BankTransaction;
import com.wallaceespindola.orchestrator.repository.AccountRepository;
import com.wallaceespindola.orchestrator.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataGeneratorService {

    private static final String[] FIRST_NAMES = {
            "Alice", "Bruno", "Carla", "Diego", "Elena", "Felipe", "Gabriela", "Hugo",
            "Isabela", "Joao", "Karin", "Lucas", "Marina", "Nina", "Otavio", "Paula",
            "Rafael", "Sofia", "Thiago", "Valentina"};
    private static final String[] LAST_NAMES = {
            "Silva", "Souza", "Oliveira", "Santos", "Pereira", "Costa", "Almeida",
            "Nascimento", "Lima", "Araujo", "Fernandes", "Carvalho", "Gomes", "Martins"};
    private static final String[] DESCRIPTIONS = {
            "Salary", "Groceries", "Rent", "Transfer", "Utilities", "Restaurant",
            "Online purchase", "Subscription", "Fuel", "Insurance", "Investment", "Refund"};

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final AppProperties properties;
    private final LockProvider lockProvider;

    /**
     * Seeds the database on startup. All 4 identical instances race here, so a ShedLock
     * lock plus an emptiness check make sure only the first one generates data.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!properties.data().generateOnStartup()) {
            return;
        }
        lockProvider.lock(new LockConfiguration(Instant.now(), "data-init",
                        java.time.Duration.ofMinutes(2), java.time.Duration.ofSeconds(1)))
                .ifPresent(lock -> {
                    try {
                        if (accountRepository.count() == 0) {
                            generate(properties.data().defaultAccounts());
                        }
                    } finally {
                        lock.unlock();
                    }
                });
    }

    @Transactional
    public GenerationResult generate(int accountCount) {
        transactionRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Account> accounts = new ArrayList<>(accountCount);
        for (int i = 0; i < accountCount; i++) {
            accounts.add(Account.builder()
                    .accountNumber("ACC-%06d".formatted(random.nextInt(1, 1_000_000)))
                    .ownerName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + " "
                            + LAST_NAMES[random.nextInt(LAST_NAMES.length)])
                    .balance(randomAmount(random, 100, 50_000))
                    .createdAt(Instant.now())
                    .build());
        }
        accounts = accountRepository.saveAll(accounts);

        List<BankTransaction> transactions = new ArrayList<>();
        for (Account account : accounts) {
            int txCount = random.nextInt(5, 51);
            for (int t = 0; t < txCount; t++) {
                transactions.add(BankTransaction.builder()
                        .accountId(account.getId())
                        .txType(random.nextBoolean() ? BankTransaction.TxType.CREDIT
                                : BankTransaction.TxType.DEBIT)
                        .amount(randomAmount(random, 1, 5_000))
                        .description(DESCRIPTIONS[random.nextInt(DESCRIPTIONS.length)])
                        .createdAt(Instant.now().minus(random.nextInt(1, 365), ChronoUnit.DAYS))
                        .build());
            }
        }
        transactionRepository.saveAll(transactions);

        log.info("Generated {} accounts with {} transactions", accounts.size(), transactions.size());
        return new GenerationResult(accounts.size(), transactions.size());
    }

    public GenerationResult summary() {
        return new GenerationResult(accountRepository.count(), transactionRepository.count());
    }

    private static BigDecimal randomAmount(ThreadLocalRandom random, int min, int max) {
        return BigDecimal.valueOf(random.nextDouble(min, max)).setScale(2, RoundingMode.HALF_UP);
    }

    public record GenerationResult(long accounts, long transactions) {
    }
}
