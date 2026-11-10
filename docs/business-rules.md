# Venda de Ingressos: regras de negócio e decisões

Autor: Breno Oliveira de Souza · Última revisão: 8 de novembro de 2026

## Visão geral e escopo

Plataforma de venda de ingressos por lotes, construída como monólito modular em Java/Spring, para aprender escalabilidade e AWS decidindo cada ponto por regra de negócio ou por medição. Serviços só são extraídos quando o teste de carga mostrar um motivo.

Ingressos foram escolhidos porque o domínio produz, sem forçar, disputa pelo mesmo registro, reserva com expiração, leitura pesada com escrita crítica e fluxos assíncronos.

**Dentro do escopo**

- Comprador e organizador (conta única por papel), mais um ADMIN que aprova organizadores.
- Lotes por tipo de ingresso, com estoque numérico e sem assento marcado.
- Reserva de 15 minutos, pagamento simulado (cartão e PIX), cancelamento, estorno, nota fiscal simulada e ingresso por e-mail com QR code.
- Deploy temporário na AWS com o menor custo possível, teste de carga, publicação e desligamento.

**Fora do escopo (evoluções possíveis)**

- Assento marcado, check-in na portaria e boleto.
- Busca por trechos do nome, corte "X horas antes" no cancelamento e verificação real de identidade do CPF.
- Kafka entre serviços e Redis como cache compartilhado.

**Princípios de trabalho**

- Toda decisão fica registrada com o motivo (seção Registro de decisões).
- Desenvolver tudo local com Docker Compose; a AWS entra no fim, em janela curta, e é derrubada depois.
- Guardar as evidências (relatórios de carga, gráficos, vídeo) antes de desligar a nuvem.

## Metas e critérios de sucesso

O sucesso é medido primeiro por correção e depois por velocidade: vender 10.000 ingressos em 5 minutos dá cerca de 33 vendas por segundo, pouco para o banco, e a dificuldade real é a disputa pelas mesmas linhas de lote durante o pico.

| Métrica | Meta |
| --- | --- |
| Usuários simulados simultâneos | 5.000 |
| Ingressos disponíveis | 10.000 |
| Requisições no pico (média sustentada) | 200 a 500 por segundo |
| Latência p95 das consultas | abaixo de 200 ms |
| Latência p95 da reserva/compra | abaixo de 500 ms |
| Overselling (vendidos acima do estoque) | zero, verificado ao fim do teste |
| Cobrança duplicada | zero |
| Erros fora das recusas esperadas | abaixo de 1% |

p95 é o tempo abaixo do qual responderam 95% das requisições; evita que a média esconda as lentas. "Esgotado" por si só não é erro.

**Teste de base.** A primeira versão é funcional e organizada, mas ingênua (sem cache, sem controle de concorrência, sem índices além do necessário). Ela fica marcada no Git e é medida antes de qualquer otimização, para que cada melhoria tenha um número de antes e depois.

## Papéis e acesso

Existem três papéis, e cada conta tem um só: comprador, organizador e ADMIN.

- **Organizador:** estados pendente, aprovado e rejeitado. Só publica eventos depois de aprovado. O status é consultado no banco a cada operação de organizador (não fica no token), porque essas operações são poucas e a resposta fica sempre atual.
- **Autorização por propriedade:** o organizador só altera os próprios eventos e lotes.
- **ADMIN:** papel mínimo, só para aprovar ou rejeitar organizadores. Nunca é criado pelo cadastro público, apenas por carga inicial controlada.
- **CPF:** informado uma vez no cadastro da conta, vinculado a ela, único por conta e não editável pelo usuário.

**Autenticação.** Módulo isolado dentro do monólito: ele emite os tokens e o resto da aplicação só os valida, o que deixa a extração futura barata.

- Token de acesso de 15 minutos.
- Token de renovação de 15 dias, guardado como hash, com rotação a cada uso. Reuso de um token já trocado derruba a sessão inteira.
- A renovação consulta o banco e confere se a conta continua ativa, o que faz uma suspensão valer em até 15 minutos.

O login consome CPU de propósito (hash de senha). Por isso o teste de carga será rodado com e sem login, sem reduzir o custo do hash só para o teste.

## Eventos e lotes

O evento passa por rascunho, publicado e cancelado; "encerrado" também é calculado, pela data de início do evento; "esgotado" não é um estado guardado, é calculado a partir do estoque disponível de cada lote, para não existir um segundo dado a manter em sincronia.

**Edição depois de publicado.** Descrição e imagem seguem livres, mesmo com vendas. Data e local ficam travados depois da primeira venda, porque quem comprou pagou por aquele dia e lugar; para mudá-los, cancela-se o evento, com estorno integral. Alternativa futura: permitir a mudança com aviso a todos os compradores e uma janela especial de reembolso.

O lote tem os estados rascunho, publicado, encerrado e cancelado. Regras:

- **Preço congelado na reserva:** quem reservou paga o valor que viu. O preço é copiado para a reserva e para o ingresso, e o estorno usa o valor efetivamente pago.
- **Quantidade:** o organizador pode aumentar o estoque, mas não diminuir. Para parar de vender, encerra o lote.
- **Lote cancelado nunca é apagado do banco**, por causa dos registros de venda e da nota fiscal.
- O lote não pode ser cancelado depois do início do evento.

**Período de venda.** O organizador cria o lote em rascunho e o deixa pronto; ao publicá-lo, define o início (imediato ou agendado) e o fim das vendas.

- Início e fim são conferidos no instante da reserva, sem tarefa que mude o estado do lote.
- Regra de consistência: início ≤ fim ≤ início do evento.
- Depois de publicado, o organizador pode estender o fim; para antecipá-lo, usa "encerrar".
- O início agendado cria uma abertura previsível, usada no teste de abertura com cache frio e aquecido.

| Operação | Novas reservas | Reservas ativas | Ingressos pagos |
| --- | --- | --- | --- |
| Encerrar lote | bloqueadas | podem ser pagas | continuam válidos |
| Cancelar lote | bloqueadas | canceladas | estornados integralmente |
| Aumentar quantidade | liberadas | sem efeito | sem efeito |

O estorno por cancelamento de lote é integral e não segue a janela de 7 dias, porque o motivo é do organizador. Ele usa o mesmo processamento assíncrono em lotes do cancelamento de evento.

## Reserva e compra

Comprar é reservar por 15 minutos e pagar dentro desse prazo; o prazo é configurável e nada o estende.

- **Limite:** no máximo 10 ingressos por CPF em cada evento, somando todos os lotes. Contam as reservas ativas e as compras pagas.
- **Reserva guarda só a quantidade** e o preço congelado. Os ingressos individuais só são criados quando o pagamento é confirmado.
- **Ordem da transação de reserva:** (1) incrementar o contador do CPF, (2) criar a reserva, (3) decrementar o estoque do lote. A linha disputada por todos fica por último, para a trava durar o mínimo. Qualquer falha desfaz tudo.
- **Margem de pagamento:** o pagamento só pode ser iniciado se restarem pelo menos 30 segundos de reserva.

**Expiração.** Uma tarefa agendada, com intervalo configurável, busca reservas vencidas e faz a transição condicional de ativa para expirada. Só quem conseguir a transição devolve o estoque ao lote e desconta o contador do CPF. O pagamento sempre confere o prazo por conta própria e não depende de a tarefa já ter rodado. A busca precisa de um índice que cubra o estado e o instante de expiração.

