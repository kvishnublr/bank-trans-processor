package com.luxoft.bank.service;

import com.luxoft.bank.domain.Account;
import com.luxoft.bank.domain.AccountNotFoundException;
import com.luxoft.bank.domain.Amount;
import com.luxoft.bank.domain.InsufficientFundsException;
import com.luxoft.bank.domain.InvalidTransferException;
import com.luxoft.bank.domain.LedgerEntry;
import com.luxoft.bank.domain.TransactionType;
import com.luxoft.bank.domain.TransferReceipt;
import com.luxoft.bank.infrastructure.InMemoryAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-20T09:00:00Z");

    private final BankingService bank =
            new BankingService(new InMemoryAccountRepository(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void opened_accounts_get_unique_ids_and_can_be_looked_up() {
        Account first = bank.openAccount();
        Account second = bank.openAccount();

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(bank.getAccount(first.id())).isSameAs(first);
    }

    @Test
    void looking_up_an_unknown_account_fails() {
        assertThatThrownBy(() -> bank.getAccount("nope"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void deposits_and_withdrawals_are_timestamped_by_the_clock() {
        String id = bank.openAccount().id();

        LedgerEntry deposit = bank.deposit(id, Amount.of("30.00"));
        LedgerEntry withdrawal = bank.withdraw(id, Amount.of("10.00"));

        assertThat(deposit.timestamp()).isEqualTo(NOW);
        assertThat(withdrawal.timestamp()).isEqualTo(NOW);
        assertThat(bank.getAccount(id).balance()).isEqualByComparingTo("20.00");
        assertThat(bank.history(id)).containsExactly(deposit, withdrawal);
    }

    @Test
    void operations_on_an_unknown_account_fail() {
        assertThatThrownBy(() -> bank.deposit("nope", Amount.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> bank.withdraw("nope", Amount.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> bank.history("nope"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Nested
    class Transfers {

        private String alice;
        private String bob;

        @BeforeEach
        void openFundedAccounts() {
            alice = bank.openAccount().id();
            bob = bank.openAccount().id();
            bank.deposit(alice, Amount.of("100.00"));
        }

        @Test
        void move_money_from_one_account_to_another() {
            bank.transfer(alice, bob, Amount.of("40.00"));

            assertThat(bank.getAccount(alice).balance()).isEqualByComparingTo("60.00");
            assertThat(bank.getAccount(bob).balance()).isEqualByComparingTo("40.00");
        }

        @Test
        void record_a_linked_entry_on_both_ledgers() {
            TransferReceipt receipt = bank.transfer(alice, bob, Amount.of("40.00"));

            LedgerEntry out = bank.history(alice).getLast();
            LedgerEntry in = bank.history(bob).getLast();

            assertThat(out.type()).isEqualTo(TransactionType.TRANSFER_OUT);
            assertThat(out.counterpartyAccountId()).isEqualTo(bob);
            assertThat(in.type()).isEqualTo(TransactionType.TRANSFER_IN);
            assertThat(in.counterpartyAccountId()).isEqualTo(alice);
            assertThat(out.transactionId()).isEqualTo(in.transactionId()).isEqualTo(receipt.transactionId());
            assertThat(receipt.timestamp()).isEqualTo(NOW);
        }

        @Test
        void that_would_overdraw_the_source_change_neither_account() {
            assertThatThrownBy(() -> bank.transfer(alice, bob, Amount.of("100.01")))
                    .isInstanceOf(InsufficientFundsException.class);

            assertThat(bank.getAccount(alice).balance()).isEqualByComparingTo("100.00");
            assertThat(bank.getAccount(bob).balance()).isEqualByComparingTo("0.00");
            assertThat(bank.history(bob)).isEmpty();
        }

        @Test
        void to_the_same_account_are_rejected() {
            assertThatThrownBy(() -> bank.transfer(alice, alice, Amount.of("1.00")))
                    .isInstanceOf(InvalidTransferException.class);

            assertThat(bank.history(alice)).hasSize(1);
        }

        @Test
        void to_an_unknown_account_are_rejected_without_debiting_the_source() {
            assertThatThrownBy(() -> bank.transfer(alice, "nope", Amount.of("1.00")))
                    .isInstanceOf(AccountNotFoundException.class);

            assertThat(bank.getAccount(alice).balance()).isEqualByComparingTo("100.00");
        }

        @Test
        void from_an_unknown_account_are_rejected() {
            assertThatThrownBy(() -> bank.transfer("nope", bob, Amount.of("1.00")))
                    .isInstanceOf(AccountNotFoundException.class);

            assertThat(bank.history(bob)).isEmpty();
        }
    }
}
