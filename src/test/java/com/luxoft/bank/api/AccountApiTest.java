package com.luxoft.bank.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract, exercised end to end through the real Spring context.
 * Every test opens its own accounts, so tests don't depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTest {

    @Autowired
    private MockMvc mvc;

    @Nested
    class OpeningAnAccount {

        @Test
        void returns_201_with_its_location_and_a_zero_balance() throws Exception {
            var response = mvc.perform(post("/accounts"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.balance").value(0.0))
                    .andReturn().getResponse();

            String id = JsonPath.read(response.getContentAsString(), "$.id");
            assertThat(response.getHeader("Location")).endsWith("/accounts/" + id);
        }

        @Test
        void the_account_can_then_be_fetched() throws Exception {
            String id = openAccount();

            mvc.perform(get("/accounts/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.balance").value(0.0));
        }

        @Test
        void fetching_an_unknown_account_returns_404_problem() throws Exception {
            mvc.perform(get("/accounts/{id}", "does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Account not found"))
                    .andExpect(jsonPath("$.detail").value("Account does-not-exist does not exist"));
        }
    }

    @Nested
    class DepositsAndWithdrawals {

        @Test
        void a_deposit_returns_the_ledger_entry_and_updates_the_balance() throws Exception {
            String id = openAccount();

            deposit(id, "100.00")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("DEPOSIT"))
                    .andExpect(jsonPath("$.amount").value(100.00))
                    .andExpect(jsonPath("$.balanceAfter").value(100.00))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.counterpartyAccountId").doesNotExist());

            mvc.perform(get("/accounts/{id}", id)).andExpect(jsonPath("$.balance").value(100.00));
        }

        @Test
        void a_withdrawal_reduces_the_balance() throws Exception {
            String id = openAccount();
            deposit(id, "100.00");

            withdraw(id, "30.25")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$.balanceAfter").value(69.75));
        }

        @Test
        void an_overdraft_returns_422_and_leaves_the_balance_alone() throws Exception {
            String id = openAccount();
            deposit(id, "10.00");

            withdraw(id, "10.01")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Insufficient funds"));

            mvc.perform(get("/accounts/{id}", id)).andExpect(jsonPath("$.balance").value(10.00));
        }

        @Test
        void a_zero_negative_or_sub_penny_amount_returns_400() throws Exception {
            String id = openAccount();

            deposit(id, "0").andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid amount"));
            deposit(id, "-5.00").andExpect(status().isBadRequest());
            deposit(id, "1.001").andExpect(status().isBadRequest());
        }

        @Test
        void a_huge_exponent_returns_400_rather_than_a_server_error() throws Exception {
            // Found by poking the running app with curl: this used to surface as a 500.
            deposit(openAccount(), "1e999999999").andExpect(status().isBadRequest());
        }

        @Test
        void a_missing_amount_returns_400_naming_the_field() throws Exception {
            mvc.perform(post("/accounts/{id}/deposits", openAccount()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.errors.amount").value("must not be null"));
        }

        @Test
        void a_malformed_body_returns_400() throws Exception {
            String id = openAccount();

            mvc.perform(post("/accounts/{id}/deposits", id).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/accounts/{id}/deposits", id).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"ten\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void depositing_into_an_unknown_account_returns_404() throws Exception {
            deposit("does-not-exist", "1.00").andExpect(status().isNotFound());
        }
    }

    @Nested
    class Transfers {

        @Test
        void move_money_and_return_a_receipt() throws Exception {
            String alice = openAccount();
            String bob = openAccount();
            deposit(alice, "50.00");

            transfer(alice, bob, "20.00")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionId").isNotEmpty())
                    .andExpect(jsonPath("$.fromAccountId").value(alice))
                    .andExpect(jsonPath("$.toAccountId").value(bob))
                    .andExpect(jsonPath("$.amount").value(20.00));

            mvc.perform(get("/accounts/{id}", alice)).andExpect(jsonPath("$.balance").value(30.00));
            mvc.perform(get("/accounts/{id}", bob)).andExpect(jsonPath("$.balance").value(20.00));
        }

        @Test
        void that_would_overdraw_return_422() throws Exception {
            String alice = openAccount();
            String bob = openAccount();

            transfer(alice, bob, "1.00").andExpect(status().isUnprocessableEntity());
        }

        @Test
        void to_the_same_account_return_422() throws Exception {
            String alice = openAccount();
            deposit(alice, "5.00");

            transfer(alice, alice, "1.00")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title").value("Invalid transfer"));
        }

        @Test
        void involving_an_unknown_account_return_404() throws Exception {
            String alice = openAccount();
            deposit(alice, "5.00");

            transfer(alice, "does-not-exist", "1.00").andExpect(status().isNotFound());
        }

        @Test
        void without_account_ids_return_400_naming_every_missing_field() throws Exception {
            mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1.00}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.fromAccountId").value("must not be blank"))
                    .andExpect(jsonPath("$.errors.toAccountId").value("must not be blank"));
        }
    }

    @Nested
    class TransactionHistory {

        @Test
        void lists_every_operation_oldest_first() throws Exception {
            String alice = openAccount();
            String bob = openAccount();
            deposit(alice, "100.00");
            withdraw(alice, "10.00");
            transfer(alice, bob, "25.00");

            mvc.perform(get("/accounts/{id}/transactions", alice))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                    .andExpect(jsonPath("$[1].type").value("WITHDRAWAL"))
                    .andExpect(jsonPath("$[2].type").value("TRANSFER_OUT"))
                    .andExpect(jsonPath("$[2].counterpartyAccountId").value(bob))
                    .andExpect(jsonPath("$[2].balanceAfter").value(65.00));

            mvc.perform(get("/accounts/{id}/transactions", bob))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                    .andExpect(jsonPath("$[0].counterpartyAccountId").value(alice));
        }

        @Test
        void for_an_unknown_account_returns_404() throws Exception {
            mvc.perform(get("/accounts/{id}/transactions", "does-not-exist"))
                    .andExpect(status().isNotFound());
        }
    }

    private String openAccount() throws Exception {
        String body = mvc.perform(post("/accounts")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions deposit(String accountId, String amount) throws Exception {
        return mvc.perform(post("/accounts/{id}/deposits", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"));
    }

    private ResultActions withdraw(String accountId, String amount) throws Exception {
        return mvc.perform(post("/accounts/{id}/withdrawals", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}"));
    }

    private ResultActions transfer(String from, String to, String amount) throws Exception {
        return mvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amount\": " + amount + "}"));
    }
}
