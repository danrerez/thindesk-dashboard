# Evidências: testes de integração da API

Executado em 09/11/2026 11:10 contra `http://localhost:8081` com o banco MongoDB Atlas real.
Período usado nos filtros: `from=2026-10-11&to=2026-11-09`.

**Resultado: 40 de 40 casos aprovados.**

| # | RF | Caso | Requisição | Esperado | Obtido | Resultado | Observação |
|---|----|------|------------|----------|--------|-----------|------------|
| 1 | RNF | Sem credenciais retorna 401 em JSON | `GET /dashboard/summary` | 401 | 401 | OK | UNAUTHORIZED, sem WWW-Authenticate |
| 2 | RF01 | Indicadores gerais do período | `GET /dashboard/summary` | 200 | 200 | OK | total=227, atendidos=205, SLA=66.8% |
| 3 | RF01 | Comparação com o período anterior | `GET /dashboard/summary` | 200 | 200 | OK | anterior=259, variação=-12.4% |
| 4 | RF01 | Indicadores sem período (todo o histórico) | `GET /dashboard/summary` | 200 | 200 | OK | total=1402, sem comparação |
| 5 | RF02 | Série diária (granularidade automática) | `GET /dashboard/timeline` | 200 | 200 | OK | 30 pontos por dia |
| 6 | RF02 | Série semanal | `GET /dashboard/timeline` | 200 | 200 | OK | 6 semanas |
| 7 | RF02 | Série de 180 dias (automática: semanal) | `GET /dashboard/timeline` | 200 | 200 | OK | auto=WEEK, 27 pontos |
| 8 | RF02 | Granularidade inexistente | `GET /dashboard/timeline` | 400 | 400 | OK | VALIDATION_ERROR |
| 9 | RF02 | DAY em período longo demais | `GET /dashboard/timeline` | 400 | 400 | OK | VALIDATION_ERROR |
| 10 | RF03 | Situação dos chamados | `GET /dashboard/status` | 200 | 200 | OK | 6 situações, soma = total |
| 11 | RF04 | Tipos mais atendidos (top 3) | `GET /dashboard/types` | 200 | 200 | OK | Software e instalação > Hardware > Sistema ERP |
| 12 | RF04 | Ranking por total aberto | `GET /dashboard/types` | 200 | 200 | OK | metric=TOTAL |
| 13 | RF04 | limit fora do intervalo | `GET /dashboard/types` | 400 | 400 | OK | VALIDATION_ERROR |
| 14 | RF04 | Desempenho por técnico | `GET /dashboard/technicians` | 200 | 200 | OK | 8 técnicos, 1º: Juliana Martins |
| 15 | RF05 | Opções dos filtros | `GET /dashboard/filters` | 200 | 200 | OK | 8 tipos, 9 técnicos |
| 16 | RF05 | Filtro combinado (situação, tipo, prioridade) | `GET /dashboard/summary` | 200 | 200 | OK | total=10, nenhum em aberto |
| 17 | RF05 | Busca textual na lista | `GET /tickets` | 200 | 200 | OK | 84 encontrados |
| 18 | RF05 | Só atrasados, ordenado por prazo | `GET /tickets` | 200 | 200 | OK | 12 atrasados |
| 19 | RF05 | Data inicial depois da final | `GET /dashboard/summary` | 400 | 400 | OK | VALIDATION_ERROR |
| 20 | RF05 | Data em formato inválido | `GET /dashboard/summary` | 400 | 400 | OK | VALIDATION_ERROR |
| 21 | RF05 | Situação inexistente | `GET /dashboard/summary` | 400 | 400 | OK | VALIDATION_ERROR |
| 22 | RF05 | Ordenação por campo não permitido | `GET /tickets` | 400 | 400 | OK | VALIDATION_ERROR |
| 23 | RF05 | Página grande demais | `GET /tickets` | 400 | 400 | OK | VALIDATION_ERROR |
| 24 | RF07 | Abrir chamado | `POST /tickets` | 201 | 201 | OK | TD-2026-001403, e-mail normalizado |
| 25 | RF07 | Corpo inválido lista erros por campo | `POST /tickets` | 400 | 400 | OK | 7 campos com erro |
| 26 | RF07 | Iniciar atendimento sem técnico | `PATCH /tickets/6af1d46dc27886ca290696ff/status` | 422 | 422 | OK | BUSINESS_RULE |
| 27 | RF07 | Transição fora do fluxo (Aberto > Resolvido) | `PATCH /tickets/6af1d46dc27886ca290696ff/status` | 422 | 422 | OK | BUSINESS_RULE |
| 28 | RF07 | Atribuir técnico | `PATCH /tickets/6af1d46dc27886ca290696ff/assignment` | 200 | 200 | OK | Ana Beatriz Souza |
| 29 | RF07 | Técnico inexistente | `PATCH /tickets/6af1d46dc27886ca290696ff/assignment` | 422 | 422 | OK | BUSINESS_RULE |
| 30 | RF07 | Iniciar atendimento | `PATCH /tickets/6af1d46dc27886ca290696ff/status` | 200 | 200 | OK | primeira resposta registrada |
| 31 | RF07 | Registrar consideração técnica | `POST /tickets/6af1d46dc27886ca290696ff/notes` | 201 | 201 | OK |  |
| 32 | RF07 | Resolver sem informar a solução | `PATCH /tickets/6af1d46dc27886ca290696ff/status` | 422 | 422 | OK | BUSINESS_RULE |
| 33 | RF07 | Resolver com a solução | `PATCH /tickets/6af1d46dc27886ca290696ff/status` | 200 | 200 | OK | resolvido dentro do SLA |
| 34 | RF07 | Fechar chamado | `PATCH /tickets/6af1d46dc27886ca290696ff/status` | 200 | 200 | OK | sem transições possíveis |
| 35 | RF07 | Nota em chamado fechado | `POST /tickets/6af1d46dc27886ca290696ff/notes` | 422 | 422 | OK | BUSINESS_RULE |
| 36 | RF06 | Detalhe com notas e histórico | `GET /tickets/6af1d46dc27886ca290696ff` | 200 | 200 | OK | 4 notas, 3 mudanças de situação |
| 37 | RF06 | Chamado inexistente | `GET /tickets/000000000000000000000000` | 404 | 404 | OK | NOT_FOUND |
| 38 | RF06 | Rota inexistente | `GET /nao-existe` | 404 | 404 | OK | NOT_FOUND |
| 39 | RF06 | Método não suportado | `DELETE /tickets/6af1d46dc27886ca290696ff` | 405 | 405 | OK | METHOD_NOT_ALLOWED |
| 40 | RF06 | JSON malformado | `POST /tickets` | 400 | 400 | OK | MALFORMED_BODY |
