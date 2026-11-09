"""
Testes de integração da API do Thindesk contra a aplicação rodando (com o MongoDB Atlas real).

Uso:
    python scripts/testes_api.py http://localhost:8080 <email> <senha> [arquivo_saida.md]

O usuário precisa ter perfil MANAGER ou ADMIN. O script cria um chamado de teste e o leva até "Resolvido",
passando pelos casos de erro previstos na documentação, e grava um relatório em Markdown com o resultado de
cada caso (status HTTP esperado x obtido).
"""
import base64
import json
import sys
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta

BASE = sys.argv[1].rstrip("/") + "/api/v1"
AUTH = "Basic " + base64.b64encode(f"{sys.argv[2]}:{sys.argv[3]}".encode()).decode()
OUT = sys.argv[4] if len(sys.argv) > 4 else None

today = date.today()
period = f"from={today - timedelta(days=29)}&to={today}"
results = []


def call(method, path, body=None, auth=True):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if auth:
        req.add_header("Authorization", AUTH)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode("utf-8")
            return resp.status, json.loads(text) if text else None, dict(resp.headers)
    except urllib.error.HTTPError as err:
        text = err.read().decode("utf-8")
        try:
            return err.code, json.loads(text), dict(err.headers)
        except ValueError:
            return err.code, {"raw": text[:200]}, dict(err.headers)


def case(rf, name, method, path, expected, body=None, auth=True, check=None):
    status, payload, headers = call(method, path, body, auth)
    ok = status == expected
    detail = ""
    if ok and check:
        try:
            detail = check(payload, headers) or ""
        except AssertionError as err:
            ok, detail = False, f"verificação falhou: {err}"
    if not detail and isinstance(payload, dict):
        detail = payload.get("code") or payload.get("detail") or ""
    results.append((rf, name, f"{method} {path.split('?')[0]}", expected, status, ok, str(detail)[:110]))
    return payload


def has(condition, message):
    assert condition, message
    return message


# Autenticação e autorização
case("RNF", "Sem credenciais retorna 401 em JSON", "GET", "/dashboard/summary", 401, auth=False,
     check=lambda p, h: has(p["code"] == "UNAUTHORIZED" and "WWW-Authenticate" not in h, "UNAUTHORIZED, sem WWW-Authenticate"))

# RF01
case("RF01", "Indicadores gerais do período", "GET", f"/dashboard/summary?{period}", 200,
     check=lambda p, h: has(p["totals"]["total"] >= p["totals"]["solved"], f"total={p['totals']['total']}, atendidos={p['totals']['solved']}, SLA={p['rates']['slaMetPercent']}%"))
case("RF01", "Comparação com o período anterior", "GET", f"/dashboard/summary?{period}", 200,
     check=lambda p, h: has(p["comparison"] is not None, f"anterior={p['comparison']['previousTotal']}, variação={p['comparison']['totalChangePercent']}%"))
case("RF01", "Indicadores sem período (todo o histórico)", "GET", "/dashboard/summary", 200,
     check=lambda p, h: has(p["comparison"] is None, f"total={p['totals']['total']}, sem comparação"))

# RF02
case("RF02", "Série diária (granularidade automática)", "GET", f"/dashboard/timeline?{period}", 200,
     check=lambda p, h: has(p["granularity"]["code"] == "DAY" and len(p["points"]) == 30, f"{len(p['points'])} pontos por dia"))
case("RF02", "Série semanal", "GET", f"/dashboard/timeline?{period}&granularity=WEEK", 200,
     check=lambda p, h: has(p["granularity"]["code"] == "WEEK", f"{len(p['points'])} semanas"))
case("RF02", "Série de 180 dias (automática: semanal)", "GET", f"/dashboard/timeline?from={today - timedelta(days=179)}&to={today}", 200,
     check=lambda p, h: has(p["granularity"]["code"] == "WEEK", f"auto={p['granularity']['code']}, {len(p['points'])} pontos"))
case("RF02", "Granularidade inexistente", "GET", "/dashboard/timeline?granularity=YEAR", 400)
case("RF02", "DAY em período longo demais", "GET", f"/dashboard/timeline?from={today - timedelta(days=700)}&to={today}&granularity=DAY", 400)

# RF03
case("RF03", "Situação dos chamados", "GET", f"/dashboard/status?{period}", 200,
     check=lambda p, h: has(len(p["items"]) == 6 and sum(i["count"] for i in p["items"]) == p["total"], "6 situações, soma = total"))

# RF04
case("RF04", "Tipos mais atendidos (top 3)", "GET", f"/dashboard/types?{period}&limit=3", 200,
     check=lambda p, h: has(len(p["items"]) <= 3, " > ".join(i["label"] for i in p["items"])))
case("RF04", "Ranking por total aberto", "GET", f"/dashboard/types?{period}&metric=TOTAL", 200,
     check=lambda p, h: has(p["metric"]["code"] == "TOTAL", "metric=TOTAL"))
case("RF04", "limit fora do intervalo", "GET", "/dashboard/types?limit=99", 400)
case("RF04", "Desempenho por técnico", "GET", f"/dashboard/technicians?{period}", 200,
     check=lambda p, h: has(len(p["items"]) > 0, f"{len(p['items'])} técnicos, 1º: {p['items'][0]['name']}"))

# RF05
case("RF05", "Opções dos filtros", "GET", "/dashboard/filters", 200,
     check=lambda p, h: has(len(p["statuses"]) == 6 and len(p["technicians"]) > 0, f"{len(p['types'])} tipos, {len(p['technicians'])} técnicos"))
