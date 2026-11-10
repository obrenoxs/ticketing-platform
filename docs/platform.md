# Módulo `platform`: o que está implementado

Estado: **fases 1 e 2 concluídas** (fundação e primeira parte do `platform`). Idempotência e outbox são as próximas peças do módulo e ainda não existem.

O `platform` é transversal: oferece recursos técnicos a todos os módulos e **não conhece nenhum módulo de negócio**. Seguem a regra de pacotes do projeto: `api` é o contrato público e `internal` fica escondido (validado pelo ArchUnit no build).

## Visão geral

| Peça | Pacote | Para que serve |
| --- | --- | --- |
| `AppProperties` | `platform.api` | todas as regras numéricas do negócio, tipadas e validadas |
| `IdGenerator` | `platform.api` | geração de ids UUID v7 |
| `DatabaseClock` | `platform.api` | horário único, o do banco |
| `@RateLimited` e `RateLimitPolicy` | `platform.api` | declarar o limite de requisições de um endpoint |
| `SoftHardCache` | `platform.api` | cache de leitura com TTL macio e duro |
| `ErrorCode`, `BusinessException`, `PlatformErrorCode` | `platform.api` | erros de negócio padronizados |
| `GlobalExceptionHandler` | `platform.internal.web` | resposta de erro no formato `ProblemDetail` (RFC 9457) |
| `TokenBucket`, `KeyedRateLimiter`, `RateLimitInterceptor` | `platform.internal` | mecanismo do limite de requisições |
| `RequestIdFilter` | `platform.internal` | identificador de cada requisição nos logs |

## Fundação (fase 1)

- Projeto Spring Boot 4.1.2 com Java 25 e PostgreSQL 17 em Docker Compose (porta 5433 no host).
- Migrações com Flyway (`V1__baseline.sql`), validação do schema pelo Hibernate.
- Actuator em porta separada (8081); segurança provisória sem estado, com a rota de saúde aberta e o resto negado até a fase de `auth`.
- Teste de arquitetura (ArchUnit) que impede um módulo de importar classes internas de outro.
- Testes de integração com Testcontainers e pipeline de CI no GitHub Actions.

## Tratamento de erros

Toda regra de negócio violada lança `BusinessException` com um `ErrorCode`, que carrega o nome do erro e o status HTTP. O `GlobalExceptionHandler` converte tudo em `ProblemDetail` com a propriedade `code`:

```json
{
  "status": 429,
  "detail": "Too many requests. Try again later.",
  "code": "RATE_LIMITED"
}
```

- Falhas de validação (`@Valid`) saem com `code = VALIDATION_FAILED` e a lista de campos em `errors`.
- Qualquer erro inesperado vira `500` com `code = INTERNAL_ERROR` e a mensagem não vaza detalhes internos.
- Cada módulo terá o seu próprio enum de códigos (por exemplo `SalesErrorCode`). O `platform` tem apenas o `PlatformErrorCode`, hoje com `RATE_LIMITED`.

## Configuração tipada (`AppProperties`)

Um record raiz com o prefixo `app` e records aninhados por assunto. Os valores padrão vêm do próprio código, a partir das regras de negócio, e podem ser sobrescritos no `application-*.yml`. A aplicação **falha na subida** se um valor for inválido.

| Grupo | Propriedade | Padrão |
| --- | --- | --- |
| `reservation` | `ttl` | 15 min |
| | `max-tickets-per-account-event` | 10 |
| | `min-time-to-pay` | 30 s |
| `payment` | `max-attempts` | 5 |
| | `reconciliation-interval` | 60 s |
| | `reconciliation-stuck-after` | 2 min |
| `outbox` | `max-attempts` | 5 |
| | `backoff-base` | 30 s |
| | `stuck-after` | 5 min |
| `idempotency` | `in-progress-timeout` | 60 s |
| | `retention` | 24 h |
| `cache` | `soft-ttl` / `hard-ttl` | 1 s / 5 s |
| `rate-limit` | `enabled` | `true` |
| | `reads-per-minute` | 60 |
| | `reserves-per-minute` | 5 |
| | `payments-per-minute` | 10 |
| | `notifications-per-minute` | 20 |

Decisão: um único record raiz concentra a configuração enquanto o projeto é pequeno. Se crescer demais, divide-se em um record por módulo.

O limite de requisições fica desligado **apenas** no perfil `local`. Nos demais perfis o padrão `true` vale. No teste de carga ele será ajustado de forma explícita.

## Geração de ids (`IdGenerator`)

