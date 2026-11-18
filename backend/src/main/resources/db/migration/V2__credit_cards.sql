-- Fase 2: cartão de crédito com fatura, compras parceladas e pagamento da fatura a partir de uma conta.
-- Segurança: o cartão guarda só os 4 últimos dígitos (o projeto antigo guardava o número inteiro). Isolamento entre
-- espaços também aqui: chaves compostas (id, workspace_id) em compra, parcela e pagamento.

CREATE TABLE credit_cards (
    id            UUID PRIMARY KEY,
    workspace_id  UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name          VARCHAR(60)   NOT NULL,
    last4         VARCHAR(4)    CHECK (last4 ~ '^[0-9]{4}$'),
    closing_day   INT           NOT NULL CHECK (closing_day BETWEEN 1 AND 28),
    due_day       INT           NOT NULL CHECK (due_day BETWEEN 1 AND 28),
    credit_limit  NUMERIC(14, 2) NOT NULL CHECK (credit_limit >= 0),
    archived      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ   NOT NULL,
    UNIQUE (id, workspace_id)
);
CREATE UNIQUE INDEX ux_cards_name ON credit_cards (workspace_id, lower(name));

CREATE TABLE card_purchases (
    id            UUID PRIMARY KEY,
    workspace_id  UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    card_id       UUID          NOT NULL,
    category_id   UUID          NOT NULL,
    description   VARCHAR(140)  NOT NULL,
    amount        NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    installments  INT           NOT NULL CHECK (installments BETWEEN 1 AND 48),
    purchased_on  DATE          NOT NULL,
    created_by    UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    FOREIGN KEY (card_id, workspace_id)     REFERENCES credit_cards (id, workspace_id),
    FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id),
    UNIQUE (id, workspace_id)
);
CREATE INDEX ix_purchases_card ON card_purchases (card_id);
CREATE INDEX ix_purchases_category ON card_purchases (category_id);

CREATE TABLE card_installments (
    id               UUID PRIMARY KEY,
    workspace_id     UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    purchase_id      UUID          NOT NULL,
    card_id          UUID          NOT NULL,
    number           INT           NOT NULL CHECK (number >= 1),
    amount           NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    statement_month  DATE          NOT NULL CHECK (EXTRACT(DAY FROM statement_month) = 1),
    FOREIGN KEY (purchase_id, workspace_id) REFERENCES card_purchases (id, workspace_id) ON DELETE CASCADE,
    FOREIGN KEY (card_id, workspace_id)     REFERENCES credit_cards (id, workspace_id),
    UNIQUE (purchase_id, number)
);
CREATE INDEX ix_installments_card_month ON card_installments (card_id, statement_month);
CREATE INDEX ix_installments_ws_month ON card_installments (workspace_id, statement_month);

-- Pagamento de fatura: lançamento CARD_PAYMENT (sai da conta, não é gasto: o gasto está nas parcelas)
ALTER TABLE transactions
    ADD COLUMN card_id UUID,
    ADD COLUMN statement_month DATE,
    ADD FOREIGN KEY (card_id, workspace_id) REFERENCES credit_cards (id, workspace_id);
ALTER TABLE transactions ALTER COLUMN type TYPE VARCHAR(20);   -- 'CARD_PAYMENT' tem 12 caracteres
ALTER TABLE transactions DROP CONSTRAINT transactions_type_check;
ALTER TABLE transactions DROP CONSTRAINT transactions_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_type_check CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER', 'CARD_PAYMENT')),
    ADD CONSTRAINT transactions_shape_check CHECK (
        (type = 'TRANSFER' AND to_account_id IS NOT NULL AND to_account_id <> account_id
            AND category_id IS NULL AND card_id IS NULL AND statement_month IS NULL)
     OR (type IN ('INCOME', 'EXPENSE') AND to_account_id IS NULL AND category_id IS NOT NULL
            AND card_id IS NULL AND statement_month IS NULL)
     OR (type = 'CARD_PAYMENT' AND to_account_id IS NULL AND category_id IS NULL
            AND card_id IS NOT NULL AND statement_month IS NOT NULL AND EXTRACT(DAY FROM statement_month) = 1));
CREATE INDEX ix_transactions_card_month ON transactions (card_id, statement_month);
