package com.saas.billing;

import java.time.LocalDate;

public class PricingCalculator {
    public Money calculate(BillingCommand command, LocalDate today) {
        LocalDate signupDate = command.signupDate();

        int monthsActive = (today.getYear() - signupDate.getYear()) * 12 + (today.getMonthValue() - signupDate.getMonthValue());

        Money basePrice = command.basePrice();
        Money calculatedPrice = basePrice;

        if (monthsActive > 24) {
            calculatedPrice = calculatedPrice.subtract(basePrice.multiply("0.15")); // 15% após 2 anos
        } else if (monthsActive > 12) {
            calculatedPrice = calculatedPrice.subtract(basePrice.multiply("0.10")); // 10% após 1 ano
        }

        if (command.isNonProfit()) {
            calculatedPrice = calculatedPrice.multiply("0.70"); // 30% off para ONGs
        }

        String promoCode = command.promoCode();
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            if (promoCode.equalsIgnoreCase("BLACKFRIDAY") && today.getMonthValue() == 11) {
                calculatedPrice = calculatedPrice.subtract(Money.of("30.00"));
            } else if (promoCode.equalsIgnoreCase("WELCOME10") && monthsActive <= 1) {
                calculatedPrice = calculatedPrice.multiply("0.90");
            }
        }

        if (command.overdueDays() > 0) {
            Money penalty = calculatedPrice.multiply("0.02");
            Money dailyInterest = calculatedPrice.multiply("0.001")
                    .multiply(Integer.toString(command.overdueDays()));
            calculatedPrice = calculatedPrice.add(penalty).add(dailyInterest);
        }

        return calculatedPrice.nonNegative().rounded();
    }
}
