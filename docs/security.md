# Segurança do Bolso

O Bolso guarda dados financeiros de várias pessoas, às vezes compartilhados. A ameaça principal é **uma pessoa ver
ou alterar dados de outra** — exatamente o defeito do projeto anterior (o id do usuário vinha no corpo da requisição).
Este documento lista cada controle e o teste que o comprova. Muitos vêm das seis revisões de segurança do
[ShopEasy](https://github.com/SSKings/shopeasy/blob/main/docs/security-review.md).

## Controles e testes

| Ameaça | Controle | Teste |
|---|---|---|
| Ler/alterar dados de outro espaço | Usuário só do token; `WorkspaceAccess` confere a participação a cada chamada; repositórios só buscam com `workspaceId` | `TenantIsolationTest` (todas as rotas, pelo espaço alheio e pelo próprio com ids alheios) |
| Erro no código vazar entre espaços | Chaves estrangeiras compostas `(id, workspace_id)` no banco | `TransactionIntegrationTest.crossWorkspaceReferences` |
| Descobrir que um espaço existe | Não membro recebe 404 com a mesma mensagem | `WorkspaceIntegrationTest.nonMemberGets404` |
| Removido continuar acessando | Participação lida do banco a cada requisição (não do token) | `membersAndRemoval` |
| Membro agir como dono | Ações de administração exigem papel OWNER (403) | `memberCannotAdminister` |
| Espaço ficar sem dono | Último dono não sai, não se remove nem se rebaixa; membros travados na checagem | `leaveRules` |
| Convite vazado/reutilizado | Código de 256 bits, só o SHA-256 no banco, uso único (UPDATE condicional), 72 h, revogável; link com o código no **fragmento** (`#codigo=`), que não chega ao servidor nem aos logs | `inviteFlow`, `inviteSecurity`, `listAndRevokeInvites`, `workspace.test.tsx` |
| Campo extra no JSON (mass assignment) | `fail-on-unknown-properties`: `workspaceId`, `createdBy`, `role`… são recusados | `unknownFieldsRejected`, `AccountIntegrationTest.validation`, `TransactionIntegrationTest.validation` |
| Força bruta no login | Bloqueio por conta (5 falhas → 15 min, dobrando até 24 h), linha travada; limite por IP (20/min na API, 10/min no nginx; IPv6 por /64; caminho normalizado) | `lockoutAfterFailures`, `lockoutIsProgressive`, `authRateLimitPerIp`, `RateLimitFilterTest` |
| Descobrir quem tem conta pelo login | Mesma resposta e mesmo custo de BCrypt para e-mail inexistente | `loginDoesNotRevealAccounts` |
| Senha fraca | 8–72 caracteres com letra e número, lista de senhas comuns, sem nome/e-mail/"bolso" | `registerValidation`, `PasswordStrengthTest` |
| Token roubado/antigo | JWT HS256 com emissor, expiração (12 h) e versão; logout e troca de senha revogam todos | `tokenLifecycle`, `changePassword`, `forgedTokens` (alg none, outro segredo, outro emissor, sem versão, usuário inexistente) |
| Dados inválidos/maliciosos | Valor > 0 com 2 casas e teto, data 2000 → +1 ano, texto sem caracteres de controle, cor `#rrggbb`, corpo ≤ 64 KB | `validation` (contas, categorias, lançamentos), `requestSizeLimit` |
| Busca virar curinga | `%` e `_` escapados no LIKE | `listAndFilter` |
| Saldo divergir | Saldo nunca é guardado: calculado dos lançamentos | `balances`, `concurrentCreates` (20 lançamentos simultâneos, saldo exato) |
| Vazamento por erro | Respostas de erro padronizadas, sem stack trace/SQL/classe | `errorsDoNotLeak` |
| XSS roubar o token (localStorage) | Front nunca injeta HTML; nginx com CSP `script-src 'self'`, `connect-src 'self'` | revisão de código; E2E de produção confere o CSP |
| Clickjacking, sniffing, referrer | `frame-ancestors 'none'`/`X-Frame-Options DENY`, `nosniff`, `no-referrer` | `securityHeaders`, E2E de produção |
| CORS aberto | Só as origens configuradas; em produção só https e sem curinga | `cors`, `ProductionConfigValidatorTest` |
| Configuração insegura em produção | A API não sobe com segredo curto/de exemplo, CORS inválido, banco não PostgreSQL ou usuário do banco igual ao dono | `ProductionConfigValidatorTest` |
| Falha de SQL virar controle do banco | Três usuários: superusuário só cria; dono só migra; API só DML (sem CREATE/DROP/COPY PROGRAM, sem acesso ao histórico do Flyway) | conferido na pilha de produção (abaixo) |
| Número de cartão vazado | Só os 4 últimos dígitos: a API recusa o número completo (e não há campo para ele); a coluna do banco tem 4 caracteres com `CHECK` de dígitos | `CardIntegrationTest.onlyLast4`; esquema conferido na pilha de produção |
| Cartão, compra ou fatura de outro espaço | Mesma guarda e chaves compostas das contas; compra só é encontrada pelo cartão **e** pelo espaço da rota | `CardIntegrationTest.isolation` |
| Pagamento de fatura burlando as regras | Só pela rota da fatura; a rota genérica de lançamentos recusa criar e editar `CARD_PAYMENT` | `cardPaymentOnlyThroughStatement` |
| Conta fixa, orçamento ou previsão de outro espaço | Mesma guarda e chaves compostas; a confirmação só usa conta do espaço | `RecurrenceIntegrationTest.isolation`, `BudgetIntegrationTest.crud`, `ForecastIntegrationTest.emptyAndIsolation` |
| Conta fixa paga duas vezes (clique duplo) | Checagem no serviço + índice único (conta fixa, mês) no banco | `confirm`, `concurrentConfirm` (8 confirmações simultâneas: exatamente uma vale) |
| Extrato malicioso (XXE, arquivo gigante, CSV com fórmula/controle) | OFX lido **sem parser XML** (entidades viram texto); até 1 MB (nginx e API) e 2.000 linhas; descrição sem caracteres de controle, até 140; valor com teto e 2 casas | `StatementParserTest` (`ofxXxe`, `invalid`, `descriptionSanitized`, `amountsRejected`), `fileLimits`, E2E de produção |
| Limite de 1 MB abrir a API toda | Limite maior **só** nas rotas de importação (filtro da API e `location` do nginx); o resto continua em 64 KB | `fileLimits`, E2E de produção |
| Extrato importado duas vezes | Identidade da linha (FITID, Identificador do Nubank ou hash de data+valor+descrição+ordem) com índice único (conta, identidade) no banco; o lote é validado inteiro antes de gravar | `ofxFlow`, `latin1Semicolon`, `commitValidation` |
| Importar em conta ou categoria de outro espaço | Mesma guarda; conta e categorias buscadas pelo espaço da rota | `ImportIntegrationTest.isolation` |
| Regra de categoria virar ReDoS | Regra é **texto literal** (`contains`), nunca regex; 2–60 caracteres | `RuleIntegrationTest.validation` (`(a+)+$` é só texto) |
| Alguém ligar o próprio Telegram à conta de outra pessoa | Código de uso único gerado logado: 256 bits, 10 min, só o SHA-256 no banco, linha travada ao consumir; um novo invalida os anteriores; participação e conta reconferidas no consumo | `codeRules`, `codeAccess`, `stateChangesBeforeConsume` |
| Telegram continuar valendo depois de "sair" ou de deixar o espaço | O vínculo guarda a versão dos tokens: logout/troca de senha o derrubam; a participação é conferida **a cada mensagem**, e o bot age como a pessoa (mesma `WorkspaceAccess` e validação da API) | `logoutRevokes`, `leavingWorkspaceRevokes` |
| Bot em grupo expor dados | Só conversa privada; em grupo recusa tudo (nem consome o código) | `groupsAndUnlinked` |
| Injeção de HTML nas respostas do bot | Todo texto vindo de pessoas (descrição, nomes) escapado | `launching`, `queriesAndUndo` |
| Spam no bot | 20 mensagens/min por conversa (memória limitada a 10 mil conversas); mensagens de bots e sem texto ignoradas | `TelegramTransportTest.rateLimit`, `botFilters` |
| Token do bot vazar | Fora de log, de mensagem de erro e do `toString`; configuração inválida impede a API de subir sem mostrar o token; long polling só de saída (sem webhook, nenhuma porta aberta) | `clientRedactsToken`, `configStartup`, `properties` |
| Dados financeiros no log | O texto das mensagens do Telegram nunca é logado | revisão de código |
| Link do bot virar `javascript:` | O front só transforma em link um `https://t.me/<bot>?start=<código>` exato | `automation.test.tsx` |
| Usuário padrão do Spring com senha no log | `UserDetailsServiceAutoConfiguration` desligada: não existe outro login além do Bolso | `noDefaultUser` |
| Ferramentas expostas | Actuator só `health` sem detalhes (e bloqueado no nginx); Swagger só em dev | `actuator`, `docsDisabledOutsideDev` |
| Disco cheio de log | Rotação 5 × 20 MB por container; logs em JSON | `docker-compose.prod.yml` |

## Verificação além dos testes

- **Teste de mutação manual** das proteções críticas, um defeito por vez, em build limpo:
  - fase 1: 9 defeitos, 8 pegos. O que sobreviveu (tirar o filtro de espaço da consulta de saldo) é uma mutação
    **equivalente**: as chaves compostas já impedem um lançamento de apontar para conta de outro espaço;
  - fase 2 (cartão): 10 defeitos, todos pegos — depois de fortalecer um teste que passava pelo motivo errado (a rota
    genérica recusava o pagamento de fatura por falta de categoria, não pela regra do pagamento);
  - fase 3 (contas fixas, orçamento, previsão): 12 defeitos, todos pegos;
  - fase 4 (importação, regras, Telegram): 16 defeitos, todos pegos — linha repetida importada, categoria ou conta de
    outro espaço, prévia sem checar membro, limite de 1 MB em todas as rotas, regra menos específica vencendo, código
    do Telegram reutilizável, sem validade ou não invalidado pelo novo, vínculo sobrevivendo ao logout ou à saída do
    espaço, bot aceitando grupo, HTML sem escapar, `/desfazer` apagando lançamento que não veio do Telegram.
- **Pilha de produção subida e testada** (nginx TLS → API → PostgreSQL) com `deploy/e2e-smoke.py`: espaço
  compartilhado, saldos, cartão parcelado, pagamento de fatura, intrusa recebendo 404/400, cabeçalhos de segurança,
  actuator e OpenAPI fora do ar, logout revogando o token; contas fixas com confirmação única, previsão e
  orçamento; importação de OFX em Latin-1 pelo nginx com regra aplicada, reimportação sem duplicar, 413 acima de 1 MB
  (e 64 KB no resto da API), intrusa recebendo 404 na importação, Telegram desligado sem token — 36/36. As migrações
  V2, V3 e V4 também foram aplicadas sobre um banco existente (o de desenvolvimento), sem perder dados. Com
  `TELEGRAM_ENABLED=true` e token inválido, a API não sobe, e o token não aparece no log. No banco, o usuário da API recebeu "permission denied" para `CREATE TABLE`, `DROP`,
  `ALTER`, `flyway_schema_history` e `COPY ... TO PROGRAM`.

## Proteção contra regressões (CI)

Todo PR para a `main` só entra com tudo verde (proteção de branch, valendo também para administradores):

| Etapa | O que barra |
|---|---|
| Backend | 190 testes contra PostgreSQL real; cobertura mínima de 97% das linhas e 92% dos ramos |
| Mutação (PIT) | Regras puras (faturas, extrato, senha, limite por IP, validador de produção) com no mínimo 92% dos defeitos injetados pegos (hoje 94,9%; os que sobram são equivalentes) |
| Contrato da API | A especificação gerada do código precisa ser igual a `docs/openapi.json`; o front gera os tipos dela, então campo renomeado ou removido vira erro de compilação lá |
| Frontend | 99 testes com as fixtures tipadas pelo contrato; cobertura mínima de 96% / 89%; `npm audit`; lint |
| Migrações | Migração que já está na `main` não pode ser editada, renomeada ou apagada |
| Produção | Sobe a versão da `main`, cria dados, sobe a do PR por cima (migrações sobre dados reais) e roda o E2E de produção (36 verificações) |
| Imagens | Trivy barra vulnerabilidade HIGH/CRITICAL corrigível nas imagens da API e do nginx; exceções só em `.trivyignore.yaml`, com justificativa e validade |
| CodeQL | Análise estática (Java e TypeScript) em todo PR e toda semana |

Dependabot: atualizações semanais e alertas e correções automáticas de vulnerabilidade ligados.

**Achados ao montar essa rede** (corrigidos, com teste que falha sem a correção):
- **Senha disfarçada passando**: `99s3nh499` era aceita — o "4" final do disfarce sumia junto com os dígitos.
- **Valor com sinal duplicado**: `-12,50-` no extrato virava **+12,50** (os sinais se anulavam); agora é recusado.
- **Mesmo IPv4 com dois limites**: `::ffff:1.2.3.4` e `1.2.3.4` contavam separados no limite por IP.
- **Limite de corpo sem `Content-Length`** (chunked) funcionava, mas nenhum teste garantia: agora há um com Tomcat real.
- **Contrato**: os tipos do front diziam que os valores vinham como texto (vêm como número) e não tinham o campo
  `recurrence` do lançamento; o painel simulado nos testes não tinha `cards`.
- **Imagem do nginx** com 13 vulnerabilidades do Alpine (1 crítica): a imagem oficial ainda não tinha as correções;
  o build aplica as atualizações de segurança e remove o `curl`, que não é usado.

## Atualização para Spring Boot 4 (resolvido)

O Spring Boot 3.5 / Spring 6.2 saiu do suporte gratuito com duas falhas críticas sem correção na linha 6.2
(CVE-2026-47884 e CVE-2026-47890 — não exploráveis aqui, mas sem saída). A migração para o Spring Boot 4.1 as
resolve e zera as exceções do scan. Ela mesma revelou três mudanças de comportamento, pegas pelos testes ou pelo
verificador de propriedades:
- **Limite de corpo sem `Content-Length`**: continuava cortando a leitura, mas respondia 400 em vez de 413 — o Jackson 3
  embrulha a exceção em mais camadas; o tratamento agora percorre toda a cadeia de causas (`ChunkedBodyLimitTest`).
- **`/actuator/health` público passou a listar grupos** (sondas do Kubernetes, ligadas por padrão no Boot 4):
  desligadas, a resposta volta a ser só `{"status":"UP"}` (`actuator`).
- **`server.error.include-message`/`include-stacktrace` deixaram de existir** e passariam a ser ignoradas: movidas para
  `spring.web.error.*` (o padrão já era não mostrar, mas a regra fica explícita).

## Riscos aceitos

- **Token no localStorage**: o app abre logado no celular. A mitigação é não haver XSS (React escapa tudo, sem
  `dangerouslySetInnerHTML`) e o CSP restritivo. Alternativa futura: refresh token em cookie `HttpOnly`.
- **Cadastro revela se o e-mail existe** ("Email already in use"). Resolver exige confirmação de e-mail em duas
  etapas — fica para quando houver envio de e-mail.
- **Bloqueio de conta permite travar a conta de outra pessoa por alguns minutos** (equilíbrio usual; o limite por IP
  reduz o abuso).
- **Limite por IP em memória**: vale por instância; com várias réplicas, o nginx continua limitando.
