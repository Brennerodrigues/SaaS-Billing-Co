package com.saas.billing;

import java.time.LocalDate;

public class PricingCalculatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final PricingCalculator CALCULATOR = new PricingCalculator();

    private static void check(String expected, String signup, String base, int overdue,
                              String promo, boolean nonProfit) {
        BillingCommand command = new BillingCommand("1", "BASIC", Money.of(base),
                LocalDate.parse(signup), overdue, promo, nonProfit, "tok_test");
        Money actual = CALCULATOR.calculate(command, TODAY);
        if (!Money.of(expected).equals(actual)) {
            throw new AssertionError("Esperado " + expected + ", recebido " + actual);
        }
    }

    public static void main(String[] args) {
        // Limites exatos: o desconto começa somente após 12 e 24 meses.
        check("100.00", "2025-11-01", "100", 0, null, false);
        check("90.00", "2025-10-01", "100", 0, null, false);
        check("90.00", "2024-11-01", "100", 0, null, false);
        check("85.00", "2024-10-01", "100", 0, null, false);
        check("70.00", "2026-10-01", "100", 0, null, true);
        check("59.50", "2024-10-01", "100", 0, null, true);

        check("70.00", "2026-10-01", "100", 0, "blackfriday", false);
        check("90.00", "2026-10-01", "100", 0, "welcome10", false);
        check("90.00", "2026-11-01", "100", 0, "WELCOME10", false);
        check("100.00", "2026-09-01", "100", 0, "WELCOME10", false);
        check("100.00", "2026-10-01", "100", 0, "UNKNOWN", false);
        check("100.00", "2026-10-01", "100", 0, "   ", false);
        check("100.00", "2026-10-01", "100", 0, " BLACKFRIDAY ", false);

        check("103.00", "2026-10-01", "100", 10, null, false);
        check("100.00", "2026-10-01", "100", -1, null, false);
        check("30.39", "2024-10-01", "100", 10, "BLACKFRIDAY", true);
        check("0.00", "2026-10-01", "10", 10, "BLACKFRIDAY", false);
        check("0.00", "2026-10-01", "-10", 0, null, false);
        check("1.01", "2026-10-01", "1.005", 0, null, false);
        check("1.03", "2026-10-01", "1", 10, null, false);
        check("63.00", "2026-10-01", "100", 0, "WELCOME10", true);

        BillingCommand command = new BillingCommand("1", "BASIC", Money.of("100"),
                LocalDate.of(2026, 10, 1), 0, "BLACKFRIDAY", false, "tok_test");
        Money outsideNovember = CALCULATOR.calculate(command, LocalDate.of(2026, 12, 15));
        if (!Money.of("100.00").equals(outsideNovember)) {
            throw new AssertionError("BLACKFRIDAY deve valer apenas em novembro.");
        }
        System.out.println("PricingCalculator: testes passaram.");
    }
}
