# Fluxo de Git

Como o código deste projeto é versionado. O objetivo é histórico legível, `main` sempre verde e commits que contam a evolução do sistema por etapa do checklist.

## Princípios

- `main` está sempre estável: compila, passa em `./mvnw verify` e na pipeline do GitHub Actions.
- Mudanças pequenas e frequentes. Cada commit entrega um passo coerente e testado.
- Projeto individual: sem revisão obrigatória por outra pessoa, mas o fluxo simula o de um time (branch, Pull Request, CI).

## Branches

| Prefixo | Uso | Exemplo |
| --- | --- | --- |
| `main` | código estável | `main` |
| `feat/` | nova funcionalidade | `feat/outbox-worker` |
| `fix/` | correção de bug | `fix/reservation-expiry-race` |
| `refactor/` | mudança interna sem alterar comportamento | `refactor/payment-facade` |
| `test/` | testes e cenários de carga | `test/k6-reserve-scenario` |
| `docs/` | documentação | `docs/git-workflow` |
| `chore/` | build, dependências, CI | `chore/bump-testcontainers` |

Regras:

- Nome em minúsculas, com hífens, em inglês, curto e descritivo.
- Uma branch por funcionalidade ou fase do checklist. Vida curta: de horas a poucos dias.
- Branches de trabalho são apagadas após o merge.
- Nunca reescrever o histórico da `main` (sem `push --force`).

## Mensagens de commit

Padrão [Conventional Commits](https://www.conventionalcommits.org/), em inglês, no modo imperativo:

```
<type>(<scope opcional>): <descrição curta em minúsculas>
```

Tipos usados:

| Tipo | Quando |
| --- | --- |
| `feat` | funcionalidade nova |
| `fix` | correção de bug |
| `refactor` | refatoração sem mudar comportamento |
| `test` | criação ou ajuste de testes |
| `docs` | documentação |
| `build` | Maven, Docker, dependências |
| `ci` | GitHub Actions |
| `chore` | tarefas de manutenção |
| `perf` | melhoria de desempenho comprovada por medição |

Exemplos:

```
feat(sales): reserve tickets with conditional atomic update
fix(payment): ignore duplicated webhook events
test(sales): add concurrency test for lot stock
docs: add business rules and data model
ci: run integration tests with Testcontainers
perf(catalog): cache event listing with soft and hard TTL
```

Boas práticas:

- Descrição com até cerca de 72 caracteres, sem ponto final.
- Um assunto por commit: não misturar `feat` com `refactor` no mesmo commit.
- Escopo = nome do módulo (`auth`, `catalog`, `sales`, `payment`, `ticketing`, `notification`, `platform`, `observability`).
- Mudança incompatível: adicionar `!` após o tipo (`feat(sales)!: ...`) e explicar no corpo.

## Fluxo de trabalho

1. Atualizar a base: `git switch main && git pull`.
2. Criar a branch: `git switch -c feat/nome-da-mudanca`.
3. Implementar em commits pequenos, rodando `./mvnw verify` antes de cada push.
4. Subir a branch e abrir um Pull Request para `main`.
5. Aguardar a pipeline ficar verde.
6. Fazer o merge com **squash** quando a branch tiver commits de ajuste (ruído) ou **merge commit/rebase** quando cada commit já for significativo. Em seguida, apagar a branch.
7. Atualizar a `main` local e seguir para a próxima etapa.

Mudanças triviais de documentação podem ir direto para a `main`.

## Proteção da `main` (configuração no GitHub)

Em *Settings > Branches > Branch protection rule* para `main`:

- exigir Pull Request antes do merge;
- exigir que o check de CI (`Build and test`) passe;
- bloquear force push e exclusão da branch.

## Pipeline (CI)

A cada push e Pull Request o GitHub Actions executa `./mvnw verify`, que roda testes unitários, o teste de arquitetura (ArchUnit) e os testes de integração com Testcontainers. Um Pull Request só entra com a pipeline verde.

## Arquivos que não vão para o repositório

- `.env` (segredos locais); apenas o `.env.example` é versionado.
- Pasta `target/`, arquivos de IDE e relatórios de carga brutos.
- Nunca commitar senhas, chaves da AWS ou tokens.

## Tags e releases

Marcos do projeto recebem tags semânticas simples, por exemplo `v0.1.0` (fundação) e `v1.0.0` (resultado final com a comparação das estratégias de concorrência medida).
