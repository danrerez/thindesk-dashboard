/*
 * Lista de chamados com filtros, paginação e abertura de novos chamados, tudo pela API.
 */
(function () {
    'use strict';

    const { api, format, escapeHtml, badge } = window.Thindesk;
    const PAGE_SIZE = 20;

    const root = document.querySelector('.tickets-page');
    const $ = (selector) => root.querySelector(selector);
    const state = { params: {}, page: 0, sort: 'openedAt,desc' };

    async function load() {
        const rows = $('[data-rows]');
        rows.classList.add('is-loading');
        try {
            const page = await api.get('/api/v1/tickets', Object.assign({}, state.params,
                { page: state.page, size: PAGE_SIZE, sort: state.sort }));
            render(page);
        } catch (error) {
            rows.innerHTML = '<tr><td colspan="8"><div class="alert alert--danger">' + escapeHtml(error.message)
                + '</div></td></tr>';
            $('[data-result-count]').textContent = '';
            $('[data-pagination]').innerHTML = '';
        } finally {
            rows.classList.remove('is-loading');
        }
    }

    function render(page) {
        $('[data-result-count]').textContent = format.number(page.totalElements) + ' chamado(s) encontrado(s)';
        $('[data-rows]').innerHTML = page.content.length === 0
            ? '<tr><td colspan="8" class="muted">Nenhum chamado encontrado com esses filtros.</td></tr>'
            : page.content.map((t) => '<tr' + (t.overdue ? ' class="is-overdue"' : '') + '>'
                + '<td><a href="/tickets/' + encodeURIComponent(t.id) + '"><strong>' + escapeHtml(t.protocol) + '</strong></a></td>'
                + '<td>' + escapeHtml(t.title) + '<div class="muted small">' + escapeHtml(t.requesterName) + ' · '
                + escapeHtml(t.department?.label) + '</div></td>'
                + '<td class="small">' + escapeHtml(t.type.label) + '</td>'
                + '<td>' + badge(t.priority, 'priority') + '</td>'
                + '<td>' + badge(t.status, 'status') + '</td>'
                + '<td class="small">' + (t.technician ? escapeHtml(t.technician.name) : '<span class="muted">Sem técnico</span>') + '</td>'
                + '<td class="small">' + format.dateTime(t.openedAt) + '</td>'
                + '<td class="small">' + format.dateTime(t.dueAt)
                + (t.overdue ? ' <span class="badge badge--danger">Atrasado</span>' : '') + '</td></tr>').join('');
        renderPagination(page);
    }

    function renderPagination(page) {
        const nav = $('[data-pagination]');
        if (page.totalPages <= 1) {
            nav.innerHTML = '';
            return;
        }
        const button = (label, target, disabled, current) => '<button type="button" class="btn btn--ghost btn--sm'
            + (current ? ' is-active' : '') + '" data-page="' + target + '"' + (disabled ? ' disabled' : '') + '>' + label + '</button>';
        const pages = [];
        const start = Math.max(0, page.page - 2);
        const end = Math.min(page.totalPages - 1, page.page + 2);
        for (let p = start; p <= end; p++) pages.push(button(p + 1, p, false, p === page.page));
        nav.innerHTML = button('Anterior', page.page - 1, page.page === 0)
            + pages.join('')
            + button('Próxima', page.page + 1, page.page >= page.totalPages - 1)
            + '<span class="muted small">Página ' + (page.page + 1) + ' de ' + page.totalPages + '</span>';
        nav.querySelectorAll('[data-page]').forEach((b) => b.addEventListener('click', () => {
            state.page = Number(b.dataset.page);
            load();
            root.scrollIntoView({ behavior: 'smooth' });
        }));
    }

    function setupDialog() {
        const dialog = $('[data-new-ticket-dialog]');
        const form = $('[data-new-ticket-form]');
        const formError = form.querySelector('[data-form-error]');

        window.Thindesk.filters.metadata().then((meta) => {
            const sources = Object.assign({}, meta, { activeTechnicians: meta.technicians.filter((t) => t.active) });
            form.querySelectorAll('select[data-options]').forEach((select) => {
                sources[select.dataset.options].forEach((item) => {
                    const option = document.createElement('option');
                    option.value = item.code || item.id;
                    option.textContent = item.label
                        ? item.label + (item.slaHours ? ' (SLA ' + item.slaHours + ' h)' : '')
                        : item.name;
                    select.appendChild(option);
                });
            });
        });

        $('[data-new-ticket]').addEventListener('click', () => {
            form.reset();
            clearErrors();
            dialog.showModal();
        });
        dialog.querySelectorAll('[data-dialog-close]').forEach((b) => b.addEventListener('click', () => dialog.close()));

        function clearErrors() {
            formError.hidden = true;
            form.querySelectorAll('[data-error-for]').forEach((p) => { p.textContent = ''; });
            form.querySelectorAll('.field--invalid').forEach((f) => f.classList.remove('field--invalid'));
        }

        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            clearErrors();
            const body = Object.fromEntries(new FormData(form).entries());
            Object.keys(body).forEach((key) => { if (body[key] === '') body[key] = null; });
            const submit = form.querySelector('[type="submit"]');
            submit.disabled = true;
            try {
                const created = await api.post('/api/v1/tickets', body);
                window.location.href = '/tickets/' + encodeURIComponent(created.id) + '?created';
            } catch (error) {
                if (error.errors.length) {
                    error.errors.forEach((e) => {
                        const target = form.querySelector('[data-error-for="' + e.field + '"]');
                        if (target) {
                            target.textContent = e.message;
                            target.closest('.field').classList.add('field--invalid');
                        }
                    });
                }
                formError.textContent = error.message;
                formError.hidden = false;
            } finally {
                submit.disabled = false;
            }
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        const sort = $('[data-sort]');
        const initialSort = new URL(window.location.href).searchParams.get('sort');
        if (initialSort && [...sort.options].some((o) => o.value === initialSort)) sort.value = initialSort;
        state.sort = sort.value;
        sort.addEventListener('change', () => {
            state.sort = sort.value;
            state.page = 0;
            load();
        });

        setupDialog();
        const filters = window.Thindesk.filters.init(root.querySelector('[data-filters]'), {
            onChange: (params) => {
                state.params = params;
                state.page = 0;
                load();
            },
        });
        filters.ready.then((params) => {
            state.params = params;
            load();
        });
    });
})();
