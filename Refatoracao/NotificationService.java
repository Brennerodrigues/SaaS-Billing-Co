package com.saas.billing;

public interface NotificationService {
    void sendReceipt(String email, Money amount);
}
