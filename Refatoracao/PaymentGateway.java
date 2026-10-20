package com.saas.billing;

public interface PaymentGateway {
    boolean charge(String cardToken, Money amount);
}
