# Relatório Técnico — Refatoração do SaaS Billing Co.

## 1. Objetivo e restrições

O objetivo da atividade foi reduzir o débito técnico do serviço de cobrança mensal
sem alterar as regras de negócio observáveis. O código legado analisado está em
`SubscriptionBillingService-pratica.java`; a implementação refatorada está em
`Refatoracao/`.

Foram preservados:

- descontos por tempo de assinatura;
- desconto para organizações sem fins lucrativos;
- regras dos cupons `BLACKFRIDAY` e `WELCOME10`;
- multa e juros por atraso;
- valor mínimo igual a zero;
- mensagens de log e fluxo de cliente ativo, pagamento e recibo.

## 2. Matriz de code smells e violações arquiteturais

| # | Linha/trecho no legado | Code smell ou princípio violado | Juros técnicos e impacto no ciclo de entrega |
|---|---|---|---|
| 1 | `SubscriptionBillingService-pratica.java:18-20` — `processMonthlyBilling` recebe `String`, `double`, `int` e `boolean` em uma lista de parâmetros | **Long Parameter List** e **Primitive Obsession** | A chamada é difícil de ler e fácil de quebrar por ordem ou tipo incorreto. Cada nova regra exige alterar a assinatura e todos os chamadores, aumentando o custo de manutenção e de testes. |
| 2 | `SubscriptionBillingService-pratica.java:18-57` — cálculo de meses, descontos, cupons, juros e limite inferior no mesmo método | **Long Method**, **violações de SRP** e alta complexidade ciclomática | Regras financeiras ficam misturadas com orquestração. Uma alteração de preço exige navegar por um método que também acessa banco, cartão e e-mail, elevando o risco de regressão e dificultando revisão de código. |
| 3 | `SubscriptionBillingService-pratica.java:29-57` — uso de `double` para valores monetários | **Primitive Obsession** e representação inadequada de dinheiro | Operações binárias podem introduzir imprecisão e o arredondamento não fica centralizado. Divergências de centavos podem aparecer em produção e tornam os testes de limite menos confiáveis. |
| 4 | `SubscriptionBillingService-pratica.java:59-86` — `DriverManager`, SQL, `Connection`, `PreparedStatement` e persistência dentro do serviço | **Dependência direta de I/O**, **Dependency Inversion Principle** violado e mistura de camadas | Testes unitários dependem de banco/configuração local ou precisam de infraestrutura real. O feedback da CI fica mais lento e falhas de ambiente podem ser confundidas com falhas de negócio. |
| 5 | `SubscriptionBillingService-pratica.java:89-96` — construção direta de `SimpleEmail` e configuração SMTP | **Dependência direta de infraestrutura** e **SRP** violado | O núcleo financeiro conhece detalhes do provedor de e-mail. A troca do SMTP ou um teste de cobrança exige configurar rede, aumentando acoplamento e tempo de entrega. |
| 6 | `SubscriptionBillingService-pratica.java:23` — `LocalDate.now()` | **Dependência temporal não injetada** | O mesmo cenário pode produzir resultados diferentes em datas distintas. Cupons e descontos por tempo ficam difíceis de reproduzir, tornando testes intermitentes e diagnósticos mais caros. |
| 7 | `SubscriptionBillingService-pratica.java:100-102` — `catch (Exception e)` que apenas transforma a falha em log | **Tratamento de erro mascarado** e captura genérica | Erros de banco, pagamento ou preparação de e-mail perdem seu tipo e não chegam ao chamador. A operação pode parecer concluída apesar de uma falha de infraestrutura, dificultando observabilidade, retry e correção. |
| 8 | `SubscriptionBillingService-pratica.java:68-77` e `:89-96` — consulta de cliente, cobrança, gravação de fatura e notificação sequenciadas no mesmo bloco | **Feature Envy/acoplamento de responsabilidades** e ausência de portas | Não há pontos de substituição para dublês de teste. Cada cenário precisa atravessar todo o fluxo concreto, aumentando o tamanho da suíte e reduzindo a capacidade de testar uma regra isoladamente. |
| 9 | `SubscriptionBillingService-pratica.java:14-16` e `:62-86` — parâmetro `planType` recebido, mas sem uso, e lógica de pagamento embutida no serviço | **Código morto** e responsabilidade excessiva | O parâmetro sem efeito induz a uma falsa expectativa de que o plano altera o preço. A lógica de pagamento não pode evoluir independentemente do caso de uso e aumenta o risco de alterações acidentais. |

## 3. Operações de refatoração aplicadas