**Idempotência.** Operações que criam estado ou causam efeito externo (reservar, criar evento ou lote, pagar, estornar) exigem uma chave enviada pelo cliente.

- A chave é um UUID gerado pelo cliente a cada operação nova (reenvios da mesma operação reutilizam a mesma chave), única por usuário e gravada no banco, na mesma transação da operação. Não é um segredo.
- Repetir a chave devolve o resultado original sem criar nada novo; a mesma chave com dados diferentes é rejeitada; duas requisições simultâneas com a mesma chave recebem "em andamento".
- Chaves antigas são removidas periodicamente (24 horas como ponto de partida).

## Concorrência e invariantes

O princípio único do projeto é verificar e alterar num único comando indivisível do banco ("mude para X, mas só se a condição ainda valer") e usar o número de linhas afetadas para saber quem venceu. Isso evita que duas requisições leiam o mesmo valor antigo e ambas passem.

| Invariante | Como é protegida |
| --- | --- |
| Vendidos e reservados nunca passam do estoque do lote | atualização condicional atômica no lote |
| No máximo 10 ingressos por CPF em cada evento | linha contadora por (conta, evento) com atualização condicional |
| Uma reserva sai de "ativa" uma única vez (paga, expirada ou cancelada) | transição condicional de estado |
| Um ingresso é cancelado e estornado uma única vez; o estornado nunca passa do pago | transição condicional por ingresso e estorno idempotente |
| Uma nota fiscal por pagamento | restrição de unicidade |
| Uma tentativa de compra não se duplica | chave de idempotência |
| Cada tarefa pendente roda em uma só instância | reivindicação condicional da tarefa |

A trava de linha existe também na atualização condicional e só é liberada no fim da transação. Por isso o decremento do lote vem por último.

**Estratégias a comparar no teste de carga** (a atualização condicional é a aposta inicial; os números decidem):

1. Base ingênua: ler o estoque e depois gravar. Deve mostrar overselling.
2. Lock pessimista: trava a linha do lote durante a transação.
3. Lock otimista (campo de versão): sem trava, com repetição em caso de conflito. Provável pior no pico, pois o conflito é a regra.
4. Atualização condicional atômica: uma instrução, sem leitura prévia.
5. Evolução: contador atômico no Redis na frente do banco, que exige decidir como manter duas fontes de estoque consistentes.

## Pagamento simulado

O pagamento é um mock configurável com confirmação assíncrona por webhook, para que todas as regras de reserva, expiração e estorno sejam exercitadas e não só o caminho feliz.

- **Meios:** cartão e PIX. Boleto fica de fora, porque a compensação leva dias e é incompatível com a reserva de 15 minutos.
- **PIX:** a validade do código é igual ao tempo que resta da reserva, o mesmo contador que o usuário vê na tela.
- **Webhook:** idempotente e com verificação de assinatura. O atraso e a taxa de recusa do mock são configuráveis.
- **Nada estende a reserva.** Um pagamento aprovado depois da expiração é estornado automaticamente.

| # | Cenário | Comportamento esperado |
| --- | --- | --- |
| 1 | Aprovado rapidamente | reserva vira paga, ingressos são criados, tarefas pós-pagamento são registradas |
| 2 | Recusado | pagamento marcado como recusado; a reserva segue ativa e o comprador pode tentar de novo dentro do prazo (regras abaixo) |
| 3 | Aprovado depois da expiração | estorno automático e aviso ao comprador |
| 4 | Em análise e depois aprovado ou recusado | a reserva expira no prazo normal; aprovação tardia cai no cenário 3 |
| 5 | Gateway não responde | pagamento fica com estado desconhecido; uma tarefa de conciliação consulta o estado real |
| 6 | Aviso (webhook) duplicado | o segundo aviso não produz efeito |
| 7 | Assinatura inválida | aviso rejeitado, sem efeito |

Os sete cenários não precisam ser implementados de uma vez: no checklist entram depois do fluxo principal, um por etapa, cada um com seu teste.

**Novas tentativas.** Depois de uma recusa, o comprador pode tentar de novo enquanto a reserva estiver ativa, com três regras:

- Só um pagamento em andamento por reserva de cada vez.
- No máximo 5 tentativas por reserva, para barrar o teste de cartões em sequência.
- Se dois pagamentos forem aprovados mesmo assim, a transição condicional da reserva faz o segundo ser estornado.

## Cancelamento e estorno

O comprador cancela ingressos individuais, até 7 dias depois da confirmação do pagamento e nunca depois do início do evento; numa compra de 4 ingressos, pode cancelar só 1. A regra se inspira no direito de arrependimento em compras online. Se o projeto virar produto real, a regra deve ser validada com um profissional jurídico.

**Cancelar um ingresso** acontece numa única transação:

1. Transição condicional do ingresso de válido para cancelado.
2. Redução de 1 no contador da conta no evento.
3. Devolução de 1 unidade ao lote, a linha mais disputada, por último.
4. Criação do registro de estorno com o valor daquele ingresso.
5. Registro das tarefas "processar estorno" (com chave de idempotência junto ao gateway) e "emitir nota de ajuste".

**Cancelar o evento inteiro** só é permitido ao organizador dono. Ele bloqueia novas vendas, cancela as reservas ativas e estorna integralmente todos os ingressos pagos, sem a janela de 7 dias. O estorno em massa roda de forma assíncrona em lotes, com estado por ingresso e repetição das falhas. Uma fila de mensagens (opção de evolução) entra quando os serviços forem extraídos.

## Pós-pagamento: outbox, nota fiscal e e-mail

Nada do que acontece depois do pagamento aprovado pode travar ou derrubar a confirmação, então essas tarefas vão por uma tabela de tarefas pendentes (padrão outbox) no próprio banco do monólito, sem Kafka.

**Como funciona**

- A transação que confirma o pagamento também insere as tarefas "emitir nota" e "enviar e-mail". Ou tudo acontece, ou nada.
- Cada tarefa tem tipo, referência, estado (pendente, em processamento, concluída, falha), número de tentativas e instante da próxima tentativa.
- Um executor agendado reivindica tarefas em lotes por transição condicional, executa, repete com intervalo crescente e marca falha após N tentativas.
- A entrega é "pelo menos uma vez", então toda tarefa é idempotente (uma nota por pagamento; marca de "já enviado" no e-mail).
- Na extração para serviços, o mesmo registro vira a origem das mensagens do Kafka.

**Nota fiscal (simulada).** Regras fiscais reais ficam fora do escopo.

- Uma nota por pagamento, emitida de forma assíncrona, em PDF, guardada em um bucket S3 privado com criptografia e acesso por link temporário. O CPF aparece nela, o que justifica a cifragem reversível.
- A nota original nunca é alterada. Cada ingresso cancelado gera uma nota de ajuste própria, ligada à original, pelo mesmo mecanismo do cancelamento individual.
- Falha na emissão não invalida o ingresso; a tarefa fica pendente e é repetida.

