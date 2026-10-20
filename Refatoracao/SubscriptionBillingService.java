package com.saas.billing;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionBillingService {

    private final CustomerRepository customerRepository;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final Clock clock;
    private final PricingCalculator pricingCalculator = new PricingCalculator();

    public SubscriptionBillingService(CustomerRepository customerRepository,
                                      PaymentGateway paymentGateway,
                                      NotificationService notificationService,
                                      Clock clock) {
        this.customerRepository = customerRepository;
        this.paymentGateway = paymentGateway;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    public List<String> processMonthlyBilling(BillingCommand command) {

        List<String> logs = new ArrayList<>();
        Money calculatedPrice = pricingCalculator.calculate(command, LocalDate.now(clock));
        String customerId = command.customerId();
        Optional<Customer> customer = customerRepository.findById(customerId);
        if (customer.isEmpty()) {
            return logs;
        }
        if (!"ACTIVE".equalsIgnoreCase(customer.get().status())) {
            logs.add("Cliente inativo. Cobrança cancelada: " + customerId);
            return logs;
        }
        boolean paymentSuccess = paymentGateway.charge(command.paymentCardToken(), calculatedPrice);
        logs.add(paymentMessage(customerId, calculatedPrice, paymentSuccess));
        customerRepository.saveInvoice(customerId, calculatedPrice, paymentSuccess);
        notificationService.sendReceipt(customer.get().email(), calculatedPrice);
        logs.add("E-mail disparado para: " + customer.get().email());
        return logs;
    }

    private String paymentMessage(String customerId, Money amount, boolean paid) {
        if (paid) {
            return "Pagamento processado com sucesso: R$ " + amount;
        }
        return "Falha no pagamento do cartão para: " + customerId;
    }
}
