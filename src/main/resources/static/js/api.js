/*
 * Cliente da API REST do Thindesk (RF06) e utilitários de formatação.
 * Todas as telas do painel buscam seus dados por aqui: a sessão do usuário logado autentica as chamadas
 * e o token CSRF da página é enviado nas requisições que alteram dados.
 */
(function () {
    'use strict';

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    class ApiError extends Error {
        constructor(status, problem) {
            super(problem?.detail || problem?.title || 'Erro ao consultar a API');
            this.status = status;
            this.problem = problem || {};
            this.errors = problem?.errors || [];
        }
    }

    function buildUrl(path, params) {
        const url = new URL(path, window.location.origin);
        Object.entries(params || {}).forEach(([key, value]) => {
            if (value === null || value === undefined || value === '' || value === false) return;
            if (Array.isArray(value)) value.forEach((v) => url.searchParams.append(key, v));
            else url.searchParams.set(key, value);
        });
        return url;
    }

    async function request(method, path, { params, body } = {}) {
        const headers = { Accept: 'application/json' };
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        if (method !== 'GET' && csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

        let response;
        try {
            response = await fetch(buildUrl(path, params), {
                method,
                headers,
                credentials: 'same-origin',
                body: body === undefined ? undefined : JSON.stringify(body),
            });
        } catch (e) {
            throw new ApiError(0, {
                title: 'Sem conexão',
                detail: 'Não foi possível falar com o servidor. Verifique a conexão e tente novamente.',
            });
        }

        if (response.status === 401) {
            window.location.href = '/login?expired';
            throw new ApiError(401, { detail: 'Sessão expirada. Faça login novamente.' });
        }

        const text = await response.text();
        let data = null;
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = { detail: 'Resposta inesperada do servidor (HTTP ' + response.status + ').' };
            }
        }
        if (!response.ok) throw new ApiError(response.status, data);
        return data;
    }

    const numberFormat = new Intl.NumberFormat('pt-BR');
    const decimalFormat = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    const dateTimeFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
    const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });
    const timeFormat = new Intl.DateTimeFormat('pt-BR', { timeStyle: 'medium' });

    const format = {
        number: (v) => (v === null || v === undefined ? '-' : numberFormat.format(v)),
        percent: (v) => (v === null || v === undefined ? '-' : decimalFormat.format(v) + '%'),
        /** Duração em horas, exibida em minutos, horas ou dias conforme o tamanho. */
        hours(v) {
            if (v === null || v === undefined) return '-';
            if (v < 1) return Math.max(1, Math.round(v * 60)) + ' min';
            if (v < 48) return decimalFormat.format(v) + ' h';
            return decimalFormat.format(v / 24) + ' dias';
        },
        dateTime: (iso) => (iso ? dateTimeFormat.format(new Date(iso)) : '-'),
        date: (iso) => (iso ? dateFormat.format(new Date(iso + (iso.length === 10 ? 'T12:00:00' : ''))) : '-'),
        time: (date) => timeFormat.format(date),
        change(v) {
            if (v === null || v === undefined) return '';
            const arrow = v > 0 ? '▲' : v < 0 ? '▼' : '■';
            return arrow + ' ' + decimalFormat.format(Math.abs(v)) + '%';
        },
    };

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, (c) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
        }[c]));
    }

    function badge(option, kind) {
        if (!option) return '<span class="muted">-</span>';
        const css = kind + '-' + option.code.toLowerCase().replace(/_/g, '-');
        return '<span class="badge badge--' + css + '">' + escapeHtml(option.label) + '</span>';
    }

    /** Lê uma variável CSS do tema ativo (usada nas cores dos gráficos). */
    function cssVar(name, fallback) {
        const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return value || fallback;
    }

    function localIsoDate(date) {
        const offset = date.getTimezoneOffset() * 60000;
        return new Date(date.getTime() - offset).toISOString().slice(0, 10);
    }

    window.Thindesk = {
        api: {
            get: (path, params) => request('GET', path, { params }),
            post: (path, body) => request('POST', path, { body }),
            patch: (path, body) => request('PATCH', path, { body }),
        },
        ApiError,
        format,
        escapeHtml,
        badge,
        cssVar,
        localIsoDate,
    };
})();
