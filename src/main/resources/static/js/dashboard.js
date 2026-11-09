/*
 * Dashboard administrativo. Busca cada bloco em um endpoint próprio da API (RF06), em paralelo, e atualiza
 * os dados sozinho no intervalo configurado (RF07). Se um endpoint falhar, só o bloco dele mostra o erro.
 */
(function () {
    'use strict';

    const { api, format, escapeHtml, badge, cssVar } = window.Thindesk;

    const STATUS_COLORS = {
        OPEN: ['--status-open', '#2f5bea'],
        IN_PROGRESS: ['--status-in-progress', '#8a4fd8'],
        WAITING_USER: ['--status-waiting-user', '#d08a1e'],
        RESOLVED: ['--status-resolved', '#1f8a5b'],
        CLOSED: ['--status-closed', '#5b8c7a'],
        CANCELED: ['--status-canceled', '#9aa3b2'],
    };

    const root = document.querySelector('.dashboard');
    const refreshSeconds = Number(root.dataset.refreshSeconds) || 60;
    const state = { params: {}, granularity: '', metric: 'SOLVED', loading: false, lastLoad: 0, charts: {} };

    const $ = (selector) => root.querySelector(selector);

    function color(code) {
        const [variable, fallback] = STATUS_COLORS[code];
        return cssVar(variable, fallback);
    }

    function setupChartDefaults() {
        Chart.defaults.font.family = cssVar('--font-sans', 'system-ui');
        Chart.defaults.color = cssVar('--color-muted', '#5f6b7d');
        Chart.defaults.borderColor = cssVar('--color-border', '#dde2ea');
        Chart.defaults.maintainAspectRatio = false;
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
    }

    function chart(id, config) {
        if (state.charts[id]) {
            state.charts[id].data = config.data;
            state.charts[id].options = config.options;
            state.charts[id].update();
            return state.charts[id];
        }
        state.charts[id] = new Chart(document.getElementById(id), config);
        return state.charts[id];
    }

    function widgetError(name, error) {
        const widget = root.querySelector('[data-widget="' + name + '"]');
        if (!widget) return;
        widget.classList.add('widget--error');
        let box = widget.querySelector('.widget__error');
        if (!box) {
            box = document.createElement('p');
            box.className = 'widget__error alert alert--danger';
            widget.appendChild(box);
        }
        box.textContent = 'Não foi possível carregar este bloco: ' + error.message;
    }

    function widgetOk(name) {
        const widget = root.querySelector('[data-widget="' + name + '"]');
        if (!widget) return;
        widget.classList.remove('widget--error');
        widget.querySelector('.widget__error')?.remove();
    }

    // RF01
    function renderSummary(data) {
        const { totals, rates, times, comparison } = data;
        const set = (key, value) => { $('[data-kpi="' + key + '"]').textContent = value; };
        const hint = (key, html) => { $('[data-kpi-hint="' + key + '"]').innerHTML = html; };
        const trend = (value, positiveIsGood) => {
            if (value === null || value === undefined) return '';
            const good = positiveIsGood ? value >= 0 : value <= 0;
            return '<span class="trend ' + (good ? 'trend--good' : 'trend--bad') + '">' + format.change(value) + '</span> ';
        };

        set('total', format.number(totals.total));
        set('active', format.number(totals.active));
        set('solved', format.number(totals.solved));
        set('overdue', format.number(totals.overdue));
        set('resolutionRate', format.percent(rates.resolutionRate));
        set('slaMetPercent', format.percent(rates.slaMetPercent));
        set('avgFirstResponseHours', format.hours(times.avgFirstResponseHours));
        set('avgResolutionHours', format.hours(times.avgResolutionHours));

        hint('total', comparison
            ? trend(comparison.totalChangePercent, true) + 'vs. ' + format.number(comparison.previousTotal) + ' no período anterior'
            : 'sem comparação (período aberto)');
        hint('solved', comparison
            ? trend(comparison.solvedChangePercent, true) + 'vs. ' + format.number(comparison.previousSolved) + ' no período anterior'
            : format.number(totals.canceled) + ' cancelados');
        hint('active', format.number(totals.open) + ' abertos, ' + format.number(totals.inProgress) + ' em atendimento, '
            + format.number(totals.waitingUser) + ' aguardando');
        hint('overdue', count(totals.criticalActive, 'crítico', 'críticos') + ' em aberto, '
            + format.number(totals.unassignedActive) + ' sem técnico');
        hint('avgResolutionHours', 'mediana: ' + format.hours(times.medianResolutionHours));
        root.querySelector('.kpi--danger').classList.toggle('is-alert', totals.overdue > 0);
    }

    // RF02
    function renderTimeline(data) {
        const widget = root.querySelector('[data-widget="timeline"]');
        const empty = data.points.length === 0 || (data.totalOpened === 0 && data.totalSolved === 0);
        widget.querySelector('.widget__empty').hidden = !empty;
        widget.querySelector('.chart').hidden = empty;
        $('[data-timeline-caption]').textContent = 'Agrupado por ' + data.granularity.label.toLowerCase() + ': '
            + format.number(data.totalOpened) + ' abertos e ' + format.number(data.totalSolved) + ' atendidos';
        chart('chart-timeline', {
            data: {
                labels: data.points.map((p) => p.label),
                datasets: [
                    { type: 'bar', label: 'Abertos', data: data.points.map((p) => p.opened),
                        backgroundColor: color('OPEN'), borderRadius: 4, yAxisID: 'y', order: 2 },
                    { type: 'bar', label: 'Atendidos', data: data.points.map((p) => p.solved),
                        backgroundColor: color('RESOLVED'), borderRadius: 4, yAxisID: 'y', order: 2 },
                    { type: 'line', label: 'Tempo médio de solução (h)', data: data.points.map((p) => p.avgResolutionHours),
                        borderColor: color('WAITING_USER'), backgroundColor: color('WAITING_USER'),
                        cubicInterpolationMode: 'monotone', spanGaps: true, pointRadius: 2, yAxisID: 'y2', order: 1 },
                ],
            },
            options: {
                interaction: { mode: 'index', intersect: false },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Chamados' }, ticks: { precision: 0 } },
                    y2: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false },
                        title: { display: true, text: 'Horas' } },
                    x: { grid: { display: false } },
                },
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: (ctx) => ctx.dataset.yAxisID === 'y2'
                                ? ' Tempo médio: ' + format.hours(ctx.raw)
                                : ' ' + ctx.dataset.label + ': ' + format.number(ctx.raw),
                        },
                    },
                },
            },
        });
    }

    // RF03
    function renderStatus(data) {
        chart('chart-status', {
            type: 'doughnut',
            data: {
                labels: data.items.map((i) => i.label),
                datasets: [{
                    data: data.items.map((i) => i.count),
                    backgroundColor: data.items.map((i) => color(i.code)),
                    borderColor: cssVar('--color-surface', '#fff'),
                    borderWidth: 2,
                }],
            },
            options: { cutout: '62%', plugins: { legend: { display: false } } },
        });
        $('[data-status-legend]').innerHTML = data.items.map((i) => {
            const params = new URLSearchParams(Object.assign({}, state.params, { status: i.code }));
            return '<li><a class="legend__item" href="/tickets?' + params + '">'
                + '<span class="legend__dot" data-color="' + i.code + '"></span>'
                + '<span class="legend__label">' + escapeHtml(i.label) + '</span>'
                + '<strong>' + format.number(i.count) + '</strong>'
                + '<span class="muted small">' + format.percent(i.percent) + '</span></a></li>';
        }).join('');
        root.querySelectorAll('.legend__dot').forEach((dot) => { dot.style.background = color(dot.dataset.color); });

        const max = Math.max(1, ...data.activeByPriority.map((p) => p.active));
        $('[data-priority-rows]').innerHTML = data.activeByPriority.map((p) =>
            '<tr><td>' + badge({ code: p.code, label: p.label }, 'priority') + '</td>'
            + '<td class="muted small">' + p.slaHours + ' h</td>'
            + '<td class="text-right">' + format.number(p.active) + '</td>'
            + '<td class="text-right' + (p.overdue > 0 ? ' text-danger' : '') + '">' + format.number(p.overdue) + '</td>'
            + '<td class="col-bar"><span class="bar"><span class="bar__fill" data-width="' + (p.active / max * 100) + '"></span>'
            + '<span class="bar__fill bar__fill--danger" data-width="' + (p.overdue / max * 100) + '"></span></span></td></tr>'
        ).join('');
        root.querySelectorAll('[data-priority-rows] [data-width]').forEach((el) => { el.style.width = el.dataset.width + '%'; });
    }

    // RF04
    function renderTypes(data) {
        const solvedMetric = data.metric.code === 'SOLVED';
        $('[data-types-caption]').textContent = solvedMetric
            ? 'Ranking por chamados resolvidos ou fechados'
            : 'Ranking por chamados abertos no período';
        chart('chart-types', {
            type: 'bar',
            data: {
                labels: data.items.map((i) => i.label),
                datasets: [{
                    label: solvedMetric ? 'Atendidos' : 'Abertos',
                    data: data.items.map((i) => (solvedMetric ? i.solved : i.total)),
                    backgroundColor: solvedMetric ? color('RESOLVED') : color('OPEN'),
                    borderRadius: 4,
                }],
            },
            options: {
                indexAxis: 'y',
                plugins: { legend: { display: false },
                    tooltip: { callbacks: { label: (ctx) => ' ' + format.number(ctx.raw) + ' chamados ('
                        + format.percent(data.items[ctx.dataIndex].share) + ')' } } },
                scales: { x: { beginAtZero: true, ticks: { precision: 0 } }, y: { grid: { display: false } } },
            },
        });
        $('[data-type-rows]').innerHTML = data.items.length === 0
            ? '<tr><td colspan="6" class="muted">Nenhum chamado no período.</td></tr>'
            : data.items.map((i) => '<tr><td class="muted">' + i.position + '</td><td>' + escapeHtml(i.label) + '</td>'
                + '<td class="text-right">' + format.number(i.total) + '</td>'
                + '<td class="text-right">' + format.number(i.solved) + '</td>'
                + '<td class="text-right">' + format.hours(i.avgResolutionHours) + '</td>'
                + '<td class="text-right">' + slaBadge(i.slaMetPercent) + '</td></tr>').join('');
    }

    function renderTechnicians(data) {
        $('[data-technicians-caption]').textContent = 'Ordenado por chamados atendidos. '
            + format.number(data.unassignedActive) + ' chamados em aberto sem técnico.';
        $('[data-technician-rows]').innerHTML = data.items.length === 0
            ? '<tr><td colspan="7" class="muted">Nenhum chamado atribuído no período.</td></tr>'
            : data.items.map((i) => '<tr><td class="muted">' + i.position + '</td>'
                + '<td><a href="/tickets?' + new URLSearchParams(Object.assign({}, state.params, { technicianId: i.id })) + '">'
                + escapeHtml(i.name) + '</a></td>'
                + '<td class="text-right">' + format.number(i.assigned) + '</td>'
                + '<td class="text-right">' + format.number(i.solved) + '</td>'
                + '<td class="text-right">' + format.number(i.active)
                + (i.overdue > 0 ? ' <span class="text-danger small">(' + count(i.overdue, 'atrasado', 'atrasados') + ')</span>' : '') + '</td>'
                + '<td class="text-right">' + format.hours(i.avgResolutionHours) + '</td>'
                + '<td class="text-right">' + slaBadge(i.slaMetPercent) + '</td></tr>').join('');
    }

    function renderOverdue(page) {
        const now = Date.now();
        $('[data-overdue-rows]').innerHTML = page.content.length === 0
            ? '<tr><td colspan="6" class="muted">Nenhum chamado atrasado com os filtros atuais.</td></tr>'
            : page.content.map((t) => '<tr><td><a href="/tickets/' + encodeURIComponent(t.id) + '">' + escapeHtml(t.protocol) + '</a></td>'
                + '<td>' + escapeHtml(t.title) + '</td>'
                + '<td>' + badge(t.priority, 'priority') + '</td>'
                + '<td>' + badge(t.status, 'status') + '</td>'
                + '<td>' + (t.technician ? escapeHtml(t.technician.name) : '<span class="text-danger">Sem técnico</span>') + '</td>'
                + '<td class="text-right text-danger">' + format.hours((now - new Date(t.dueAt).getTime()) / 3600000) + '</td></tr>').join('');
        const link = $('[data-overdue-link]');
        link.href = '/tickets?' + new URLSearchParams(Object.assign({}, state.params, { overdue: 'true' }));
        link.textContent = 'Ver todos (' + format.number(page.totalElements) + ')';
    }

    /** "1 chamado" / "2 chamados". */
    function count(n, singular, plural) {
        return format.number(n) + ' ' + (n === 1 ? singular : plural);
    }

    function slaBadge(value) {
        if (value === null || value === undefined) return '<span class="muted">-</span>';
        const css = value >= 85 ? 'success' : value >= 70 ? 'warning' : 'danger';
        return '<span class="badge badge--' + css + '">' + format.percent(value) + '</span>';
    }

    /** Destaques em texto para apoiar a decisão, calculados a partir das respostas já carregadas. */
    function renderInsights(results) {
        const [summary, , status, types, technicians] = results.map((r) => (r.status === 'fulfilled' ? r.value : null));
        const items = [];
        if (summary) {
            const { totals, comparison, rates } = summary;
            if (comparison && comparison.totalChangePercent !== null) {
                const up = comparison.totalChangePercent >= 0;
                items.push(['info', 'Volume ' + (up ? 'maior' : 'menor') + ' que o período anterior: '
                    + format.number(totals.total) + ' chamados (' + format.change(comparison.totalChangePercent) + ').']);
            }
            if (totals.overdue > 0) {
                items.push(['danger', count(totals.overdue, 'chamado em aberto já passou', 'chamados em aberto já passaram')
                    + ' do prazo de SLA.']);
            }
            if (totals.unassignedActive > 0) {
                items.push(['warning', count(totals.unassignedActive, 'chamado aguarda', 'chamados aguardam')
                    + ' atribuição de técnico.']);
            }
            if (rates.slaMetPercent !== null) {
                items.push([rates.slaMetPercent >= 85 ? 'success' : 'warning',
                    'SLA cumprido em ' + format.percent(rates.slaMetPercent) + ' dos chamados resolvidos (meta: 85%).']);
            }
        }
        if (types && types.items.length) {
            const top = types.items[0];
            items.push(['info', 'Tipo mais atendido: ' + top.label + ' (' + format.number(top.solved) + ' atendidos, '
                + format.percent(top.share) + ' do total).']);
            const worst = types.items.filter((i) => i.slaMetPercent !== null && i.solved >= 5)
                .sort((a, b) => a.slaMetPercent - b.slaMetPercent)[0];
            if (worst && worst.slaMetPercent < 85) {
                items.push(['warning', 'Pior SLA por tipo: ' + worst.label + ' (' + format.percent(worst.slaMetPercent)
                    + ', tempo médio de ' + format.hours(worst.avgResolutionHours) + ').']);
            }
        }
        if (technicians && technicians.items.length) {
            const best = technicians.items[0];
            items.push(['success', 'Técnico com mais atendimentos: ' + best.name + ' (' + format.number(best.solved) + ').']);
            const loaded = technicians.items.slice().sort((a, b) => b.active - a.active)[0];
            if (loaded && loaded.active > 0 && loaded.id !== best.id) {
                items.push(['info', 'Maior fila atual: ' + loaded.name + ' com ' + count(loaded.active, 'chamado', 'chamados') + ' em aberto.']);
            }
        }
        if (status) {
            const critical = status.activeByPriority.find((p) => p.code === 'CRITICAL');
            if (critical && critical.active > 0) {
                items.push(['danger', count(critical.active, 'chamado crítico', 'chamados críticos') + ' em aberto (SLA de '
                    + critical.slaHours + ' h).']);
            }
        }
        $('[data-insights]').innerHTML = items.length
            ? items.map(([kind, text]) => '<li class="insight insight--' + kind + '">' + escapeHtml(text) + '</li>').join('')
            : '<li class="muted">Sem dados suficientes no período.</li>';
    }

    async function load() {
        if (state.loading) return;
        state.loading = true;
        root.classList.add('is-loading');
        const params = state.params;
        const requests = [
            ['summary', api.get('/api/v1/dashboard/summary', params), renderSummary],
            ['timeline', api.get('/api/v1/dashboard/timeline', Object.assign({ granularity: state.granularity }, params)), renderTimeline],
            ['status', api.get('/api/v1/dashboard/status', params), renderStatus],
            ['types', api.get('/api/v1/dashboard/types', Object.assign({ metric: state.metric }, params)), renderTypes],
            ['technicians', api.get('/api/v1/dashboard/technicians', params), renderTechnicians],
            ['overdue', api.get('/api/v1/tickets', Object.assign({}, params, { overdue: true, sort: 'dueAt,asc', size: 8 })), renderOverdue],
        ];
        const results = await Promise.allSettled(requests.map((r) => r[1]));
        let failures = 0;
        results.forEach((result, index) => {
            const [name, , render] = requests[index];
            if (result.status === 'fulfilled') {
                try {
                    render(result.value);
                    widgetOk(name);
                } catch (e) {
                    console.error(e);
                    widgetError(name, e);
                }
            } else {
                failures++;
                widgetError(name, result.reason);
            }
        });
        renderInsights(results);

        const globalError = $('[data-global-error]');
        globalError.hidden = failures < requests.length;
        if (failures === requests.length) {
            globalError.textContent = 'A API não respondeu: ' + results[0].reason.message
                + ' Os dados exibidos podem estar desatualizados.';
        }
        state.lastLoad = Date.now();
        $('[data-last-update]').textContent = failures === 0
            ? 'Atualizado às ' + format.time(new Date())
            : 'Atualizado às ' + format.time(new Date()) + ' (' + failures + ' bloco(s) com erro)';
        root.classList.remove('is-loading');
        state.loading = false;
    }

    function segmented(selector, key) {
        root.querySelectorAll(selector).forEach((button) => {
            button.addEventListener('click', () => {
                root.querySelectorAll(selector).forEach((b) => b.classList.toggle('is-active', b === button));
                state[key] = button.dataset[key];
                load();
            });
        });
    }

    function setupAutoRefresh() {
        const toggle = $('[data-auto-refresh]');
        setInterval(() => {
            if (toggle.checked && document.visibilityState === 'visible') load();
        }, refreshSeconds * 1000);
        document.addEventListener('visibilitychange', () => {
            const stale = Date.now() - state.lastLoad > refreshSeconds * 1000;
            if (document.visibilityState === 'visible' && toggle.checked && stale) load();
        });
        $('[data-refresh]').addEventListener('click', load);
    }

    document.addEventListener('DOMContentLoaded', () => {
        setupChartDefaults();
        segmented('[data-granularity]', 'granularity');
        segmented('[data-metric]', 'metric');
        setupAutoRefresh();
        const filters = window.Thindesk.filters.init(root.querySelector('[data-filters]'), {
            onChange: (params) => {
                state.params = params;
                load();
            },
        });
        filters.ready.then((params) => {
            state.params = params;
            load();
        });
    });
})();
