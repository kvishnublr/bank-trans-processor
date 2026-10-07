package com.luxoft.bank.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountTest {

    @Test
    void a_positive_amount_with_up_to_two_decimals_is_accepted() {
        Amount amount = Amount.of("10.5");

        assertThat(amount.value()).isEqualByComparingTo("10.50");
    }

    @Test
    void amounts_are_normalised_to_two_decimal_places() {
        assertThat(Amount.of("7").value()).isEqualTo(new BigDecimal("7.00"));
    }

    @Test
    void amounts_with_the_same_value_are_equal_regardless_of_scale() {
        assertThat(Amount.of("5")).isEqualTo(Amount.of("5.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01"})
    void zero_or_negative_amounts_are_rejected(String value) {
        assertThatThrownBy(() -> Amount.of(value))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void fractions_of_a_penny_are_rejected_rather_than_silently_rounded() {
        assertThatThrownBy(() -> Amount.of("10.005"))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("2 decimal places");
    }

    @Test
    void the_largest_allowed_amount_is_accepted() {
        assertThat(Amount.of("999999999999.99").value()).isEqualByComparingTo("999999999999.99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000000.00", "1e999999999"})
    void absurdly_large_amounts_are_rejected_before_any_arithmetic_is_attempted(String value) {
        assertThatThrownBy(() -> Amount.of(value))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("cannot exceed");
    }

    @Test
    void a_missing_amount_is_rejected() {
        assertThatThrownBy(() -> Amount.of((BigDecimal) null))
                .isInstanceOf(InvalidAmountException.class);
    }
}
