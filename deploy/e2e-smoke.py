#!/usr/bin/env python3
"""Teste ponta a ponta da pilha de produção do Bolso (nginx TLS → API → PostgreSQL), só com a biblioteca padrão.

Cria três pessoas (Ana, Bruno e uma intrusa), usa espaço compartilhado, lançamentos e cartão de crédito, e confere
regras de dinheiro, isolamento e cabeçalhos de segurança. Os dados ficam no banco (use num ambiente de teste).

    python3 deploy/e2e-smoke.py https://localhost:18443           # certificado autoassinado: não confere o TLS
    python3 deploy/e2e-smoke.py https://bolso.seudominio.com.br --verify-tls

O nginx limita as rotas de login/cadastro a 10/min por IP: rodar duas vezes seguidas pode dar 429; espere 1 minuto.
"""
import datetime
import json
import ssl
import sys
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "https://localhost:18443"
CTX = ssl.create_default_context() if "--verify-tls" in sys.argv else ssl._create_unverified_context()
PASSWORD = "Vagalume4821"
results = []


def call(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=20) as r:
            status, raw, hdrs = r.status, r.read(), dict(r.headers)
    except urllib.error.HTTPError as e:
        status, raw, hdrs = e.code, e.read(), dict(e.headers)
    try:
        data = json.loads(raw) if raw else None
    except ValueError:
        data = raw.decode(errors="replace")
    return status, data, hdrs


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("OK    " if cond else "FALHA ") + name + (f"  [{detail}]" if detail and not cond else ""))


def register(name):
    s, b, _ = call("POST", "/api/auth/register",
                   body={"name": name, "email": f"e2e-{uuid.uuid4().hex[:10]}@teste.com", "password": PASSWORD})
    if s != 201:
        sys.exit(f"cadastro falhou ({s}): {b} — se for 429, espere 1 minuto")
    return b["token"], call("GET", "/api/me", b["token"])[1]