**Entrega do ingresso.** E-mail com os ingressos e QR codes. O conteúdo do QR é um código aleatório não adivinhável, nunca um identificador sequencial. Localmente, os e-mails são capturados por uma ferramenta de teste; na AWS, o envio é simulado no teste de carga e o SES real (em sandbox, só para endereços verificados) fica para uma demonstração. Check-in na portaria está fora do escopo.

**Notificações de cancelamento e estorno.**

- E-mail e aviso dentro do app, ambos criados por tarefas do outbox, idempotentes.
- A notificação é consolidada por pagamento: quem cancela 4 ingressos recebe um aviso só, mesmo com 4 notas de ajuste.
- O aviso no app é uma tabela de notificações (destinatário, tipo, mensagem, referência, lida ou não) consultada pelo front ao abrir o app e ao navegar, com a contagem de não lidas. Sem consulta periódica e sem push em tempo real, que ficam como evolução.

## Leitura: listagem, cache e paginação

A maior parte dos 200 a 500 requisições por segundo será leitura, e a compra é sempre decidida de forma exata pelo banco, nunca pelo cache.

- **Filtros:** cidade, data (específica ou período), categoria e nome do evento. Cidade e categoria são dados padronizados: estado e cidade em tabelas próprias, com seleção dependente (cidades da lista oficial do IBGE, carregadas uma vez e mantidas em cache de longa duração), e categoria em tabela própria.
- **Busca por nome:** só pelo começo do nome, usando uma versão do nome sem acento e em minúsculas guardada à parte para o índice funcionar. Limitação conhecida: quem digita uma palavra do meio não encontra.
- **Disponibilidade:** número exato de ingressos, com cache de validade curta.
- **Paginação por referência (cursor):** o cliente devolve o marcador do último item visto (data e identificador, codificados pelo servidor num texto opaco) e recebe os seguintes, sempre em ordem fixa: data mais próxima primeiro, com desempate pelo identificador. Custo constante em qualquer profundidade; sem total de resultados e sem salto para uma página. Experimento local: comparar com paginação numerada em centenas de milhares de eventos pelo plano de execução do banco.

**Cache**

- Fica na memória de cada instância (escopo inicial: uma instância). Redis fica como evolução ou experimento comparado no teste.
- A disponibilidade tem dois tempos: o suave, de 1 segundo, serve o valor antigo enquanto uma única atualização roda em segundo plano; o rígido, de 5 segundos, impede servir o dado antigo e espera uma carga real.
- Contra o stampede (muitas requisições recalculando o mesmo dado quando o cache expira): aquecer o cache dos eventos populares antes da abertura das vendas e juntar as cargas simultâneas da primeira vez.
- Dados que mudam pouco (nome, local, data) ficam em cache separado, com validade maior.
- Só a listagem principal é cacheada. Combinações de filtros se apoiam em índices, porque quase nunca se repetem.

## Segurança e dados pessoais

O CPF é guardado de duas formas, porque precisa ser recuperável para a nota fiscal e pesquisável para o limite de 10 ingressos.

- **Coluna cifrada pela aplicação** (reversível): usada só quando o valor original é realmente necessário, como na nota fiscal. A cifragem correta usa aleatoriedade, por isso não serve para buscar.
- **Coluna com HMAC e chave secreta** (irreversível): usada para buscar, contar e garantir unicidade. A chave secreta é obrigatória porque o espaço de CPFs é pequeno e um hash simples seria quebrado por força bruta.
- São duas chaves distintas (cifragem e HMAC), guardadas fora do banco. Sem a chave, um dump do banco mostra apenas bytes ilegíveis.
- O CPF nunca aparece em logs.

**Onde ficam os segredos.** São poucos e fixos: chave de cifragem do CPF, chave do HMAC, chave de assinatura dos tokens, segredo do webhook e senha do banco.

- Cada um é gerado uma vez, com gerador criptográfico seguro (cerca de 32 bytes aleatórios), e é diferente por finalidade.
- Local: arquivo .env fora do Git. AWS: Parameter Store (SecureString), lido na inicialização com a permissão da própria instância e mantido em memória. O Parameter Store não rotaciona sozinho.
- Junto de cada CPF cifrado fica a versão da chave usada, para permitir troca futura. A chave do HMAC é fixa, pois trocá-la obriga a recalcular tudo.
- A chave de idempotência não é segredo e fica no banco (seção Reserva e compra).

Outras regras:

- Tokens de renovação são guardados como hash.
- O bucket S3 das notas é privado, criptografado e acessado por link temporário.
- O webhook de pagamento verifica a assinatura de quem chama.
- O ADMIN só nasce de carga inicial controlada.

**Cadastro e limite de requisições.**

- Se o CPF ou o e-mail já existir, o cadastro responde a mesma mensagem genérica de sempre e avisa o dono da conta por e-mail, para não revelar quem tem conta.
- Limite de requisições por usuário e por tipo de operação (reservar, pagar, login), com resposta 429 e tempo de espera. Login e cadastro, que ainda não têm usuário, são limitados por IP. Valores iniciais por minuto, por usuário: leituras 60, reservar 5, pagar 10, notificações 20; login 10 por IP e 5 falhas por conta; cadastro 5 por hora por IP. O algoritmo é o balde de fichas, com o contador na memória da instância, e os valores são ajustados pela medição. O limite por IP é ajustado no teste de carga, porque os usuários simulados saem de uma só máquina.

**Limitação conhecida:** o sistema não verifica se o CPF pertence mesmo a quem se cadastra. O limite de 10 por CPF é uma barreira de negócio e não impede um robô que gere CPFs válidos.

## Máquinas de estados

Toda transição é uma atualização condicional ("mude para X, mas só se o estado atual for Y"): quem obtém uma linha afetada vence, e quem obtém zero segue o plano alternativo. O relógio é sempre o do banco (`now()`), nunca o da aplicação. A ordem global de travas é: contador, depois reserva ou ingresso, e o lote sempre por último, em todos os caminhos, para não haver impasse (deadlock).

**Reserva** (`ACTIVE`, `PAID`, `EXPIRED`, `CANCELED`; os três últimos são finais)

| Transição | Quem dispara | Condição | Efeitos |
| --- | --- | --- | --- |
| nada → ACTIVE | comprador | lote publicado e no período, evento publicado, conta ativa; estado e período do lote vêm na mesma atualização que reserva o estoque | contador +q (até 10), cria a reserva, preenche first\_sale\_at se nulo, lote reservada +q |
| ACTIVE → PAID | pagamento aprovado | status ACTIVE e expires\_at > now() | lote reservada −q e vendida +q, cria os ingressos, tarefas do outbox |
| ACTIVE → EXPIRED | tarefa de expiração | status ACTIVE e expires\_at <= now() | contador −q, lote reservada −q |
| ACTIVE → CANCELED | comprador (desistir), cancelamento de lote ou evento | status ACTIVE | contador −q, lote reservada −q |

**Pagamento** (`PENDING`, `UNDER_REVIEW`, `UNKNOWN`, `APPROVED`, `REFUSED`, `EXPIRED`). O gateway é chamado fora da transação, com o id do pagamento como chave de idempotência; o resultado chega por webhook.

