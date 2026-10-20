# SaaS-Billing-Co
O sistema realiza o cálculo mensal de assinaturas, aplicação de cupons promocionais, cobrança via cartão de crédito e envio de recibos por e-mail.

Para executar os testes, use Java 17 ou superior e execute o método `main` das classes `PricingCalculatorTest` e `SubscriptionBillingServiceTest` pela IDE.
Os testes usam data fixa e dependências falsas, sem PostgreSQL ou SMTP.

O GitHub Actions executa os testes a cada push ou pull request. Essa validação não compila o adaptador de e-mail, que depende de Apache Commons Email.
