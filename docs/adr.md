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

