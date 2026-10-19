package com.saas.billing;

public class TokenPaymentGateway implements PaymentGateway {
    @Override
    public boolean charge(String cardToken, Money amount) {
        return cardToken != null && cardToken.startsWith("tok_");
    }
}