case("RF05", "Filtro combinado (situação, tipo, prioridade)", "GET", f"/dashboard/summary?{period}&status=CLOSED,RESOLVED&type=HARDWARE&priority=HIGH", 200,
     check=lambda p, h: has(p["totals"]["active"] == 0, f"total={p['totals']['total']}, nenhum em aberto"))
case("RF05", "Busca textual na lista", "GET", "/tickets?q=impressora&size=5", 200,
     check=lambda p, h: has(p["totalElements"] > 0 and all("mpressora" in t["title"] for t in p["content"]), f"{p['totalElements']} encontrados"))
case("RF05", "Só atrasados, ordenado por prazo", "GET", "/tickets?overdue=true&sort=dueAt,asc&size=5", 200,
     check=lambda p, h: has(all(t["overdue"] for t in p["content"]), f"{p['totalElements']} atrasados"))
case("RF05", "Data inicial depois da final", "GET", f"/dashboard/summary?from={today}&to={today - timedelta(days=5)}", 400)
case("RF05", "Data em formato inválido", "GET", "/dashboard/summary?from=09/11/2026", 400)
case("RF05", "Situação inexistente", "GET", "/dashboard/summary?status=ABERTO", 400)
case("RF05", "Ordenação por campo não permitido", "GET", "/tickets?sort=password,asc", 400)
case("RF05", "Página grande demais", "GET", "/tickets?size=500", 400)

# RF06 / RF07: ciclo de vida completo de um chamado
created = case("RF07", "Abrir chamado", "POST", "/tickets", 201, body={
    "title": "Teste automatizado de integração", "description": "Chamado criado pelo roteiro de testes da API.",
    "type": "NETWORK", "priority": "HIGH", "requesterName": "Roteiro de Testes",
    "requesterEmail": "Testes@Exemplo.com.br", "department": "OPERATIONS"},
    check=lambda p, h: has("Location" in h and p["requesterEmail"] == "testes@exemplo.com.br", f"{p['protocol']}, e-mail normalizado"))
tid = created["id"]
technician = call("GET", "/technicians?active=true")[1][0]["id"]
case("RF07", "Corpo inválido lista erros por campo", "POST", "/tickets", 400, body={"title": "abc", "requesterEmail": "x"},
     check=lambda p, h: has(len(p["errors"]) >= 5, f"{len(p['errors'])} campos com erro"))
case("RF07", "Iniciar atendimento sem técnico", "PATCH", f"/tickets/{tid}/status", 422, body={"status": "IN_PROGRESS"})
case("RF07", "Transição fora do fluxo (Aberto > Resolvido)", "PATCH", f"/tickets/{tid}/status", 422,
     body={"status": "RESOLVED", "note": "x"})
case("RF07", "Atribuir técnico", "PATCH", f"/tickets/{tid}/assignment", 200, body={"technicianId": technician},
     check=lambda p, h: has(p["technician"]["id"] == technician, p["technician"]["name"]))
case("RF07", "Técnico inexistente", "PATCH", f"/tickets/{tid}/assignment", 422, body={"technicianId": "nao-existe"})
case("RF07", "Iniciar atendimento", "PATCH", f"/tickets/{tid}/status", 200, body={"status": "IN_PROGRESS"},
     check=lambda p, h: has(p["firstResponseAt"] is not None, "primeira resposta registrada"))
case("RF07", "Registrar consideração técnica", "POST", f"/tickets/{tid}/notes", 201, body={"text": "Diagnóstico: porta do switch desativada."})
case("RF07", "Resolver sem informar a solução", "PATCH", f"/tickets/{tid}/status", 422, body={"status": "RESOLVED"})
case("RF07", "Resolver com a solução", "PATCH", f"/tickets/{tid}/status", 200,
     body={"status": "RESOLVED", "note": "Porta reativada e VLAN corrigida."},
     check=lambda p, h: has(p["status"]["code"] == "RESOLVED" and p["slaMet"] is True, "resolvido dentro do SLA"))
case("RF07", "Fechar chamado", "PATCH", f"/tickets/{tid}/status", 200, body={"status": "CLOSED"},
     check=lambda p, h: has(p["allowedTransitions"] == [], "sem transições possíveis"))
case("RF07", "Nota em chamado fechado", "POST", f"/tickets/{tid}/notes", 422, body={"text": "depois de fechado"})
case("RF06", "Detalhe com notas e histórico", "GET", f"/tickets/{tid}", 200,
     check=lambda p, h: has(len(p["history"]) == 3, f"{len(p['notes'])} notas, {len(p['history'])} mudanças de situação"))
case("RF06", "Chamado inexistente", "GET", "/tickets/000000000000000000000000", 404)
case("RF06", "Rota inexistente", "GET", "/nao-existe", 404)
case("RF06", "Método não suportado", "DELETE", f"/tickets/{tid}", 405)
case("RF06", "JSON malformado", "POST", "/tickets", 400, body=None)

passed = sum(1 for r in results if r[5])
lines = [
    "# Evidências: testes de integração da API",
    "",
    f"Executado em {datetime.now():%d/%m/%Y %H:%M} contra `{sys.argv[1]}` com o banco MongoDB Atlas real.",
    f"Período usado nos filtros: `{period}`.",
    "",
    f"**Resultado: {passed} de {len(results)} casos aprovados.**",
    "",
    "| # | RF | Caso | Requisição | Esperado | Obtido | Resultado | Observação |",
    "|---|----|------|------------|----------|--------|-----------|------------|",
]
for i, (rf, name, req, exp, got, ok, detail) in enumerate(results, 1):
    lines.append(f"| {i} | {rf} | {name} | `{req}` | {exp} | {got} | {'OK' if ok else 'FALHOU'} | {detail.replace('|', '/')} |")
report = "\n".join(lines) + "\n"
if OUT:
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(report)
print(report)
sys.exit(0 if passed == len(results) else 1)
