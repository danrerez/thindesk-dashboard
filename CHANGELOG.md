# Changelog

## [1.1.0] - 2026-11-12

### Adicionado

- Documentação técnica em Word e PDF nas normas ABNT (NBR 14724 e NBR 6023), com diagramas de arquitetura, modelo de dados e fluxo de situações.

## [1.0.1] - 2026-11-09

### Alterado

- `.env.example`, coleção Postman e exemplo de `curl` do README usam chaves `${YOUR_...}` no lugar de credenciais de exemplo.

## [1.0.0] - 2026-11-09

Primeira versão do dashboard administrativo do Thindesk.

### Adicionado

- Base de login seguro reaproveitada da atividade M1 (perfis USER, MANAGER e ADMIN, sessões no MongoDB Atlas).
- Domínio de chamados técnicos com SLA por prioridade, fluxo de situações, considerações técnicas e histórico.
- API REST `/api/v1` com 13 endpoints: indicadores (RF01), série histórica (RF02), situação (RF03), tipos mais atendidos (RF04), filtros (RF05) e atualização de chamados (RF07). Erros em Problem Details (RFC 9457) e documentação OpenAPI.
- Dashboard, lista e detalhe de chamados consumindo a API (RF06), com atualização automática a cada 60 segundos.
- Massa de demonstração com 1.400 chamados e 9 técnicos.
- 75 testes automatizados e roteiro de 40 testes de integração contra o banco real.
- Documentação, evidências de testes e coleção Postman.
