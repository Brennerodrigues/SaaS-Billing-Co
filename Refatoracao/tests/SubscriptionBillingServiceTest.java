package com.saas.billing;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubscriptionBillingServiceTest {
    private static class FakeRepository implements CustomerRepository {
        Optional<Customer> customer = Optional.of(new Customer("cliente@example.com", "ACTIVE"));
        final List<String> events = new ArrayList<>();
        RuntimeException findFailure;
        RuntimeException saveFailure;

        public Optional<Customer> findById(String id) {
            events.add("find:" + id);
            if (findFailure != null) throw findFailure;
            return customer;
        }

        public void saveInvoice(String id, Money amount, boolean paid) {
            events.add("save:" + id + ":" + amount + ":" + paid);
            if (saveFailure != null) throw saveFailure;
        }
    }

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);

    private static BillingCommand command(String token) {
        return new BillingCommand("1", "BASIC", Money.of("100"),
                LocalDate.of(2026, 10, 1), 10, "BLACKFRIDAY", false, token);
    }

    private static SubscriptionBillingService service(FakeRepository repository) {
        PaymentGateway gateway = (token, amount) -> {
            repository.events.add("charge:" + token + ":" + amount);
            return new TokenPaymentGateway().charge(token, amount);
        };
        NotificationService notification = (email, amount) ->
                repository.events.add("notify:" + email + ":" + amount);
        return new SubscriptionBillingService(repository, gateway, notification, CLOCK);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Resultado inesperado.");
    }

    private static void expectFailure(RuntimeException failure, Runnable action) {
        try {
            action.run();
            throw new AssertionError("A falha deveria ser propagada.");
        } catch (RuntimeException actual) {
            check(actual == failure);
        }
    }

    public static void main(String[] args) {
        FakeRepository repository = new FakeRepository();
        List<String> logs = service(repository).processMonthlyBilling(command("tok_test"));
        check(logs.equals(List.of("Pagamento processado com sucesso: R$ 72.10",
                "E-mail disparado para: cliente@example.com")));
        check(repository.events.equals(List.of("find:1", "charge:tok_test:72.10",
                "save:1:72.10:true", "notify:cliente@example.com:72.10")));

        for (String token : new String[]{"invalid", null}) {
            repository.events.clear();
            logs = service(repository).processMonthlyBilling(command(token));
            check(logs.equals(List.of("Falha no pagamento do cartão para: 1",
                    "E-mail disparado para: cliente@example.com")));
            check(repository.events.equals(List.of("find:1", "charge:" + token + ":72.10",
                    "save:1:72.10:false", "notify:cliente@example.com:72.10")));
        }

        repository.events.clear();
        repository.customer = Optional.of(new Customer("cliente@example.com", "INACTIVE"));
        check(service(repository).processMonthlyBilling(command("tok_test"))
                .equals(List.of("Cliente inativo. Cobrança cancelada: 1")));
        check(repository.events.equals(List.of("find:1")));

        repository.events.clear();
        repository.customer = Optional.empty();
        check(service(repository).processMonthlyBilling(command("tok_test")).isEmpty());
        check(repository.events.equals(List.of("find:1")));

        FakeRepository failingRepository = new FakeRepository();
        RuntimeException failure = new IllegalStateException("Falha de infraestrutura");
        failingRepository.findFailure = failure;
        expectFailure(failure, () -> service(failingRepository).processMonthlyBilling(command("tok_test")));
        check(failingRepository.events.equals(List.of("find:1")));

        failingRepository.findFailure = null;
        failingRepository.events.clear();
        SubscriptionBillingService failingGateway = new SubscriptionBillingService(failingRepository,
                (token, amount) -> { throw failure; },
                (email, amount) -> { throw new AssertionError("Não deve notificar."); }, CLOCK);
        expectFailure(failure, () -> failingGateway.processMonthlyBilling(command("tok_test")));
        check(failingRepository.events.equals(List.of("find:1")));

        failingRepository.saveFailure = failure;
        failingRepository.events.clear();
        expectFailure(failure, () -> service(failingRepository).processMonthlyBilling(command("tok_test")));
        check(failingRepository.events.equals(List.of("find:1", "charge:tok_test:72.10", "save:1:72.10:true")));

        failingRepository.saveFailure = null;
        failingRepository.events.clear();
        SubscriptionBillingService failingNotification = new SubscriptionBillingService(failingRepository,
                new TokenPaymentGateway(), (email, amount) -> { throw failure; }, CLOCK);
        expectFailure(failure, () -> failingNotification.processMonthlyBilling(command("tok_test")));
        check(failingRepository.events.equals(List.of("find:1", "save:1:72.10:true")));
        System.out.println("SubscriptionBillingService: testes passaram.");
    }
}
