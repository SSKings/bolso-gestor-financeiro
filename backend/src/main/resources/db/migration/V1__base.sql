-- Bolso — esquema inicial: usuários, espaços compartilhados, contas, categorias e lançamentos.
--
-- Isolamento entre espaços também no BANCO, não só no código: conta, categoria e lançamento carregam o workspace_id,
-- e as chaves estrangeiras são compostas (id, workspace_id). Um lançamento do espaço A não consegue apontar para uma
-- conta ou categoria do espaço B nem se o código tiver um erro.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    token_version   INT          NOT NULL DEFAULT 0,      -- muda no logout e na troca de senha: tokens antigos param
    failed_logins   INT          NOT NULL DEFAULT 0,
    lock_count      INT          NOT NULL DEFAULT 0,      -- bloqueios seguidos (o tempo dobra a cada um)
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE workspaces (
    id          UUID PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE memberships (
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role          VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX ix_memberships_user ON memberships (user_id);

-- Convite para o espaço: só o hash SHA-256 do código fica no banco; vale uma vez, até expirar.
CREATE TABLE invites (
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    created_by   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    used_by      UUID        REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX ix_invites_workspace ON invites (workspace_id);

CREATE TABLE accounts (
    id               UUID PRIMARY KEY,
    workspace_id     UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name             VARCHAR(60)   NOT NULL,
    type             VARCHAR(20)   NOT NULL CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH', 'INVESTMENT')),
    initial_balance  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    archived         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL,
    UNIQUE (id, workspace_id)
);
CREATE UNIQUE INDEX ux_accounts_name ON accounts (workspace_id, lower(name));

CREATE TABLE categories (
    id            UUID PRIMARY KEY,
    workspace_id  UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name          VARCHAR(40) NOT NULL,
    kind          VARCHAR(10) NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    color         VARCHAR(7)  NOT NULL CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    archived      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (id, workspace_id)
);
CREATE UNIQUE INDEX ux_categories_name ON categories (workspace_id, kind, lower(name));

CREATE TABLE transactions (
    id             UUID PRIMARY KEY,
    workspace_id   UUID          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    type           VARCHAR(10)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount         NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    occurred_on    DATE          NOT NULL,
    description    VARCHAR(140)  NOT NULL,
    account_id     UUID          NOT NULL,
    to_account_id  UUID,
    category_id    UUID,
    created_by     UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    FOREIGN KEY (account_id, workspace_id)    REFERENCES accounts (id, workspace_id),
    FOREIGN KEY (to_account_id, workspace_id) REFERENCES accounts (id, workspace_id),
    FOREIGN KEY (category_id, workspace_id)   REFERENCES categories (id, workspace_id),
    -- transferência: conta de destino diferente da origem e sem categoria; receita/despesa: categoria e sem destino
    CHECK ((type = 'TRANSFER' AND to_account_id IS NOT NULL AND to_account_id <> account_id AND category_id IS NULL)
        OR (type <> 'TRANSFER' AND to_account_id IS NULL AND category_id IS NOT NULL))
);
CREATE INDEX ix_transactions_workspace_date ON transactions (workspace_id, occurred_on DESC);
CREATE INDEX ix_transactions_account ON transactions (account_id);
CREATE INDEX ix_transactions_to_account ON transactions (to_account_id);
CREATE INDEX ix_transactions_category ON transactions (category_id);