- Gera **UUID v7** (com timestamp no início), pela biblioteca `uuid-creator`.
- Motivo: ids ordenados no tempo mantêm as inserções no fim do índice da chave primária, ao contrário do UUID v4, que se espalha pelo índice. O PostgreSQL 17 não gera v7 nativamente, então a aplicação gera.
- Os módulos dependem da interface `IdGenerator`; a implementação (`UuidV7IdGenerator`) é interna e injetada pelo Spring.

## Relógio do banco (`DatabaseClock`)

- Devolve o `now()` do PostgreSQL, para que todas as instâncias da aplicação concordem sobre o horário.
- `now()` retorna o instante de **início da transação**, o mesmo valor que os UPDATEs condicionais enxergam. O Java só pede o horário quando precisa calcular algo (por exemplo `expiresAt`). Comparações críticas continuam no SQL.
- Cada chamada é uma ida ao banco; usar com critério.

## Limite de requisições

Algoritmo **token bucket** (balde de fichas): aceita rajadas até a capacidade e depois impõe um ritmo médio.

1. `TokenBucket`: capacidade e taxa de reposição, com relógio injetável (testes sem `sleep`). Thread-safe.
2. `KeyedRateLimiter`: um balde por `política:identificador`, guardado num cache Caffeine limitado (100 mil chaves, descarte após 2 min parado) para impedir esgotamento de memória.
3. `RateLimitInterceptor`: lê a anotação `@RateLimited` do endpoint, identifica o cliente e responde **429** com o cabeçalho `Retry-After` quando o limite estoura.

Uso em um controller:

```java
@PostMapping("/reservations")
@RateLimited(RateLimitPolicy.RESERVE)
public ReservationResponse reserve(...) { ... }
```

Identificação do cliente: a conta autenticada (`acc:<id>`) ou, sem autenticação, o IP (`ip:<endereço>`). O cabeçalho `X-Forwarded-For` **não** é usado por ser forjável; atrás do balanceador na AWS a leitura do IP real será habilitada explicitamente no perfil `aws`.

Limitação conhecida: os baldes ficam na memória de cada instância. Com duas instâncias, o limite efetivo por cliente dobra. Isso será observado na etapa de medição e justifica o experimento com Redis.

## Rastreamento (`X-Request-Id`)

- O filtro roda antes do Spring Security. Aproveita o `X-Request-Id` recebido se estiver no formato `[A-Za-z0-9._-]` de até 64 caracteres; senão gera um novo (proteção contra injeção de linhas falsas no log).
- O id vai para o MDC, aparece em todas as linhas de log (`%X{requestId}`), volta no cabeçalho da resposta e é removido do MDC ao final da requisição.

## Cache com TTL macio e duro (`SoftHardCache`)

| Idade do valor | Comportamento |
| --- | --- |
| menor que o TTL macio (1 s) | devolve do cache |
| entre o macio e o duro (1 s a 5 s) | devolve o valor antigo e recarrega **uma vez** em segundo plano |
| maior que o TTL duro (5 s) | descarta e carrega de forma síncrona; chamadas simultâneas compartilham uma única carga |

- Implementado sobre o `LoadingCache` do Caffeine: `refreshAfterWrite` (macio), `expireAfterWrite` (duro) e `recordStats()` para medir acertos.
- Se a recarga falhar, o valor antigo continua servido até o TTL duro.
- O cache serve apenas a leitura (listagem). **A reserva sempre decide no banco**, com UPDATE condicional, então um valor desatualizado na tela nunca causa venda acima do estoque.
- Limitação conhecida: cache por instância; com duas instâncias cada uma tem o seu.

## Testes desta fase

| Componente | Onde roda | O que garante |
| --- | --- | --- |
| `AppProperties` | PC | padrões corretos e falha com valor inválido |
| `UuidV7IdGenerator` | PC | versão 7, unicidade, ordem temporal |
| `DatabaseClock` | Docker (notebook) | horário próximo ao da aplicação; mesmo valor dentro de uma transação |
| `TokenBucket` | PC | capacidade, reposição, teto, tempo de espera, **1000 threads disputando 100 fichas e exatamente 100 passam** |
| `KeyedRateLimiter` | PC | independência entre clientes e entre políticas |
| `RateLimitInterceptor` | PC | 429 com `Retry-After`, endpoints sem anotação, limite desligado |
| `RequestIdFilter` | PC | geração, reaproveitamento, descarte de valor inválido, limpeza do MDC |
| `SoftHardCache` | PC | TTL macio, TTL duro, recarga que falha, **50 threads e uma única carga** |

## Pendências do módulo

- Idempotência (chaves de requisição, `IN_PROGRESS`, retenção).
- Outbox transacional (SKIP LOCKED, tentativas, recuperação de tarefas presas).
- Expor `SoftHardCache.stats()` como métrica na fase de observabilidade.
- Habilitar leitura do IP real atrás do balanceador no perfil `aws`.
