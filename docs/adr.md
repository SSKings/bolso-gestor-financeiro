# Decisões de arquitetura (ADRs)

## ADR-001 — Reescrever o "Gestor Financeiro" como Bolso

**Contexto:** o projeto anterior era um CRUD sem uso prático (lançamento sem descrição, cartão sem fatura, "tipo" e
"fonte" genéricos e compartilhados entre usuários) e tinha falhas graves: o `usuario_id` vinha no corpo da requisição
(qualquer pessoa logada lia, alterava e apagava dados de outra), o cartão guardava o número completo, o saldo era um
número alterado à mão e o banco era criado por `ddl-auto=update`.

**Decisão:** reescrever o domínio do zero, mantendo o histórico do repositório. Stack e padrões de segurança do
ShopEasy (Spring Boot 3.5, Flyway, PostgreSQL, React + TypeScript, Docker).

## ADR-002 — Espaços compartilhados desde o início

**Decisão:** os dados pertencem a um **espaço**, não ao usuário. Cada pessoa tem um espaço "Pessoal" e pode criar
outros (casa, viagem, negócio) e convidar pessoas. Papéis: OWNER (administra) e MEMBER (usa).

**Por quê agora:** mudar o dono dos dados depois exigiria migrar tudo. Com o espaço desde o começo, uso individual é
só um espaço de um membro.

**Consequência:** toda rota de dados é `/api/workspaces/{id}/...` e passa pela guarda `WorkspaceAccess`.

## ADR-003 — Isolamento entre espaços em três camadas

1. **Rota:** `WorkspaceAccess` confere a participação do usuário do token a cada chamada (404 para quem não participa).
2. **Consulta:** repositórios só têm buscas com `workspaceId` (não existe "buscar conta só pelo id").
3. **Banco:** chaves estrangeiras compostas `(id, workspace_id)`: um lançamento não aponta para conta ou categoria de
   outro espaço nem com erro no código.

## ADR-004 — Saldo calculado, nunca guardado

**Decisão:** saldo = saldo inicial + receitas − despesas ± transferências, até hoje (lançamento futuro é previsão).
Calculado numa consulta SQL. **Por quê:** no projeto anterior, o saldo guardado divergia dos lançamentos; com
lançamentos simultâneos, um "saldo += valor" também perderia atualizações. Custo: uma agregação por consulta, com
índices por conta e data.

## ADR-005 — Dinheiro e datas

- `NUMERIC(14,2)` no banco, `BigDecimal` no Java, **centavos inteiros** no front (nada de ponto flutuante).
  Valor sempre positivo; o tipo diz o sentido.
- "Hoje" no fuso do usuário (`America/Sao_Paulo`, configurável): o servidor roda em UTC e, à noite, já seria amanhã.

## ADR-006 — Testes contra PostgreSQL de verdade (Testcontainers)

**Decisão:** os testes de integração sobem o mesmo PostgreSQL da produção (imagem fixada por digest), não H2.
**Por quê:** as regras dependem de recursos do PostgreSQL (chaves compostas, `CHECK`, `FILTER`, travas de linha) que o
H2 imita mal. Em dev, `spring-boot-docker-compose` sobe o mesmo banco sozinho.

## ADR-007 — Sessão: JWT no localStorage, protegido por CSP

**Decisão:** token JWT (12 h, revogável pela versão) no localStorage, para o app abrir logado no celular.
**Risco:** um XSS leria o token. **Mitigação:** o front não injeta HTML e o nginx serve com CSP `script-src 'self'` e
`connect-src 'self'`. Alternativa futura: refresh token em cookie `HttpOnly` com proteção CSRF.

## ADR-008 — Convite por link com o código no fragmento

**Decisão:** o dono gera um link `https://…/convite#codigo=…`. Só o hash do código fica no banco; vale uma vez, por
72 h. **Por quê o fragmento:** o navegador não envia a parte depois de `#` ao servidor, então o código não aparece em
log de acesso nem de proxy (lição do e-mail do ShopEasy). Sem login, o código espera na sessão do navegador até a
pessoa entrar.

## ADR-009 — Cartão de crédito: fatura, parcelas e pagamento

- **Fatura do mês M** fecha no dia de fechamento de M; compra até esse dia (inclusive) entra em M, depois em M+1. O
  vencimento fica no mesmo mês se for depois do fechamento; senão, no mês seguinte. Dias de 1 a 28 (todo mês tem).
- **Parcelas**: geradas na compra (`card_installments`), uma por fatura; o total é dividido em centavos e o resto vai
  na primeira (R$ 100 em 3× = 33,34 + 33,33 + 33,33). Editar a compra refaz as parcelas.
- **Gasto conta no mês da fatura**, na categoria da compra. **Pagar a fatura** é um lançamento `CARD_PAYMENT`: sai da
  conta, mas não é gasto (contaria a mesma compra duas vezes). Só é criado pela rota da fatura.
- **Limite disponível** = limite − (parcelas lançadas − pagamentos).
- **Só os 4 últimos dígitos** do cartão: o número completo não tem utilidade para o app e seria um dado a proteger
  (o projeto anterior o guardava).
- Fora do escopo por enquanto: estorno, anuidade/juros e fatura de cartão adicional.

## ADR-010 — Contas fixas com confirmação, orçamento e previsão do mês