| Transição | Gatilho |
| --- | --- |
| nada → PENDING | comprador paga: reserva ACTIVE com pelo menos 30 s, menos de 5 tentativas e nenhum pagamento em andamento |
| PENDING → UNDER\_REVIEW | webhook "em análise" |
| PENDING → APPROVED | webhook de aprovação (procedimento abaixo) |
| PENDING → REFUSED | webhook de recusa; a reserva segue ACTIVE |
| PENDING → UNKNOWN | o gateway não respondeu à chamada |
| PENDING → EXPIRED | PIX venceu sem pagamento |
| UNDER\_REVIEW → APPROVED ou REFUSED | webhook ou conciliação |
| UNKNOWN → APPROVED, REFUSED ou EXPIRED | conciliação consulta o gateway pelo id do pagamento; sem registro lá, vira EXPIRED |
| EXPIRED → APPROVED | webhook tardio com dinheiro movido; resulta em estorno |

A conciliação roda a cada 60 s sobre pagamentos PENDING, UNKNOWN ou UNDER\_REVIEW parados há mais de 2 minutos (valores configuráveis). O pagamento em andamento é PENDING, UNDER\_REVIEW ou UNKNOWN: só um por reserva, para não haver cobrança dupla. Webhooks fora de ordem ou saindo de estado final são ignorados e contados como anomalia.

**Procedimento de aprovação**, numa transação:

1. Registra o aviso do gateway com INSERT ... ON CONFLICT DO NOTHING; se não inseriu, é duplicata e para.
2. Atualiza o pagamento para APPROVED se o estado atual permitir, preenchendo confirmed\_at.
3. Tenta a transição ACTIVE → PAID da reserva.
4. Se deu certo: lote, ingressos e tarefas do outbox.
5. Se não deu: estorno integral (LATE\_PAYMENT para reserva expirada ou desistida; LOT\_CANCELED ou EVENT\_CANCELED; DUPLICATE\_PAYMENT se outra aprovação já pagou a reserva), tarefa de estorno e aviso ao comprador.

**Estorno.** PENDING → COMPLETED quando a tarefa chama o gateway (com o id do estorno como chave de idempotência) e ele confirma; PENDING → FAILED quando as tentativas se esgotam, o que gera alerta e exige tratamento manual (fora do escopo). A criação do estorno é condicional em payment.refunded\_cents.

**Ingresso** (`VALID`, `CANCELED`). A única transição é VALID → CANCELED, por dois caminhos. No cancelamento individual (dono, pago há menos de 7 dias, evento não iniciado) os efeitos são, nesta ordem: ingresso, contador −1, lote vendida −1, estorno, tarefas. No cancelamento em massa (lote ou evento) é o mesmo, sem a janela de 7 dias. A data do evento não muda depois da primeira venda, então pode ser lida antes sem corrida.

**Lote** (`DRAFT`, `PUBLISHED`, `CLOSED`, `CANCELED`). DRAFT → PUBLISHED define início (imediato ou agendado) e fim; PUBLISHED → CLOSED pára novas reservas e não volta; PUBLISHED ou CLOSED → CANCELED só antes do evento, com o resto assíncrono. Aumentar a quantidade (DRAFT ou PUBLISHED) e estender o fim (PUBLISHED) não mudam o estado. "Agendado", "à venda", "esgotado" e "encerrado por tempo" são calculados. A publicação do lote é independente da do evento: a venda exige os dois publicados.

**Evento** (`DRAFT`, `PUBLISHED`, `CANCELED`). DRAFT → PUBLISHED exige organizador aprovado, data futura, campos obrigatórios e pelo menos um lote. PUBLISHED → CANCELED só antes do início, numa transação que coloca o evento e todos os seus lotes em CANCELED; reservas, ingressos, estornos e avisos correm depois em lotes. Como a atualização do lote na reserva confere o estado do lote, nenhuma reserva fica ACTIVE num evento cancelado sem ser vista. Rascunhos de evento e de lote podem ser apagados de verdade; os demais nunca.

**Tarefa do outbox** (`PENDING`, `PROCESSING`, `DONE`, `FAILED`). PENDING → PROCESSING pelo executor com FOR UPDATE SKIP LOCKED; PROCESSING → DONE no sucesso; PROCESSING → PENDING com intervalo crescente na falha; PROCESSING → FAILED ao esgotar as tentativas (5, com intervalo que dobra a partir de 30 s); PROCESSING → PENDING pela recuperação quando passa de 5 minutos preso. Como duas execuções simultâneas são possíveis, todo tratador é idempotente.

## Módulos do monólito

Cada módulo é um pacote Java com uma fachada pública; o resto do pacote é interno e invisível aos demais. Um teste de arquitetura (ArchUnit) roda no build e falha se um módulo importar classes internas de outro, porque é isso que faz o monólito ser de fato modular. Os nomes de pacotes e classes seguem em inglês.

| Módulo | Responsabilidade | Tabelas |
| --- | --- | --- |
| `auth` | cadastro, login, tokens, confirmação de e-mail, papéis | account, email\_verification\_token, refresh\_token, organizer\_profile |
| `catalog` | eventos, lotes (cadastro), categorias, estados e cidades, listagem e busca | event, lot (cadastro), category, state, city |
| `sales` | reserva, contador por conta, expiração, estoque do lote | reservation, account\_event\_counter, lot (contadores) |
| `payment` | pagamento, webhook, conciliação, estorno, gateway simulado | payment, payment\_webhook\_event, refund |
| `ticketing` | ingresso, cancelamento individual e em massa, nota fiscal | ticket, invoice |
| `notification` | notificações no app, e-mail simulado, consolidação | notification |
| `platform` | outbox, idempotência, limite de requisições, relógio, configuração | outbox\_task, idempotency\_key |
| `observability` | métricas técnicas e de negócio | nenhuma |

**Lote compartilhado.** A tabela `lot` tem dois donos, divididos por coluna: o `catalog` escreve datas, preço, quantidade e estado; só o `sales` escreve `reserved` e `sold`. Assim o UPDATE condicional da reserva continua atômico, lendo estado, período e estoque na mesma linha. A regra é reforçada pelo teste de arquitetura.

**Dependências.**

- Chamadas síncronas: `sales` consulta o `catalog`; `payment` chama `sales` (confirmar reserva paga) e `ticketing` (criar ingressos); `ticketing` chama `payment` (pedir estorno) e `sales` (devolver contador e lote).
- Consequências passam pelo outbox: nota fiscal, notificação, e-mail e estornos de cancelamento em massa. `notification` e a nota fiscal do `ticketing` só reagem a tarefas, nunca são chamados direto. É este o ponto que vira tópico no Kafka mais tarde.
- `auth` não depende de ninguém; os outros só conhecem o `account_id` que vem no token.
- `platform` e `observability` são transversais e não conhecem nenhum módulo de negócio.
- O ciclo `payment` ⇄ `ticketing` ⇄ `sales` é contido por interfaces pequenas (portas), e o fluxo de aprovação fica num único orquestrador dentro do `payment`.

**Tarefas agendadas.**

| Tarefa | Módulo | Frequência inicial |
| --- | --- | --- |
| Expirar reservas | `sales` | a cada 15 s, em lote |
| Conciliar pagamentos parados | `payment` | 60 s |
| Executar o outbox / recuperar tarefas presas | `platform` | 1 s / 60 s |
| Limpar chaves de idempotência e tokens vencidos | `platform` e `auth` | 1 vez por hora |

