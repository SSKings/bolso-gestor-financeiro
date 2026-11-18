# Roadmap

Cada fase termina com o app utilizável, testado (TDD) e revisado em segurança.

| Fase | Conteúdo | Situação |
|---|---|---|
| 0 — Fundação | Spring Boot 3.5, Flyway, PostgreSQL + Testcontainers, segurança (JWT revogável, senha, bloqueio, limite por IP), CI, Docker, front PWA | ✅ |
| 1 — Uso diário | Espaços compartilhados com convite, contas, categorias, lançamentos (receita, despesa, transferência), saldo calculado, painel do mês | ✅ |
| 2 — Cartão de crédito | Cartão com fechamento e vencimento, compra parcelada nas faturas certas, fatura, pagamento a partir da conta, limite disponível | ✅ |
| 3 — Recorrência e orçamento | Contas fixas e assinaturas (confirmação mensal com o valor real, lembrete no painel), orçamento por categoria com alerta, previsão de saldo do mês | ✅ |
| 4 — Menos digitação | Importar extrato OFX/CSV sem duplicar, regras de categorização automática, lançamento e alertas pelo Telegram (inclusive o lembrete de contas fixas fora do app) | próxima |
| 5 — Opcional | Metas; Open Finance via agregador (Pluggy/Belvo) | |
