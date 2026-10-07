package com.luxoft.bank.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-04-20T09:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    private final Account account = new Account("ACC-1");

    @Test
    void an_account_must_have_an_id() {
        assertThatThrownBy(() -> new Account(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_new_account_starts_with_a_zero_balance_and_an_empty_ledger() {
        assertThat(account.balance()).isEqualByComparingTo("0.00");
        assertThat(account.history()).isEmpty();
    }

    @Nested
    class Deposits {

        @Test
        void increase_the_balance() {
            account.deposit(Amount.of("100.00"), NOW);
            account.deposit(Amount.of("25.50"), LATER);

            assertThat(account.balance()).isEqualByComparingTo("125.50");
        }

        @Test
        void are_recorded_in_the_ledger_with_their_timestamp_and_resulting_balance() {
            LedgerEntry entry = account.deposit(Amount.of("100.00"), NOW);

            assertThat(entry.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(entry.amount()).isEqualByComparingTo("100.00");
            assertThat(entry.balanceAfter()).isEqualByComparingTo("100.00");
            assertThat(entry.timestamp()).isEqualTo(NOW);
            assertThat(entry.transactionId()).isNotBlank();
            assertThat(account.history()).containsExactly(entry);
        }
    }

    @Nested
    class Withdrawals {

        @Test
        void decrease_the_balance() {
            account.deposit(Amount.of("100.00"), NOW);

            account.withdraw(Amount.of("40.00"), LATER);

            assertThat(account.balance()).isEqualByComparingTo("60.00");
        }

        @Test
        void may_take_the_balance_to_exactly_zero() {
            account.deposit(Amount.of("50.00"), NOW);

            account.withdraw(Amount.of("50.00"), LATER);

            assertThat(account.balance()).isEqualByComparingTo("0.00");
        }

        @Test
        void that_would_overdraw_are_rejected_leaving_balance_and_ledger_untouched() {
            account.deposit(Amount.of("50.00"), NOW);

            assertThatThrownBy(() -> account.withdraw(Amount.of("50.01"), LATER))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("ACC-1");

            assertThat(account.balance()).isEqualByComparingTo("50.00");
            assertThat(account.history()).hasSize(1);
        }

        @Test
        void are_recorded_in_the_ledger() {
            account.deposit(Amount.of("50.00"), NOW);

            LedgerEntry entry = account.withdraw(Amount.of("20.00"), LATER);

            assertThat(entry.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(entry.balanceAfter()).isEqualByComparingTo("30.00");
            assertThat(entry.timestamp()).isEqualTo(LATER);
        }
    }

    @Nested
    class Ledger {

        @Test
        void lists_entries_oldest_first() {
            LedgerEntry first = account.deposit(Amount.of("10.00"), NOW);
            LedgerEntry second = account.withdraw(Amount.of("5.00"), LATER);

            assertThat(account.history()).containsExactly(first, second);
        }

        @Test
        void cannot_be_modified_by_callers() {
            account.deposit(Amount.of("10.00"), NOW);

            assertThatThrownBy(() -> account.history().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void gives_each_entry_a_unique_transaction_id() {
            LedgerEntry first = account.deposit(Amount.of("10.00"), NOW);
            LedgerEntry second = account.deposit(Amount.of("10.00"), NOW);

            assertThat(first.transactionId()).isNotEqualTo(second.transactionId());
        }
    }
}
