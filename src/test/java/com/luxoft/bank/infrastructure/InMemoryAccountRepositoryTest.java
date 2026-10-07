package com.luxoft.bank.infrastructure;

import com.luxoft.bank.domain.Account;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAccountRepositoryTest {

    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();

    @Test
    void finds_an_account_that_was_added() {
        Account account = new Account("ACC-1");
        repository.add(account);

        assertThat(repository.findById("ACC-1")).containsSame(account);
    }

    @Test
    void returns_empty_for_an_unknown_id() {
        assertThat(repository.findById("ACC-404")).isEmpty();
    }

    @Test
    void refuses_a_second_account_with_the_same_id_and_keeps_the_first() {
        Account original = new Account("ACC-1");
        repository.add(original);

        assertThatThrownBy(() -> repository.add(new Account("ACC-1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findById("ACC-1")).containsSame(original);
    }
}
