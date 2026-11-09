/*
 * Barra de filtros (RF05). Carrega as opções da API, sincroniza os filtros com a URL (o link pode ser
 * compartilhado e sobrevive ao recarregar a página) e avisa a tela quando eles mudam.
 */
(function () {
    'use strict';

    const { api, localIsoDate } = window.Thindesk;
    const DEFAULT_DAYS = 30;
    const KEYS = ['from', 'to', 'status', 'type', 'priority', 'department', 'technicianId', 'q', 'overdue'];

    let metadataPromise = null;

    function metadata() {
        if (!metadataPromise) {
            metadataPromise = api.get('/api/v1/dashboard/filters').catch((error) => {
                metadataPromise = null;
                throw error;
            });
        }
        return metadataPromise;
    }

    function fillOptions(form, meta) {
        form.querySelectorAll('select[data-options]').forEach((select) => {
            const source = meta[select.dataset.options] || [];
            source.forEach((item) => {
                const option = document.createElement('option');
                option.value = item.code || item.id;
                option.textContent = item.label || item.name + (item.active === false ? ' (inativo)' : '');
                select.appendChild(option);
            });
        });
    }

    function presetRange(days) {
        const to = new Date();
        const from = new Date();
        from.setDate(to.getDate() - (days - 1));
        return { from: localIsoDate(from), to: localIsoDate(to) };
    }

    function init(form, { onChange, extraKeys = [] }) {
        const errorBox = form.querySelector('[data-filters-error]');
        const allKeys = KEYS.concat(extraKeys);

        function read() {
            const data = new FormData(form);
            const params = {};
            KEYS.forEach((key) => {
                const value = data.get(key);
                if (value !== null && value !== '') params[key] = value;
            });
            return params;
        }

        function writeUrl(params, all) {
            const url = new URL(window.location.href);
            allKeys.concat('period').forEach((key) => {
                if (!extraKeys.includes(key)) url.searchParams.delete(key);
            });
            Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));
            if (all) url.searchParams.set('period', 'all');
            window.history.replaceState(null, '', url);
        }

        function markPreset() {
            const { from, to } = { from: form.elements.from.value, to: form.elements.to.value };
            form.querySelectorAll('[data-preset]').forEach((chip) => {
                const preset = chip.dataset.preset;
                const active = preset === 'all'
                    ? !from && !to
                    : (() => { const r = presetRange(Number(preset)); return r.from === from && r.to === to; })();
                chip.classList.toggle('is-active', active);
            });
        }

        function validate(params) {
            if (params.from && params.to && params.from > params.to) {
                return 'A data inicial deve ser anterior ou igual à data final.';
            }
            return null;
        }

        function apply() {
            const params = read();
            const error = validate(params);
            errorBox.hidden = !error;
            errorBox.textContent = error || '';
            if (error) return;
            writeUrl(params, !params.from && !params.to);
            markPreset();
            onChange(params);
        }

        function setRange(range) {
            form.elements.from.value = range.from || '';
            form.elements.to.value = range.to || '';
        }

        function loadFromUrl() {
            const url = new URL(window.location.href);
            KEYS.forEach((key) => {
                const field = form.elements[key];
                const value = url.searchParams.get(key);
                if (!field || value === null) return;
                if (field.type === 'checkbox') field.checked = value === 'true';
                else field.value = value;
            });
            if (!url.searchParams.has('from') && !url.searchParams.has('to') && url.searchParams.get('period') !== 'all') {
                setRange(presetRange(DEFAULT_DAYS));
            }
        }

        form.addEventListener('submit', (event) => {
            event.preventDefault();
            apply();
        });

        form.querySelectorAll('[data-preset]').forEach((chip) => {
            chip.addEventListener('click', () => {
                const preset = chip.dataset.preset;
                setRange(preset === 'all' ? {} : presetRange(Number(preset)));
                apply();
            });
        });

        form.querySelector('[data-filters-clear]').addEventListener('click', () => {
            form.reset();
            setRange(presetRange(DEFAULT_DAYS));
            apply();
        });

        const ready = metadata()
            .then((meta) => fillOptions(form, meta))
            .catch(() => {
                errorBox.hidden = false;
                errorBox.textContent = 'Não foi possível carregar as opções dos filtros.';
            })
            .then(() => {
                loadFromUrl();
                markPreset();
                return read();
            });

        return { ready, read, apply };
    }

    window.Thindesk.filters = { init, metadata };
})();