def main():
    today = datetime.date.today().isoformat()
    ana, me_a = register("Ana Souza")
    ws = me_a["workspaces"][0]["id"]
    bruno, _ = register("Bruno Lima")
    eve, me_e = register("Eve Intrusa")

    # ---- espaço compartilhado ----
    s, inv, _ = call("POST", f"/api/workspaces/{ws}/invites", ana)
    check("dono gera convite", s == 201 and len(inv["code"]) == 43)
    s, b, _ = call("POST", f"/api/invites/{inv['code']}/accept", bruno)
    check("Bruno aceita e entra no espaço da Ana", s == 200 and b["workspaceId"] == ws)

    accounts = call("GET", f"/api/workspaces/{ws}/accounts", bruno)[1]
    cats = call("GET", f"/api/workspaces/{ws}/categories", bruno)[1]
    wallet = accounts[0]["id"]
    food = next(c["id"] for c in cats if c["name"] == "Alimentação")
    salary = next(c["id"] for c in cats if c["name"] == "Salário")
    s, chk, _ = call("POST", f"/api/workspaces/{ws}/accounts", ana, {"name": "Nubank", "type": "CHECKING", "initialBalance": "1000.00"})

    # ---- lançamentos ----
    codes = (
        call("POST", f"/api/workspaces/{ws}/transactions", bruno, {"type": "EXPENSE", "amount": "35.90", "date": today,
             "description": "Almoço", "accountId": chk["id"], "categoryId": food})[0],
        call("POST", f"/api/workspaces/{ws}/transactions", ana, {"type": "INCOME", "amount": "3500.00", "date": today,
             "description": "Salário", "accountId": chk["id"], "categoryId": salary})[0],
        call("POST", f"/api/workspaces/{ws}/transactions", ana, {"type": "TRANSFER", "amount": "200.00", "date": today,
             "description": "Saque", "accountId": chk["id"], "toAccountId": wallet})[0])
    check("os dois lançam no espaço compartilhado", codes == (201, 201, 201), codes)
    bal = {a["name"]: float(a["balance"]) for a in call("GET", f"/api/workspaces/{ws}/accounts", ana)[1]}
    check("saldos calculados (Nubank 4264,10; Carteira 200)", bal["Nubank"] == 4264.10 and bal["Carteira"] == 200.0, bal)
    items = call("GET", f"/api/workspaces/{ws}/transactions", ana)[1]["items"]
    check("lista mostra quem lançou", {i["description"]: i["createdBy"]["name"] for i in items}.get("Almoço") == "Bruno Lima")

    # ---- cartão de crédito ----
    s, card, _ = call("POST", f"/api/workspaces/{ws}/cards", ana,
                      {"name": "Nubank", "last4": "1234", "closingDay": 10, "dueDay": 17, "creditLimit": "5000.00"})
    check("cartão cadastrado só com o final", s == 201 and card["last4"] == "1234")
    check("número completo do cartão recusado", call("POST", f"/api/workspaces/{ws}/cards", ana,
          {"name": "X", "last4": "4111111111111111", "closingDay": 10, "dueDay": 17, "creditLimit": "1.00"})[0] == 400)
    s, pur, _ = call("POST", f"/api/workspaces/{ws}/cards/{card['id']}/purchases", bruno,
                     {"description": "Geladeira", "amount": "100.00", "installments": 3, "date": "2026-11-20", "categoryId": food})
    check("compra em 3× nas faturas certas", s == 201 and [p["statement"] for p in pur["parts"]] == ["2026-12", "2027-01", "2027-02"], pur)
    st = call("GET", f"/api/workspaces/{ws}/cards/{card['id']}/statements/2026-12", ana)[1]
    check("fatura de dezembro: R$ 33,34, vence 17/12", float(st["total"]) == 33.34 and st["dueDate"] == "2026-12-17", st)
    s, pay, _ = call("POST", f"/api/workspaces/{ws}/cards/{card['id']}/statements/2026-12/payments", ana,
                     {"accountId": chk["id"], "amount": "33.34", "date": today})
    check("pagamento da fatura", s == 201 and pay["type"] == "CARD_PAYMENT")
    check("fatura paga", call("GET", f"/api/workspaces/{ws}/cards/{card['id']}/statements/2026-12", ana)[1]["status"] == "PAID")
    bal = {a["name"]: float(a["balance"]) for a in call("GET", f"/api/workspaces/{ws}/accounts", ana)[1]}
    check("pagamento saiu da conta (4264,10 − 33,34)", abs(bal["Nubank"] - 4230.76) < 0.001, bal)
    dz = call("GET", f"/api/workspaces/{ws}/dashboard?month=2026-12", ana)[1]
    check("painel de dezembro: gasto 33,34 (o pagamento não conta de novo)",
          float(dz["expense"]) == 33.34 and dz["cards"][0]["name"] == "Nubank", dz.get("expense"))

    # ---- contas fixas, orçamento e previsão ----
    month = today[:7]
    s, rec, _ = call("POST", f"/api/workspaces/{ws}/recurrences", ana, {"type": "EXPENSE", "description": "Internet", "amount": "100.00",
                     "dayOfMonth": 28, "categoryId": food, "accountId": chk["id"], "startMonth": month})
    check("conta fixa cadastrada", s == 201)
    occ = call("GET", f"/api/workspaces/{ws}/recurrences/occurrences?month={month}", bruno)[1]
    check("conta fixa aparece no mês, ainda não paga", [o["status"] for o in occ if o["description"] == "Internet"][0] != "PAID")
    fc = call("GET", f"/api/workspaces/{ws}/forecast", ana)[1]
    check("previsão = saldo + a receber − a pagar", abs(float(fc["projectedBalance"]) - (float(fc["currentBalance"])
          + float(fc["pendingIncome"]) - float(fc["pendingExpense"]))) < 0.001, fc)
    check("previsão inclui a conta fixa não paga", "Internet" in [i["description"] for i in fc["items"]])
    s, _, _ = call("POST", f"/api/workspaces/{ws}/recurrences/{rec['id']}/occurrences/{month}/confirm", bruno,
                   {"amount": "104.90", "date": today, "accountId": chk["id"]})
    check("confirmar com o valor real", s == 201)
    s, dup, _ = call("POST", f"/api/workspaces/{ws}/recurrences/{rec['id']}/occurrences/{month}/confirm", ana,
                     {"amount": "104.90", "date": today, "accountId": chk["id"]})
    check("não confirma duas vezes no mesmo mês", s == 409, dup)
    fc2 = call("GET", f"/api/workspaces/{ws}/forecast", ana)[1]
    check("paga, a conta fixa sai da previsão", "Internet" not in [i["description"] for i in fc2["items"]])
    s, _, _ = call("PUT", f"/api/workspaces/{ws}/budgets/{food}", ana, {"amount": "150.00"})
    bud = [b for b in call("GET", f"/api/workspaces/{ws}/budgets?month={month}", ana)[1] if b["category"]["id"] == food][0]
    check("orçamento soma o gasto do mês (almoço + conta fixa)", s == 200 and float(bud["spent"]) >= 35.90 + 104.90, bud)
    check("intrusa não vê previsão nem orçamento", call("GET", f"/api/workspaces/{ws}/forecast", eve)[0] == 404
          and call("GET", f"/api/workspaces/{ws}/budgets?month={month}", eve)[0] == 404)

    # ---- isolamento ----
    own = me_e["workspaces"][0]["id"]
    codes = [call("GET", f"/api/workspaces/{ws}/transactions", eve)[0],
             call("GET", f"/api/workspaces/{ws}/accounts", eve)[0],
             call("GET", f"/api/workspaces/{ws}/cards/{card['id']}/statements/2026-12", eve)[0],
             call("POST", f"/api/workspaces/{ws}/transactions", eve, {"type": "EXPENSE", "amount": "1.00", "date": today,
                  "description": "x", "accountId": chk["id"], "categoryId": food})[0],
             call("POST", f"/api/workspaces/{own}/transactions", eve, {"type": "EXPENSE", "amount": "1.00", "date": today,
                  "description": "x", "accountId": chk["id"], "categoryId": food})[0]]
    check("intrusa: 404 no espaço alheio, 400 com ids alheios no próprio", codes == [404, 404, 404, 404, 400], codes)

    # ---- borda ----
    s, _, h = call("GET", "/api/me", ana)
    csp = h.get("Content-Security-Policy", "")
    check("cabeçalhos: HSTS, CSP, nosniff, frame DENY", "max-age" in h.get("Strict-Transport-Security", "")
          and "script-src 'self'" in csp and h.get("X-Content-Type-Options") == "nosniff" and h.get("X-Frame-Options") == "DENY", csp)
    check("cabeçalhos sem duplicados vindos da API", csp.count("default-src") == 1)
    check("actuator não sai para a internet", call("GET", "/actuator/health")[0] == 404)
    s, b, _ = call("GET", "/v3/api-docs", ana)
    check("especificação da API (OpenAPI) não é exposta", "openapi" not in str(b).lower())
    s, _, _ = call("POST", "/api/auth/logout", bruno)
    check("logout revoga o token", s == 204 and call("GET", "/api/me", bruno)[0] == 401)

    print(f"\n{sum(results)}/{len(results)} OK")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
