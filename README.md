# Bolso

<p>
  <img src="https://github.com/SSKings/bolso-gestor-financeiro/actions/workflows/ci.yml/badge.svg" alt="CI"/>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 4.1"/>
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black" alt="React 19"/>
</p>

Finanças pessoais **e compartilhadas**, feitas para usar no dia a dia, pelo celular. Registre um gasto em segundos,
divida as contas da casa com quem mora com você e saiba, a qualquer momento, quanto entrou, quanto saiu e para onde
foi o dinheiro no mês.

## O que já dá para fazer

- **Lançamento rápido**: despesa, receita ou transferência entre contas, com valor digitado como em app de banco
- **Espaços compartilhados**: um espaço "Pessoal" e outros para a casa, uma viagem ou um negócio. Convide alguém por
  um link; cada lançamento mostra quem lançou
- **Painel do mês**: entradas, saídas, resultado, gastos por categoria e saldo de cada conta
- **Contas** (corrente, poupança, dinheiro, investimento) com **saldo sempre calculado** a partir dos lançamentos
- **Categorias** prontas e editáveis; conta e categoria arquivadas saem das listas sem apagar o histórico
- **Cartão de crédito como na vida real**: compra à vista ou parcelada (até 48×) caindo nas faturas certas pelo dia de
  fechamento, fatura com vencimento e status, pagamento a partir de uma conta e limite disponível. O gasto conta no mês
  da fatura e o pagamento não é contado de novo
- **Contas fixas e assinaturas**: cada mês mostra o que está atrasado, vence em breve, pendente ou pago; você confirma
  com o valor real (a conta de luz muda) e não dá para confirmar duas vezes
- **Orçamento por categoria**: limite mensal com alerta em 80% e estouro acima de 100%, contando também o cartão
- **Previsão do mês**: quanto vai sobrar no fim do mês — saldo de hoje, mais o que vai entrar, menos contas fixas
  (inclusive atrasadas), lançamentos agendados e faturas que vencem no mês
- **Importar extrato** (OFX ou CSV do banco): prévia para revisar antes de gravar, categorias sugeridas e **nada
  duplicado** — reimportar o mesmo arquivo, ou um período que se sobrepõe, só traz o que é novo. Lançamento manual
  parecido (mesmo dia e valor) vem marcado para você decidir
- **Regras de categoria**: "descrição contém *uber* → Transporte", sem acento nem maiúscula; a mais específica vence
- **Telegram**: mande `32,50 almoço` e o gasto entra na hora, já categorizado; `/saldo`, `/contas`, `/desfazer`, e
  um lembrete por dia das contas fixas atrasadas ou vencendo
- **App instalável** no celular (PWA)

Próximas ideias: metas e Open Finance — ver [docs/roadmap.md](docs/roadmap.md).

## Segurança desde o começo

- O usuário vem **sempre do token**, nunca do corpo da requisição; toda rota de espaço confere a participação a cada
  chamada (quem é removido perde o acesso na hora). Quem não participa recebe 404 — não descobre nem que o espaço existe
- Isolamento entre espaços **também no banco**: chaves estrangeiras compostas impedem um lançamento de apontar para
  conta ou categoria de outro espaço, mesmo com um erro no código
- Senha com política e lista de senhas comuns, bloqueio progressivo por conta, limite por IP, tokens revogáveis
  (logout e troca de senha derrubam todas as sessões), convites de uso único guardados só como hash
- O cartão guarda **só os 4 últimos dígitos**: a API recusa o número completo e o banco não tem onde guardá-lo
- Extrato lido **sem parser XML** (sem XXE), com limite de tamanho e de linhas; regras de categoria são texto, não
  regex (sem ReDoS)
- O Telegram é ligado por código de uso único e o bot age **como a pessoa**, pelas mesmas checagens da API: sair do
  espaço ou de todos os aparelhos desliga a conversa na hora; o token do bot nunca aparece em log
- Produção com três usuários no PostgreSQL (a API só tem DML), containers sem root e somente leitura, nginx com TLS e
  CSP, logs com rotação; a API não sobe com configuração insegura

Detalhes, ameaças e o teste de cada controle: [docs/security.md](docs/security.md). Decisões: [docs/adr.md](docs/adr.md).

## Testes

