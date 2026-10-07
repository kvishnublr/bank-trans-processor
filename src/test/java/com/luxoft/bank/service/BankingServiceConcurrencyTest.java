package com.luxoft.bank.service;

import com.luxoft.bank.domain.Amount;
import com.luxoft.bank.domain.InsufficientFundsException;
import com.luxoft.bank.infrastructure.InMemoryAccountRepository;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These tests describe what callers can rely on when many requests hit the same accounts at once.
 */
class BankingServiceConcurrencyTest {

    private static final int THREADS = 16;

    private final BankingService bank = new BankingService(new InMemoryAccountRepository(), Clock.systemUTC());

    @RepeatedTest(5)
    void concurrent_deposits_are_never_lost() throws Exception {
        String id = bank.openAccount().id();

        runConcurrently(2_000, () -> bank.deposit(id, Amount.of("1.00")));

        assertThat(bank.getAccount(id).balance()).isEqualByComparingTo("2000.00");
        assertThat(bank.history(id)).hasSize(2_000);
    }

    @RepeatedTest(5)
    void concurrent_withdrawals_never_overdraw_the_account() throws Exception {
        String id = bank.openAccount().id();
        bank.deposit(id, Amount.of("100.00"));
        AtomicInteger succeeded = new AtomicInteger();

        runConcurrently(500, () -> {
            try {
                bank.withdraw(id, Amount.of("1.00"));
                succeeded.incrementAndGet();
            } catch (InsufficientFundsException expected) {
                // most of these should be refused
            }
        });

        assertThat(succeeded.get()).isEqualTo(100);
        assertThat(bank.getAccount(id).balance()).isEqualByComparingTo("0.00");
    }

    @RepeatedTest(5)
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void opposing_transfers_neither_deadlock_nor_create_or_destroy_money() throws Exception {
        String alice = bank.openAccount().id();
        String bob = bank.openAccount().id();
        bank.deposit(alice, Amount.of("1000.00"));
        bank.deposit(bob, Amount.of("1000.00"));
        AtomicInteger counter = new AtomicInteger();

        runConcurrently(2_000, () -> {
            boolean aliceToBob = counter.incrementAndGet() % 2 == 0;
            try {
                if (aliceToBob) {
                    bank.transfer(alice, bob, Amount.of("3.00"));
                } else {
                    bank.transfer(bob, alice, Amount.of("3.00"));
                }
            } catch (InsufficientFundsException ignored) {
                // acceptable under contention; what matters is the total
            }
        });

        assertThat(bank.getAccount(alice).balance().add(bank.getAccount(bob).balance()))
                .isEqualByComparingTo("2000.00");
    }

    private static void runConcurrently(int tasks, Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    task.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