## Modelo de dados

O banco é PostgreSQL e o modelo tem 19 tabelas em quatro grupos: identidade, catálogo, vendas, e pós-pagamento e suporte. O esquema completo está no bloco abaixo, em DBML, para colar no dbdiagram.io e gerar o diagrama.

**Convenções**

- Nomes de tabelas, colunas, classes e estados em inglês: tabelas em `snake_case` no singular, classes em `PascalCase`, estados em maiúsculas.
- Identificadores UUID v7 gerados pela aplicação; dinheiro em inteiro de centavos (`_cents`); datas em `timestamptz` (`_at`), sempre em UTC.
- Estados como texto com CHECK. Estado e cidade usam o código do IBGE como chave.
- A tabela se chama `account` e não `user`, porque `user` é palavra reservada do PostgreSQL.

**O que o próprio banco garante**

- Reservados mais vendidos nunca passam do total do lote.
- O contador por conta e evento fica entre 0 e 10, e o estornado nunca passa do pago.
- Há no máximo um pagamento em andamento por reserva (índice único parcial), um estorno por ingresso, uma nota original por pagamento e uma nota de ajuste por ingresso.
- CPF único (pelo HMAC), aviso do gateway sem duplicata e tarefas do outbox sem duplicata (pela chave de deduplicação).

**Índices.** Começam só os necessários às regras (chaves, unicidades e o índice parcial das reservas ativas por expiração). Os de listagem (data, cidade, categoria, nome por prefixo) entram medidos, comparando o plano de execução antes e depois.