| Parte | Comando | Testes | Cobertura (linhas / branches) |
|---|---|---|---|
| Backend | `cd backend && ./mvnw verify` | 190, contra **PostgreSQL de verdade** (Testcontainers) e um Telegram falso local | 99% / 94% |
| Frontend | `cd frontend && npm run coverage` | 99 (Vitest + Testing Library), com fixtures tipadas pelo contrato da API | 98% / 91% |
| Produção | `python3 deploy/e2e-smoke.py https://...` | 36 verificações ponta a ponta (nginx → API → PostgreSQL) | — |

Desenvolvido com TDD: os testes de cada fatia foram escritos antes do código. O CI **barra** o PR se a cobertura cair
(backend 97% / 92%, frontend 96% / 89%), se o teste de mutação das regras puras pegar menos de 92% dos defeitos, se o
contrato da API mudar sem o front acompanhar, se uma migração antiga for editada, se a pilha de produção não subir por
cima da versão anterior com dados, ou se as imagens tiverem vulnerabilidade grave corrigível — ver
[docs/security.md](docs/security.md#proteção-contra-regressões-ci). As proteções críticas passaram por teste de mutação
manual: cada defeito injetado (lançamento lido sem filtrar o espaço, papel de dono não checado, token sem versão,
convite reutilizável, último dono saindo, compra no dia do fechamento na fatura errada, pagamento contado como gasto,
número completo do cartão aceito, conta fixa confirmada duas vezes, previsão sem as contas atrasadas, extrato
importado duas vezes, código do Telegram reutilizável, Telegram valendo depois do logout…) foi pego por um teste.

## Rodando localmente

Pré-requisitos: Java 17, Node 22.22+, Docker.

```bash
cd backend && ./mvnw spring-boot:run      # sobe o PostgreSQL de dev sozinho (compose.yaml) e a API em :8080
cd frontend && npm ci && npm run dev      # app em http://localhost:5173 (abre também de outros aparelhos da rede)
```

Documentação interativa da API (só em dev): http://localhost:8080/swagger-ui.html

**Mudou a API?** O teste de contrato vai falhar até você atualizar os dois lados:

```bash
cd backend && CONTRACT_UPDATE=1 ./mvnw test -Dtest=ApiContractTest   # regrava docs/openapi.json
cd ../frontend && npm run contract && npx tsc -b                     # regera os tipos; o tsc aponta o que quebrou
```

Banco: nunca edite uma migração existente — crie a próxima (`V<n+1>__descricao.sql`).

## Produção

```bash
cd deploy
cp .env.prod.example .env.prod      # preencha (openssl rand ... para as senhas e o JWT_SECRET)
mkdir certs && cp /caminho/fullchain.pem /caminho/privkey.pem certs/
sudo chown 101 certs/privkey.pem && chmod 600 certs/privkey.pem   # o nginx roda sem root (uid 101) e precisa ler a chave
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Sobe PostgreSQL (rede interna, sem porta exposta), API (perfil `prod`) e nginx (TLS, front-end e proxy de `/api`).
Telegram (opcional): crie o bot no @BotFather e preencha `TELEGRAM_ENABLED=true`, `TELEGRAM_BOT_TOKEN` e
`TELEGRAM_BOT_USERNAME` no `.env.prod` — não precisa abrir porta (long polling).
Depois de subir, `python3 deploy/e2e-smoke.py https://seu-dominio --verify-tls` confere a pilha de ponta a ponta (cria
dados de teste: rode num ambiente de homologação).

## Stack

**Backend:** Java 17 · Spring Boot 4.1 (Spring 7, Security 7, Jackson 3, Hibernate 7) · JWT · Spring Data JPA · Flyway · PostgreSQL ·
Testcontainers · JaCoCo
**Frontend:** React 19 · TypeScript · Vite · Tailwind CSS · React Router · Vitest · Testing Library
**Infra:** Docker · nginx · GitHub Actions · Dependabot

## Estrutura

```
backend/    API: auth, workspace (espaços e convites), account, category, transaction, card, recurrence, budget,
            forecast, dashboard, rule (categorização), importing (extrato), telegram
frontend/   SPA/PWA em React
deploy/     produção: compose, nginx, papéis do banco
docs/       segurança, decisões e roadmap
```

---

Este repositório começou como "API Gestor Financeiro", um CRUD de contas e lançamentos; o histórico continua no git.
O Bolso é a reescrita com foco em uso real, finanças compartilhadas e segurança.