- **Conta fixa não vira lançamento sozinha.** Valores mudam (luz, água) e um lançamento automático com valor errado
  engana o saldo. Cada mês a ocorrência fica pendente até ser **confirmada com o valor real**; a confirmação cria o
  lançamento ligado à conta fixa e ao mês. Um índice único (conta fixa, mês) impede a confirmação dupla mesmo com
  cliques simultâneos. Excluir a conta fixa mantém os lançamentos (só tira o vínculo).
- **Status por vencimento**: atrasada, vence em breve (7 dias), pendente, paga. O lembrete fora do app (Telegram)
  fica para a fase 4; por enquanto, o painel mostra o que está atrasado ou vencendo.
- **Orçamento**: um limite mensal por categoria de despesa; o gasto inclui as parcelas de cartão do mês (mesma regra
  do painel). Atenção a partir de 80%; estourado acima de 100%.
- **Previsão do mês atual**: saldo de hoje + receitas que vão entrar − contas fixas não pagas (inclusive atrasadas) −
  lançamentos agendados − o que falta das faturas que vencem no mês. Sem contagem dupla: conta fixa confirmada com data
  futura entra só como agendada; pagamento de fatura agendado já reduz o que falta da fatura.

## ADR-011 — Importação de extrato em duas etapas, sem duplicar

- **Prévia → revisão → importação.** A prévia lê o arquivo e sugere categorias sem gravar nada; a pessoa confere e
  manda só as linhas que quer. Nada entra "às cegas".
- **OFX sem parser XML.** OFX 1 é SGML (tags sem fechamento) e OFX 2 é XML; ler os dois com padrões de texto resolve
  os formatos dos bancos brasileiros e elimina XXE por construção. CSV com detecção de separador (`;` ou `,`), de
  codificação (UTF-8 ou Windows-1252) e de colunas pelos nomes usuais (Nubank, Inter, genérico).
- **Identidade da linha**: FITID do OFX, Identificador do CSV do Nubank ou, sem isso, hash de data + valor +
  descrição + ordem da repetição (dois cafés iguais no mesmo dia são dois lançamentos; reimportar o mesmo arquivo
  dá os mesmos hashes). Índice único (conta, identidade) no banco: nem duas importações simultâneas duplicam.
- **Parecido ≠ duplicado.** Lançamento manual com mesma data, valor e tipo é marcado "parecido" e vem desmarcado — a
  pessoa decide (pode ser o mesmo gasto lançado antes à mão).

## ADR-012 — Regras de categorização como texto, não regex

"Descrição contém X → categoria", comparando sem acento e sem maiúsculas; a regra mais longa vence ("uber eats"
ganha de "uber"). Regex daria mais poder, mas abre ReDoS e é difícil para quem não programa. Regra cuja categoria
foi arquivada é ignorada; regra de despesa não vale para receita (cai em "Outros").

## ADR-013 — Telegram por long polling, agindo como a pessoa

- **Long polling, não webhook**: a API só faz conexões de saída; não precisa de URL pública nem de porta aberta, e
  funciona igual em casa e no servidor. Desligado sem token; configuração inválida impede a subida.
- **Vínculo por código de uso único** gerado no app (link `t.me/<bot>?start=<código>`): o Telegram não tem senha do
  Bolso e o código prova que quem está na conversa é quem estava logado. Uma conversa por pessoa e vice-versa.
- **O bot roda como a pessoa** (contexto de segurança só durante a mensagem): os mesmos serviços, a mesma
  `WorkspaceAccess` e a mesma validação da API. Não há "caminho do bot" com regras próprias para escapar.
- **Revogação**: o vínculo guarda a versão dos tokens; sair de todos os aparelhos ou trocar a senha desliga o
  Telegram. Sair do espaço também.
- **Valor no chat só no formato brasileiro**: "10,555" é recusado (no extrato seria milhar em inglês; no chat é quase
  sempre erro de digitação e viraria um gasto mil vezes maior).
- **Lembrete diário** das contas fixas atrasadas, de hoje e de amanhã: rodada de hora em hora das 8h às 21h, no máximo
  uma mensagem por dia e só se houver algo; marcado depois do envio (se o Telegram falhar, tenta na próxima hora).

## ADR-014 — Contrato da API gerado do código (OpenAPI → TypeScript)

- O backend gera a especificação OpenAPI (springdoc) num teste e compara com `docs/openapi.json`, versionado. O front
  gera os tipos de `schema.d.ts` a partir desse arquivo; `types.ts` só dá nomes a eles. Uma mudança na API exige
  atualizar o contrato, e o `tsc` aponta cada uso no front que quebrou.
- **Respostas**: todo campo é obrigatório, salvo os marcados `@Schema(nullable = true)` — o front sabe exatamente o
  que pode faltar. **Requisições** seguem a validação (`@NotNull`…). Valores em dinheiro saem do front como texto
  (`Money<>`), nunca como número de ponto flutuante.
- Um teste impede dois records com o mesmo nome simples (o OpenAPI os juntaria sem aviso).
- O gerador fica em `frontend/tools/openapi`, isolado (exige TypeScript 5; o app usa o 7), com lockfile próprio.

## ADR-015 — A `main` só recebe o que passou por toda a rede de proteção

Proteção de branch exigindo os jobs Backend, Frontend, Migrações, Produção e CodeQL, sem push direto nem force push,
valendo para administradores. O job de produção sobe a pilha real (TLS, papéis do banco, nginx) — a versão anterior
primeiro, com dados, e a nova por cima —, então Dockerfile, nginx, permissões e migrações são testados antes do merge,
não no deploy.
