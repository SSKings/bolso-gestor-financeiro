-- Fase 4: regras de categorização, importação de extrato (sem duplicar) e Telegram.

-- "descrição contém X → categoria": texto simples (nunca regex), comparado sem acento e sem maiúsculas
CREATE TABLE categorization_rules (
    id            UUID PRIMARY KEY,
    workspace_id  UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    pattern       VARCHAR(60)  NOT NULL,
    normalized    VARCHAR(60)  NOT NULL,
    category_id   UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    FOREIGN KEY (category_id, workspace_id) REFERENCES categories (id, workspace_id) ON DELETE CASCADE,
    UNIQUE (workspace_id, normalized)
);

-- origem do lançamento e identidade da linha importada (FITID do OFX ou hash da linha do CSV)
ALTER TABLE transactions
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'IMPORT', 'TELEGRAM')),
    ADD COLUMN external_id VARCHAR(80) CHECK (external_id ~ '^[A-Za-z0-9._:-]+$');
-- a mesma linha do extrato não entra duas vezes na mesma conta, nem com duas importações simultâneas
CREATE UNIQUE INDEX ux_transactions_external ON transactions (account_id, external_id) WHERE external_id IS NOT NULL;

-- conversa do Telegram ligada a uma pessoa (uma conversa por pessoa e vice-versa), com o espaço e a conta padrão
CREATE TABLE telegram_links (
    id                UUID PRIMARY KEY,
    user_id           UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    chat_id           BIGINT      NOT NULL UNIQUE,
    workspace_id      UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    account_id        UUID        NOT NULL,
    token_version     INT         NOT NULL,  -- "sair de todos os dispositivos"/troca de senha também desliga o Telegram
    created_at        TIMESTAMPTZ NOT NULL,
    last_reminder_on  DATE,
    FOREIGN KEY (account_id, workspace_id) REFERENCES accounts (id, workspace_id) ON DELETE CASCADE
);

-- código de uso único para ligar a conversa: só o hash, 10 minutos
CREATE TABLE telegram_link_codes (
    id            UUID PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    account_id    UUID        NOT NULL,
    code_hash     VARCHAR(64) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    FOREIGN KEY (account_id, workspace_id) REFERENCES accounts (id, workspace_id) ON DELETE CASCADE
);
CREATE INDEX ix_telegram_codes_user ON telegram_link_codes (user_id);
