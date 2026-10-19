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
        
        LocalDate signupDate = command.signupDate();
        LocalDate today = LocalDate.now(clock);

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

        calculatedPrice = calculatedPrice.nonNegative().rounded();

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
        if (paymentSuccess) {
            logs.add("Pagamento processado com sucesso: R$ " + calculatedPrice);
        } else {
            logs.add("Falha no pagamento do cartão para: " + customerId);
        }

        customerRepository.saveInvoice(customerId, calculatedPrice, paymentSuccess);
        notificationService.sendReceipt(customer.get().email(), calculatedPrice);
        logs.add("E-mail disparado para: " + customer.get().email());

        return logs;
    }
}