```dbml
// Ticketing platform - PostgreSQL data model (paste into dbdiagram.io)
// Conventions: snake_case, singular names, UUID v7 ids generated by the app,
// money in integer cents, timestamptz in UTC, states as text + CHECK.

// ---------- Identity ----------

Table account {
  id uuid [pk]
  email varchar(254) [not null, unique, note: 'stored lowercase']
  name varchar(150) [not null]
  password_hash varchar(255) [not null]
  role varchar(20) [not null, note: 'CHECK IN (BUYER, ORGANIZER, ADMIN)']
  status varchar(20) [not null, note: 'CHECK IN (PENDING_EMAIL, ACTIVE, SUSPENDED)']
  cpf_encrypted bytea [not null, note: 'application-level encryption (reversible)']
  cpf_key_version smallint [not null, note: 'which encryption key was used']
  cpf_hmac bytea [not null, unique, note: 'HMAC with secret key: search, count, uniqueness']
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]
}

Table email_verification_token {
  id uuid [pk]
  account_id uuid [not null, ref: > account.id]
  token_hash bytea [not null, unique]
  expires_at timestamptz [not null, note: '24 hours']
  used_at timestamptz
  created_at timestamptz [not null, default: `now()`]
}

Table organizer_profile {
  account_id uuid [pk, ref: - account.id]
  public_name varchar(150) [not null]
  status varchar(20) [not null, note: 'CHECK IN (PENDING, APPROVED, REJECTED)']
  decided_by uuid [ref: > account.id, note: 'ADMIN who decided']
  decided_at timestamptz
  rejection_reason varchar(500)
  created_at timestamptz [not null, default: `now()`]
}

Table refresh_token {
  id uuid [pk]
  account_id uuid [not null, ref: > account.id]
  family_id uuid [not null, note: 'one family per session; reuse revokes the family']
  token_hash bytea [not null, unique, note: 'plain SHA-256 is enough: token is long and random']
  expires_at timestamptz [not null, note: '15 days']
  used_at timestamptz [note: 'set on rotation']
  revoked_at timestamptz
  created_at timestamptz [not null, default: `now()`]

  indexes {
    account_id
    family_id
  }
}

// ---------- Catalog ----------

Table state {
  id smallint [pk, note: 'IBGE code']
  abbreviation char(2) [not null, unique]
  name varchar(60) [not null]
}

Table city {
  id integer [pk, note: 'IBGE code']
  state_id smallint [not null, ref: > state.id]
  name varchar(120) [not null]
  normalized_name varchar(120) [not null, note: 'lowercase, no accents']

  indexes {
    (state_id, normalized_name)
  }
}

Table category {
  id smallint [pk]
  name varchar(60) [not null, unique]
  slug varchar(60) [not null, unique]
}

Table event {
  id uuid [pk]
  organizer_id uuid [not null, ref: > organizer_profile.account_id]
  name varchar(150) [not null]
  normalized_name varchar(150) [not null, note: 'lowercase, no accents; prefix search']
  description text
  image_key varchar(255) [note: 'object key only']
  category_id smallint [not null, ref: > category.id]
  city_id integer [not null, ref: > city.id]
  venue_name varchar(150) [not null]
  venue_address varchar(255) [not null]
  starts_at timestamptz [not null]
  status varchar(20) [not null, note: 'CHECK IN (DRAFT, PUBLISHED, CANCELED); ended is computed from starts_at']
  first_sale_at timestamptz [note: 'set once by the first reservation; locks date and venue edits']
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    organizer_id
  }
}

Table lot {
  id uuid [pk]
  event_id uuid [not null, ref: > event.id]
  name varchar(100) [not null]
  price_cents integer [not null, note: 'CHECK > 0']
  total_quantity integer [not null, note: 'CHECK > 0; can only increase']
  reserved_quantity integer [not null, default: 0, note: 'CHECK >= 0']
  sold_quantity integer [not null, default: 0, note: 'CHECK >= 0']
  sales_start_at timestamptz [note: 'null while DRAFT']
  sales_end_at timestamptz [note: 'null while DRAFT; CHECK start < end']
  status varchar(20) [not null, note: 'CHECK IN (DRAFT, PUBLISHED, CLOSED, CANCELED)']
  version integer [not null, default: 0, note: 'only for the optimistic-lock experiment']
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    event_id
  }

  Note: 'Table CHECK: reserved_quantity + sold_quantity <= total_quantity. Rule start <= end <= event start is enforced by the application.'
}

// ---------- Sales ----------

Table reservation {
  id uuid [pk]
  account_id uuid [not null, ref: > account.id]
  lot_id uuid [not null, ref: > lot.id]
  event_id uuid [not null, ref: > event.id, note: 'copy of lot.event_id']
  quantity integer [not null, note: 'CHECK BETWEEN 1 AND 10']
  unit_price_cents integer [not null, note: 'frozen price']
  status varchar(20) [not null, note: 'CHECK IN (ACTIVE, PAID, EXPIRED, CANCELED)']
  expires_at timestamptz [not null, note: 'created_at + 15 min (configurable)']
  payment_attempts integer [not null, default: 0, note: 'limit of 5 enforced by conditional update']
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    account_id
    expires_at [name: 'idx_reservation_expiry', note: 'partial: WHERE status = ACTIVE']
  }
}

Table account_event_counter {
  account_id uuid [not null, ref: > account.id]
  event_id uuid [not null, ref: > event.id]
  quantity integer [not null, note: 'CHECK BETWEEN 0 AND 10; active reservations + valid tickets']
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    (account_id, event_id) [pk]
  }
}

Table payment {
  id uuid [pk, note: 'also the idempotency key sent to the gateway']
  reservation_id uuid [not null, ref: > reservation.id]
  attempt_number smallint [not null, note: '1 to 5']
  method varchar(10) [not null, note: 'CHECK IN (CARD, PIX)']
  amount_cents integer [not null, note: 'CHECK > 0']
  refunded_cents integer [not null, default: 0, note: 'CHECK refunded_cents <= amount_cents']
  status varchar(20) [not null, note: 'CHECK IN (PENDING, UNDER_REVIEW, APPROVED, REFUSED, UNKNOWN, EXPIRED)']
  gateway_reference varchar(100) [unique]
  pix_code text
  pix_expires_at timestamptz
  refusal_reason varchar(100)
  confirmed_at timestamptz
  confirmation_email_sent_at timestamptz
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]

  indexes {
    reservation_id [unique, name: 'uq_payment_in_flight', note: 'partial: WHERE status IN (PENDING, UNDER_REVIEW, UNKNOWN)']
  }
}

Table payment_webhook_event {
  id uuid [pk]
  gateway_event_id varchar(100) [not null, unique, note: 'dedup: INSERT ... ON CONFLICT DO NOTHING']
  payment_id uuid [ref: > payment.id]
  payload jsonb [not null]
  received_at timestamptz [not null, default: `now()`]
  processed_at timestamptz
}

Table ticket {
  id uuid [pk]
  account_id uuid [not null, ref: > account.id, note: 'owner']
  reservation_id uuid [not null, ref: > reservation.id]
  payment_id uuid [not null, ref: > payment.id]
  lot_id uuid [not null, ref: > lot.id]
  event_id uuid [not null, ref: > event.id, note: 'copy']
  unit_price_cents integer [not null, note: 'price actually paid']
  qr_code varchar(64) [not null, unique, note: '>= 128 random bits, NOT derived from id']
  status varchar(20) [not null, note: 'CHECK IN (VALID, CANCELED)']
  paid_at timestamptz [not null, note: 'copy of payment.confirmed_at; base of the 7-day window']
  canceled_at timestamptz
  created_at timestamptz [not null, default: `now()`]

  indexes {
    account_id
  }
}

Table refund {
  id uuid [pk]
  payment_id uuid [not null, ref: > payment.id]
  ticket_id uuid [ref: > ticket.id, note: 'null for late or duplicate payments']
  amount_cents integer [not null, note: 'CHECK > 0']
  reason varchar(30) [not null, note: 'CHECK IN (TICKET_CANCELED, EVENT_CANCELED, LOT_CANCELED, LATE_PAYMENT, DUPLICATE_PAYMENT)']
  status varchar(20) [not null, note: 'CHECK IN (PENDING, COMPLETED, FAILED)']
  gateway_reference varchar(100) [unique]
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]
  completed_at timestamptz

  indexes {
    ticket_id [unique, name: 'uq_refund_per_ticket', note: 'partial: WHERE ticket_id IS NOT NULL']
  }
}

// ---------- Post-payment and support ----------

Table idempotency_key {
  account_id uuid [not null, ref: > account.id]
  key uuid [not null, note: 'generated by the client for each new operation']
  operation varchar(50) [not null]
  request_hash bytea [not null, note: 'SHA-256 of the request body']
  status varchar(20) [not null, note: 'CHECK IN (IN_PROGRESS, COMPLETED); IN_PROGRESS older than 60 s is abandoned']
  response_status smallint
  response_body jsonb
  created_at timestamptz [not null, default: `now()`]

  indexes {
    (account_id, key) [pk]
    created_at [note: 'cleanup after about 24 hours']
  }
}

Table outbox_task {
  id uuid [pk]
  type varchar(30) [not null, note: 'CHECK IN (ISSUE_INVOICE, SEND_TICKETS_EMAIL, PROCESS_REFUND, ISSUE_ADJUSTMENT_NOTE, SEND_REFUND_NOTICE)']
  reference_id uuid [not null, note: 'target entity id; no FK on purpose']
  dedup_key varchar(120) [not null, unique, note: 'built by the app, e.g. ISSUE_INVOICE:payment_id']
  status varchar(20) [not null, note: 'CHECK IN (PENDING, PROCESSING, DONE, FAILED)']
  attempts integer [not null, default: 0]
  next_attempt_at timestamptz [not null, default: `now()`]
  claimed_at timestamptz [note: 'PROCESSING for too long returns to PENDING']
  last_error varchar(500)
  created_at timestamptz [not null, default: `now()`]
  updated_at timestamptz [not null, default: `now()`]
  completed_at timestamptz

  indexes {
    next_attempt_at [name: 'idx_outbox_pending', note: 'partial: WHERE status = PENDING']
  }
}

Table notification {
  id uuid [pk]
  account_id uuid [not null, ref: > account.id]
  type varchar(30) [not null, note: 'CHECK IN (REFUND_PROCESSED, LATE_PAYMENT_REFUNDED, EVENT_CANCELED, LOT_CANCELED)']
  data jsonb [not null, note: 'parameters; text is rendered at display time']
  payment_id uuid [ref: > payment.id]
  dedup_key varchar(120) [not null, unique]
  read_at timestamptz [note: 'null = unread']
  created_at timestamptz [not null, default: `now()`]

  indexes {
    (account_id, created_at) [name: 'idx_notification_unread', note: 'partial: WHERE read_at IS NULL']
  }
}

Table invoice {
  id uuid [pk]
  number bigint [not null, unique, note: 'from a sequence; gaps possible (simulated invoice)']
  payment_id uuid [not null, ref: > payment.id]
  type varchar(15) [not null, note: 'CHECK IN (ORIGINAL, ADJUSTMENT)']
  parent_invoice_id uuid [ref: > invoice.id, note: 'null on ORIGINAL, required on ADJUSTMENT']
  ticket_id uuid [ref: > ticket.id, note: 'set on ADJUSTMENT']
  amount_cents integer [not null, note: 'CHECK > 0']
  storage_key varchar(255) [not null, note: 'PDF in the private S3 bucket']
  issued_at timestamptz [not null]

  indexes {
    payment_id [unique, name: 'uq_invoice_original', note: 'partial: WHERE type = ORIGINAL']
    ticket_id [unique, name: 'uq_invoice_adjustment', note: 'partial: WHERE type = ADJUSTMENT']
  }

  Note: 'Row exists only after the PDF is stored, so existing means issued.'
}
```

## Registro de decisões e pendências

Foram fechadas 75 decisões; as pendências abaixo ainda precisam de resposta antes ou durante o system design.

