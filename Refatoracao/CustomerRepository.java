package com.saas.billing;

import java.util.Optional;

public interface CustomerRepository {
    Optional<Customer> findById(String customerId);

    void saveInvoice(String customerId, Money amount, boolean paid);
}
