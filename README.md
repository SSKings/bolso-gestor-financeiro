# Bolso

<p>
  <img src="https://github.com/SSKings/bolso-gestor-financeiro/actions/workflows/ci.yml/badge.svg" alt="CI"/>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white" alt="Java 17"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.5"/>
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
- **App instalável** no celular (PWA)

Próximas fases: contas fixas e orçamento por categoria, importação de extrato (OFX/CSV) e lançamento pelo Telegram —
ver [docs/roadmap.md](docs/roadmap.md).

## Segurança desde o começo

- O usuário vem **sempre do token**, nunca do corpo da requisição; toda rota de espaço confere a participação a cada
  chamada (quem é removido perde o acesso na hora). Quem não participa recebe 404 — não descobre nem que o espaço existe
- Isolamento entre espaços **também no banco**: chaves estrangeiras compostas impedem um lançamento de apontar para
  conta ou categoria de outro espaço, mesmo com um erro no código
- Senha com política e lista de senhas comuns, bloqueio progressivo por conta, limite por IP, tokens revogáveis
  (logout e troca de senha derrubam todas as sessões), convites de uso único guardados só como hash
- O cartão guarda **só os 4 últimos dígitos**: a API recusa o número completo e o banco não tem onde guardá-lo
- Produção com três usuários no PostgreSQL (a API só tem DML), containers sem root e somente leitura, nginx com TLS e
  CSP, logs com rotação; a API não sobe com configuração insegura

Detalhes, ameaças e o teste de cada controle: [docs/security.md](docs/security.md). Decisões: [docs/adr.md](docs/adr.md).

## Testes

| Parte | Comando | Testes | Cobertura (linhas / branches) |
|---|---|---|---|
| Backend | `cd backend && ./mvnw verify` | 102, contra **PostgreSQL de verdade** (Testcontainers) | 98% / 91% |
| Frontend | `cd frontend && npm run coverage` | 66 (Vitest + Testing Library) | 97% / 90% |
| Produção | `python3 deploy/e2e-smoke.py https://...` | 19 verificações ponta a ponta (nginx → API → PostgreSQL) | — |

Desenvolvido com TDD: os testes de cada fatia foram escritos antes do código. O build **falha** se a cobertura cair
(mínimo de 90% das linhas e 85% dos branches nas duas partes). As proteções críticas passaram por teste de mutação
manual: cada defeito injetado (lançamento lido sem filtrar o espaço, papel de dono não checado, token sem versão,
convite reutilizável, último dono saindo, compra no dia do fechamento na fatura errada, pagamento contado como gasto,
número completo do cartão aceito…) foi pego por um teste.

## Rodando localmente

Pré-requisitos: Java 17, Node 22.22+, Docker.

```bash
cd backend && ./mvnw spring-boot:run      # sobe o PostgreSQL de dev sozinho (compose.yaml) e a API em :8080
cd frontend && npm ci && npm run dev      # app em http://localhost:5173 (abre também de outros aparelhos da rede)
```

Documentação interativa da API (só em dev): http://localhost:8080/swagger-ui.html

## Produção

```bash
cd deploy
cp .env.prod.example .env.prod      # preencha (openssl rand ... para as senhas e o JWT_SECRET)
mkdir certs && cp /caminho/fullchain.pem /caminho/privkey.pem certs/
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Sobe PostgreSQL (rede interna, sem porta exposta), API (perfil `prod`) e nginx (TLS, front-end e proxy de `/api`).
Depois de subir, `python3 deploy/e2e-smoke.py https://seu-dominio --verify-tls` confere a pilha de ponta a ponta (cria
dados de teste: rode num ambiente de homologação).

## Stack

**Backend:** Java 17 · Spring Boot 3.5 · Spring Security · JWT · Spring Data JPA · Flyway · PostgreSQL ·
Testcontainers · JaCoCo
**Frontend:** React 19 · TypeScript · Vite · Tailwind CSS · React Router · Vitest · Testing Library
**Infra:** Docker · nginx · GitHub Actions · Dependabot

## Estrutura

```
backend/    API: auth, workspace (espaços e convites), account, category, transaction, card, dashboard
frontend/   SPA/PWA em React
deploy/     produção: compose, nginx, papéis do banco
docs/       segurança, decisões e roadmap
```

---

Este repositório começou como "API Gestor Financeiro", um CRUD de contas e lançamentos; o histórico continua no git.
O Bolso é a reescrita com foco em uso real, finanças compartilhadas e segurança.