| Necessidade | Operação aplicada | Resultado |
|---|---|---|
| Valores monetários seguros | **Replace Primitive with Value Object** e **Encapsulate Field** | `Money` é um `record` imutável baseado em `BigDecimal`, centralizando soma, subtração, multiplicação, arredondamento e não negatividade. |
| Muitos parâmetros soltos | **Introduce Parameter Object** | `BillingCommand` reúne os dados de entrada do caso de uso e substitui a assinatura extensa. |
| Dependências concretas no núcleo | **Extract Interface** e **Dependency Injection** | `CustomerRepository`, `PaymentGateway` e `NotificationService` definem portas; os adaptadores JDBC, token e e-mail ficam separados. |
| Data do sistema não determinística | **Inject Dependency** | `Clock` é recebido pelo construtor e usado com `LocalDate.now(clock)`. Os testes usam `Clock.fixed`. |
| Cálculo misturado à orquestração | **Extract Class** | `PricingCalculator` contém as regras matemáticas e não possui efeitos colaterais de banco, rede ou SMTP. |
| Método principal extenso | **Extract Method** e simplificação da fachada | `SubscriptionBillingService.processMonthlyBilling` passa a coordenar portas e delega a mensagem de pagamento para `paymentMessage`. |
| Falhas mascaradas | **Replace Exception Handler** | A fachada não captura `Exception`; os adaptadores convertem apenas exceções específicas de suas APIs em `IllegalStateException` com causa preservada. |

## 4. Métricas antes versus depois

As métricas abaixo foram obtidas por inspeção dos arquivos e contagem de linhas
do código versionado. A contagem de linhas inclui linhas em branco; a métrica
de complexidade é uma comparação de ramificações do método orquestrador, não uma
medição de uma ferramenta externa.

| Métrica | Antes — legado | Depois — refatorado | Ganho |
|---|---:|---:|---|
| Parâmetros de `processMonthlyBilling` | 8 | 1 (`BillingCommand`) | Contrato menor e mais legível |
| Linhas do método orquestrador | 86 (`:18-103`) | 21 (`:27-47`) | Fachada dentro do limite de 25 linhas legíveis |
| Tipos concretos de I/O conhecidos pela fachada | 3 (`DriverManager`, JDBC e `SimpleEmail`) | 0 | Núcleo depende somente de portas |
| Portas para serviços externos | 0 | 3 | Dublês podem ser injetados nos testes |
| Operações monetárias com `double` no núcleo | 5 ocorrências de declaração/uso | 0; usa `Money`/`BigDecimal` | Precisão e arredondamento encapsulados |
| Uso direto de data do sistema | 1 (`LocalDate.now()`) | 0; `Clock` injetado | Cenários determinísticos |
| Capturas genéricas no serviço | 1 (`catch (Exception)`) | 0 | Falhas de infraestrutura são propagadas |
| Classe dedicada ao cálculo de preço | Não existia | 1 (`PricingCalculator`, 42 linhas) | Regras testáveis sem I/O |
| Testes executáveis sem banco/SMTP | Não havia suíte para o legado | 2 classes, com cenários de preço, fluxo e falhas | CI reproduzível |

O método legado possui ramificações de cálculo, persistência, status e pagamento
no mesmo fluxo. Após a extração, a fachada possui apenas as decisões de cliente
inexistente/inativo e delega as decisões de preço ao `PricingCalculator`; isso
reduz o contexto necessário para revisar cada alteração.

## 5. Validação da preservação de comportamento

Foi executado o mesmo procedimento configurado no workflow de CI, compilando com
Java 17 e executando os testes em um diretório temporário:

```text
PricingCalculator: testes passaram.
SubscriptionBillingService: testes passaram.
```

Os testes de `PricingCalculator` cobrem limites de 12 e 24 meses, organização
sem fins lucrativos, cupons válidos e inválidos, atraso, arredondamento e valor
mínimo. Os testes de `SubscriptionBillingService` cobrem pagamento aprovado e
recusado, cliente inativo ou ausente e propagação de falhas do repositório,
gateway e notificação.

Não foram necessários PostgreSQL, SMTP ou dependências externas para essa
execução. O adaptador de e-mail permanece separado do conjunto compilado pela
CI, conforme documentado no `README.md`.

## 6. Histórico estruturado no Git

O histórico rastreável da refatoração é:

| Ordem | Commit | Evolução |
|---:|---|---|
| 1 | `f19016c` — Create SubscriptionBillingService-pratica.java | Adição do código legado base. |
| 2 | `622e519` — Introdução dos Value Objects e remoção de Primitive Obsession | Inclusão de `Money` e `BillingCommand`. |
| 3 | `1086220` — Extração das interfaces de infraestrutura e injeção de dependência | Inclusão das portas, adaptadores e `Clock` no serviço. |
| 4 | `18d763b` — Refatoração do motor de precificação e validação da suíte verde | Extração de `PricingCalculator`, testes determinísticos e workflow de CI. |

Essa sequência separa as mudanças por intenção e permite revisar ou reverter
cada etapa sem depender de um commit único com o estado final.
