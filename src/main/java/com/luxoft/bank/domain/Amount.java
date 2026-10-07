package com.luxoft.bank.domain;

import java.math.BigDecimal;

/**
 * A positive amount of money, in a single currency, to the penny.
 * Once you hold an Amount you no longer need to re-validate it.
 */
public record Amount(BigDecimal value) {

    private static final int SCALE = 2;

    /**
     * A sanity ceiling, not a business limit. It exists so input like 1e999999999 is refused
     * before we try to scale it, which would otherwise blow up deep inside BigDecimal.
     */
    static final BigDecimal MAXIMUM = new BigDecimal("999999999999.99");

    public Amount {
        if (value == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (value.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (value.compareTo(MAXIMUM) > 0) {
            throw new InvalidAmountException("Amount cannot exceed " + MAXIMUM.toPlainString());
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidAmountException("Amount cannot have more than 2 decimal places");
        }
        value = value.setScale(SCALE);
    }

    public static Amount of(BigDecimal value) {
        return new Amount(value);
    }

    public static Amount of(String value) {
        return new Amount(new BigDecimal(value));
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