| # | Decisão | Motivo |
| --- | --- | --- |
| 1 | Venda de ingressos como projeto | gera disputa, reserva, leitura pesada e fluxos assíncronos naturalmente |
| 2 | Monólito modular primeiro, extração depois | cada extração precisa de motivo medido |
| 3 | Comprador e organizador; conta com um papel só | separa responsabilidades e evita acesso cruzado |
| 4 | Lotes por tipo com estoque numérico | foco na disputa por estoque, sem a complexidade de assento marcado |
| 5 | Meta: 5.000 usuários, 10.000 ingressos, 200 a 500 RPS, com critérios de correção | correção vem antes de velocidade |
| 6 | Teste de base ingênuo marcado no Git | dá número de antes e depois a cada melhoria |
| 7 | Reserva de 15 minutos, configurável | o comprador só paga por algo que já é dele |
| 8 | Máximo de 10 ingressos por CPF por evento, contando reservas e pagas | distribuição mais justa e barreira contra cambismo |
| 9 | Estoque por atualização condicional atômica, comparada a lock pessimista e otimista | verifica e altera num só passo |
| 10 | CPF cifrado mais HMAC com chave secreta | recuperável para nota fiscal e pesquisável para o limite |
| 11 | Contador por (conta, evento) com atualização condicional; ordem contador, reserva, lote | linha disputada por todos fica por último |
| 12 | Expiração por tarefa agendada com transição condicional; pagamento confere o prazo | previsível e sem depender da tarefa |
| 13 | Pagamento tardio é estornado automaticamente | respeita a regra dos 15 minutos |
| 14 | Cancelamento por ingresso, até 7 dias e antes do início do evento | permite cancelar parte de uma compra |
| 15 | Ingresso cancelado volta ao lote e reduz o contador do CPF | o comprador não o possui mais |
| 16 | Cancelamento de evento ou lote: estorno assíncrono em lotes | operação em massa sem travar a requisição |
| 17 | "Esgotado" calculado a partir dos lotes | evita um segundo estado para sincronizar |
| 18 | Preço congelado na reserva | justiça com quem viu o valor |
| 19 | Lote sem diminuição de quantidade; encerrar é diferente de cancelar; cancelado não é apagado | simplifica e preserva registros fiscais |
| 20 | Organizador exige aprovação de um ADMIN mínimo | evita abuso no cadastro |
| 21 | Status do organizador consultado no banco | operações raras e resposta sempre atual |
| 22 | Disponibilidade exata com cache suave de 1 s e rígido de 5 s | atraso máximo conhecido e baixo custo no banco |
| 23 | Busca por nome pelo começo | usa índice comum |
| 24 | Paginação por cursor, sem total e sem salto | custo constante em qualquer profundidade |
| 25 | Cache na memória da instância; Redis como evolução | uma única instância no escopo inicial |
| 26 | Chave de idempotência no banco, na mesma transação | atomicidade com a operação |
| 27 | Autenticação como módulo isolado; tokens de 15 min e 15 dias com rotação | extração futura barata e revogação possível |
| 28 | CPF no cadastro, único por conta, não editável | impede várias contas com o mesmo CPF |
| 29 | Cartão e PIX; mock com webhook e 7 cenários; PIX com validade do tempo restante | exercita as regras de expiração e estorno |
| 30 | Margem de 30 s para iniciar pagamento | evita pagamento quase certo de falhar |
| 31 | Outbox no monólito; nota fiscal simulada por pagamento com PDF no S3 privado | nada se perde se a aplicação cair |
| 32 | Check-in fora do escopo; teste de carga com e sem login | foco no que se quer provar |
| 33 | Lote com rascunho, publicação com início imediato ou agendado e fim; fim pode ser estendido | conferido na reserva, sem tarefa; abertura previsível para o teste |
| 34 | Evento publicado: descrição e imagem livres; data e local travados após a primeira venda | o comprador pagou por aquele dia e lugar |
| 35 | Nova tentativa após recusa: um pagamento em andamento por reserva, até 5 tentativas | evita dupla aprovação e teste de cartões em sequência |
| 36 | Segredos no .env local e no Parameter Store (SecureString) na AWS; chave de idempotência no banco | custo praticamente zero; são coisas diferentes |
| 37 | Mensagem genérica no cadastro quando CPF ou e-mail já existe | não revela quem tem conta |
| 38 | Nota de ajuste por ingresso; notificação consolidada por pagamento | mesmo mecanismo do cancelamento individual, sem 4 avisos |
| 39 | Estado, cidade e categoria em tabelas próprias, com seleção dependente | dado padronizado e filtros que usam índice |
| 40 | Listagem por data mais próxima, desempate pelo identificador; marcador opaco | ordem estável para o cursor; ordem que o comprador espera |
| 41 | Notificação por e-mail e na lista do app, consultada ao abrir e navegar (sem consulta periódica) | estorno não é urgente; poupa requisições do teste |
| 42 | Limite de requisições por usuário e operação; por IP em login e cadastro | bloqueia o que um humano não faz |
| 43 | Banco no RDS, sem IP público; motor a definir no system design | medir se o banco aguenta é parte do objetivo |
| 44 | Aplicação em EC2 t3.small; k6 em EC2 separada na mesma região | a internet doméstica não limita o teste |
| 45 | Observabilidade com Actuator, Micrometer, Prometheus e Grafana local; k6 e CloudWatch na AWS | gratuito e ensina o básico de métricas |
| 46 | E-mail simulado no teste de carga; SES real só em demonstração | usuários falsos não enviam e-mail real |
| 47 | Etapa final opcional com 2 instâncias e ALB; Redis em contêiner | mostra o limite do cache por instância e a evolução |
| 48 | Região us-east-1; sem NAT Gateway; alarme de orçamento antes de ligar | menor custo e sem surpresas de cobrança |
| 49 | Nomes de tabelas, colunas, classes e estados em inglês | padrão do mercado e coerência em todo o código |
| 50 | PostgreSQL como banco (RDS) | índices parciais, SKIP LOCKED e planos fáceis de analisar |
| 51 | Identificadores UUID v7 gerados pela aplicação | inadivinháveis e com inserção quase sequencial |
| 52 | Dinheiro em inteiro de centavos | sem arredondamento; soma e comparação exatas |
| 53 | Estados como texto com CHECK | o banco garante os valores e mudar a lista é simples |
| 54 | Lote com três contadores e CHECK de reservada mais vendida até o total | segunda defesa contra overselling |
| 55 | Contador por (conta, evento), com CHECK de 0 a 10 | o CPF é único por conta; evita espalhar o CPF |
| 56 | Confirmação de e-mail por link; recuperação de senha fora do escopo | a mensagem genérica de cadastro só faz sentido com confirmação |
| 57 | Evento encerrado é calculado; primeira\_venda (first\_sale\_at) trava data e local | sem tarefa e sem corrida entre edição e primeira reserva |
| 58 | Código do IBGE como chave de estado e cidade; categorias por carga inicial | dado oficial e estável, sem mapeamento |
| 59 | Uma reserva é de um lote só; event\_id e paid\_at copiados para reserva e ingresso | evita travar vários lotes e junções nos caminhos quentes |
| 60 | Um pagamento em andamento por reserva (índice único parcial); refunded\_cents com CHECK | o banco impõe sozinho as regras de pagamento e estorno |
| 61 | Idempotência com chave uuid; outbox com dedup\_key; notificação por tipo e parâmetros; nota só após o PDF pronto | repetições seguras e sem estado intermediário |
| 62 | Limite de requisições por balde de fichas, com contador na memória da instância | permite rajadas curtas; a etapa com ALB mostra o limite do contador local |
| 63 | Comprador pode desistir de reserva ativa; todas as condições de tempo usam o relógio do banco | libera estoque na hora; instâncias com relógios diferentes não discordam |
| 64 | Ordem global de travas: contador, reserva ou ingresso, lote por último | evita deadlock entre reservar e cancelar na mesma conta; corrige o cancelamento de ingresso |
| 65 | Pagamento aprovado com a reserva fora de ACTIVE é estornado; pagamento em andamento não é cancelado no gateway | o estoque pode já ter sido vendido; evita uma chamada externa que também falha |
| 66 | Pagamento ganha o estado EXPIRED; UNKNOWN conta como pagamento em andamento; conciliação a cada 60 s para parados há mais de 2 min | evita cobrança dupla enquanto o resultado é desconhecido |
| 67 | Webhooks fora de ordem ou saindo de estado final são ignorados e contados | estado final não muda; a anomalia fica visível nas métricas |
| 68 | Lote e evento publicam de forma independente; evento exige um lote; evento só cancela antes do início, junto com seus lotes | venda exige os dois publicados; cancelamento sem estado intermediário exposto |
| 69 | Estado e período do lote conferidos na mesma atualização que reserva o estoque | fecha a corrida entre reservar e cancelar |
| 70 | Rascunhos podem ser apagados; publicados, encerrados e cancelados nunca | nada referencia um rascunho; o restante tem registros fiscais |
| 71 | Outbox: 5 tentativas, intervalo dobrando a partir de 30 s, 5 min para tarefa presa | valores iniciais configuráveis, ajustados por medição |
| 72 | Oito módulos: auth, catalog, sales, payment, ticketing, notification, platform, observability | cada um com um dono claro e candidato a extração futura; platform e observability são transversais |
| 73 | Tabela lot dividida por coluna: catalog escreve o cadastro, sales escreve reserved e sold | mantém o UPDATE condicional atômico; separar em duas tabelas seria otimizar antes de medir |
| 74 | Teste de arquitetura (ArchUnit) no build, barrando acesso a classes internas de outros módulos | sem isso a modularidade é só convenção |
| 75 | Toda tarefa agendada usa FOR UPDATE SKIP LOCKED, como o outbox | com duas instâncias, nenhuma repete o trabalho da outra |

