package com.saas.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) {

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("O valor não pode ser nulo.");
        }
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(String factor) {
        return new Money(amount.multiply(new BigDecimal(factor)));
    }

    public Money rounded() {
        return new Money(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public Money nonNegative() {
        return new Money(amount.max(BigDecimal.ZERO));
    }

    @Override
    public String toString() {
        return rounded().amount.toPlainString();
    }
}
