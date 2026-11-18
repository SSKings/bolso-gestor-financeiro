-- Fase 3: contas fixas/assinaturas (com confirmação mensal), orçamento por categoria.

CREATE TABLE recurrences (
    id            UUID PRIMARY KEY,
    workspace_id  UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    type          VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    description   VARCHAR(140)  NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    day_of_month  INT           NOT NULL CHECK (day_of_month BETWEEN 1 AND 28),
    category_id   UUID          NOT NULL,
    account_id    UUID          NOT NULL,
    start_month   DATE          NOT NULL CHECK (EXTRACT(DAY FROM start_month) = 1),
    end_month     DATE          CHECK (end_month IS NULL OR (EXTRACT(DAY FROM end_month) = 1 AND end_month >= start_month)),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id),
    FOREIGN KEY (account_id, workspace_id)  REFERENCES accounts (id, workspace_id),
    UNIQUE (id, workspace_id)
);
CREATE INDEX ix_recurrences_workspace ON recurrences (workspace_id);

-- Lançamento que confirma uma conta fixa num mês. Excluir a conta fixa mantém o lançamento (só tira o vínculo).
ALTER TABLE transactions
    ADD COLUMN recurrence_id UUID,
    ADD COLUMN recurrence_month DATE,
    ADD CONSTRAINT transactions_recurrence_fk FOREIGN KEY (recurrence_id, workspace_id)
        REFERENCES recurrences (id, workspace_id) ON DELETE SET NULL (recurrence_id),
    ADD CONSTRAINT transactions_recurrence_check CHECK (recurrence_id IS NULL
        OR (recurrence_month IS NOT NULL AND EXTRACT(DAY FROM recurrence_month) = 1 AND type IN ('INCOME', 'EXPENSE')));
-- uma confirmação por conta fixa por mês, mesmo com cliques simultâneos
CREATE UNIQUE INDEX ux_transactions_recurrence ON transactions (recurrence_id, recurrence_month) WHERE recurrence_id IS NOT NULL;

CREATE TABLE budgets (
    id            UUID PRIMARY KEY,
    workspace_id  UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    category_id   UUID          NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id) ON DELETE CASCADE,
    UNIQUE (workspace_id, category_id)
);
