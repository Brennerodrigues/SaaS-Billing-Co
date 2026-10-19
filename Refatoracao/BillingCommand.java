package com.saas.billing;

import java.time.LocalDate;

public record BillingCommand(
        String customerId,
        String planType,
        Money basePrice,
        LocalDate signupDate,
        int overdueDays,
        String promoCode,
        boolean isNonProfit,
        String paymentCardToken) {
}