**Pendências**

- [ ] Período de venda do lote (início e fim) e se encerra sozinho.
- [ ] Edição de evento já publicado (data, local): o que acontece com quem já comprou.
- [ ] Nova tentativa de pagamento depois de uma recusa, dentro do prazo.
- [ ] Onde guardar as chaves de cifragem e HMAC e o custo na AWS.
- [ ] Mensagem de CPF já cadastrado (privacidade).
- [ ] Nota de ajuste por ingresso ou consolidada por pagamento no cancelamento em massa.
- [ ] Cidade e categoria como tabelas próprias.
- [x] Formato do marcador do cursor e ordenação fixa.
- [x] Notificações de cancelamento e estorno por e-mail.
- [x] Limite de requisições: valores iniciais definidos, ajustados pela medição.
- [x] Métricas e observabilidade para o teste de carga.
- [x] Serviços da AWS usados e o custo de cada um.
- [x] Quem cadastra as categorias (ADMIN ou carga inicial).

## Infraestrutura na AWS e observabilidade

Tudo é desenvolvido e medido localmente; a AWS entra numa janela curta de poucas horas, na região us-east-1, e é desligada logo depois. O plano gratuito atual funciona por créditos (até cerca de $200 em até 6 meses), então o custo é proporcional às horas ligadas.

| Componente | Escolha | Motivo |
| --- | --- | --- |
| Aplicação | EC2 t3.small em sub-rede pública; sobe para c7i-flex.large se virar gargalo | 1 GB de memória do t3.micro é apertado para Spring Boot sob carga |
| Banco | RDS db.t3.micro em sub-rede privada, sem IP público (motor a definir no system design) | medir se o banco aguenta é parte do objetivo; IP público de banco é cobrado |
| Notas fiscais | S3 privado e criptografado | centavos para alguns PDFs |
| Segredos | Parameter Store (SecureString) | guardar custa zero |
| E-mail | envio simulado no teste de carga; SES real só em demonstração | 5.000 usuários falsos não enviam e-mail de verdade; o SES novo fica em sandbox |
| Gerador de carga | k6 em uma EC2 separada, na mesma região, só durante o teste | a internet doméstica não limita o teste |
| Redis (experimento) | contêiner na EC2 | ElastiCache está fora do plano gratuito |
| Balanceador | ALB apenas na etapa final opcional, com duas instâncias | mostra o problema do cache por instância e justifica o Redis |
| Monitoramento | CloudWatch básico e alarme de orçamento (Budgets) | gratuito dentro dos limites |

**Regras de custo**

- Conferir no console de cobrança se a conta está no plano Free ou Paid e ativar o alarme de orçamento antes de ligar qualquer recurso.
- Banco sem IP público e sem NAT Gateway (cobrado por hora e por GB): a EC2 fica em sub-rede pública e o banco em sub-rede privada.
- O desligamento é uma etapa do checklist, com conferência de que não sobrou instância, banco, volume nem IP. O custo estimado de 6 horas de t3.small e db.t3.micro é cerca de $0,25, a confirmar na calculadora da AWS.

**Observabilidade.** O Spring Boot Actuator com Micrometer expõe as métricas da aplicação, o Prometheus as coleta e o Grafana as mostra, tudo em Docker Compose, grátis e rodando local. Na AWS ficam o k6 e o CloudWatch básico, e os relatórios do k6 são guardados como evidência.

- Técnicas: requisições por segundo, p95 e p99 por endpoint, taxa de erro, CPU e memória da aplicação e do banco, uso do pool de conexões.
- De negócio: reservas criadas, recusadas por esgotado, recusadas pelo limite de CPF, expiradas, tarefas pendentes do outbox e consultas por segundo ao banco (efeito do cache).

## Plano de medição

Cada rodada de teste de carga responde a uma pergunta de negócio, e os relatórios, gráficos e o vídeo devem ser guardados antes de derrubar a AWS, porque depois não há como provar nada.

| Rodada | O que prova | Métrica principal |
| --- | --- | --- |
| Base ingênua | onde quebra e que há overselling sem controle | vendidos acima do estoque; p95 |
| Lock pessimista, lock otimista, atualização condicional | qual estratégia de estoque aguenta o pico | RPS sustentado; p95 da reserva; erros |
| Contador por CPF | o limite resiste a requisições simultâneas do mesmo CPF | nenhum CPF acima de 10 |
| Índices e cursor | ganho nas consultas e na paginação | plano de execução; p95 das consultas |
| Cache com validade suave e rígida | alivio do banco na leitura | consultas por segundo ao banco; p95 |
| Abertura das vendas com cache frio e aquecido | efeito do stampede | pico de consultas ao banco no primeiro segundo |
| Com e sem login | custo de CPU do hash de senha | CPU; p95 da reserva |
| Expiração com reserva curta (1 minuto) sob carga | devolução correta de estoque e contador | estoque final igual ao esperado |
| Sete cenários do pagamento | cada caminho se comporta como definido | estado final de reserva, pagamento e estorno |
| Cancelamento em massa | estorno assíncrono sem travar a aplicação | tempo total; tarefas com falha |
| Duas instâncias com ALB, com e sem Redis | efeito do cache por instância e o ganho do cache compartilhado | consultas ao banco por segundo; consistência do número exibido |

Ordem de execução: local, com Docker Compose, até os números fecharem; depois uma janela curta e planejada na AWS, com alarme de orçamento ativo, e desligamento imediato ao fim.
