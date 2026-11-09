/*
 * Detalhe do chamado: mostra dados, SLA, considerações técnicas e histórico, e permite atualizar
 * situação, técnico e notas pela API (RF07). Cada alteração recarrega o chamado a partir da resposta.
 */
(function () {
    'use strict';

    const { api, format, escapeHtml, badge } = window.Thindesk;
    const NOTE_REQUIRED = ['RESOLVED', 'CANCELED'];

    const root = document.querySelector('.ticket-page');
    const id = root.dataset.ticketId;
    const $ = (selector) => root.querySelector(selector);
    const field = (name) => root.querySelector('[data-field="' + name + '"]');
    let ticket = null;

    function flash(message) {
        const box = $('[data-flash]');
        box.textContent = message;
        box.hidden = false;
        clearTimeout(flash.timer);
        flash.timer = setTimeout(() => { box.hidden = true; }, 5000);
    }

    function render(t) {
        ticket = t;
        document.title = t.protocol + ' | Thindesk';
        field('protocol').textContent = t.protocol;
        field('title').textContent = t.title;
        field('badges').innerHTML = badge(t.status, 'status') + ' ' + badge(t.priority, 'priority')
            + (t.overdue ? ' <span class="badge badge--danger">Fora do SLA</span>' : '');
        field('description').textContent = t.description;
        field('requester').innerHTML = escapeHtml(t.requesterName) + ' <span class="muted small">&lt;'
            + escapeHtml(t.requesterEmail) + '&gt;</span>';
        field('department').textContent = t.department.label;
        field('type').textContent = t.type.label;
        field('technician').innerHTML = t.technician ? escapeHtml(t.technician.name) : '<span class="muted">Sem técnico</span>';
        field('openedAt').textContent = format.dateTime(t.openedAt);
        field('firstResponseAt').textContent = format.dateTime(t.firstResponseAt);
        field('resolvedAt').textContent = format.dateTime(t.resolvedAt);
        field('closedAt').textContent = format.dateTime(t.closedAt);

        field('due').textContent = format.dateTime(t.dueAt);
        const slaBox = $('[data-sla-box]');
        let slaText;
        if (t.slaMet === true) slaText = 'Resolvido dentro do prazo';
        else if (t.slaMet === false) slaText = 'Resolvido com atraso';
        else if (t.overdue) slaText = 'Atrasado há ' + format.hours((Date.now() - new Date(t.dueAt)) / 3600000);
        else if (t.status.code === 'CANCELED') slaText = 'Cancelado';
        else slaText = 'Restam ' + format.hours((new Date(t.dueAt) - Date.now()) / 3600000) + ' (SLA de ' + t.slaHours + ' h)';
        field('slaState').textContent = slaText;
        slaBox.classList.toggle('sla-box--danger', t.overdue || t.slaMet === false);
        slaBox.classList.toggle('sla-box--success', t.slaMet === true);

        renderTimeline(t);
        renderActions(t);
        $('[data-ticket-header]').hidden = false;
        $('[data-ticket-body]').hidden = false;
    }

    function renderTimeline(t) {
        const events = t.notes.map((n) => ({ at: n.createdAt, kind: 'note', html:
                '<strong>' + escapeHtml(n.author) + '</strong><p class="pre-line">' + escapeHtml(n.text) + '</p>' }))
            .concat(t.history.map((h) => ({ at: h.changedAt, kind: 'status', html:
                '<span>' + escapeHtml(h.changedBy) + ' alterou a situação de ' + badge(h.from, 'status') + ' para '
                + badge(h.to, 'status') + '</span>' })))
            .sort((a, b) => new Date(b.at) - new Date(a.at));
        $('[data-timeline]').innerHTML = events.length === 0
            ? '<li class="muted">Sem registros.</li>'
            : events.map((e) => '<li class="timeline__item timeline__item--' + e.kind + '">'
                + '<time class="muted small">' + format.dateTime(e.at) + '</time>' + e.html + '</li>').join('');
    }

    function renderActions(t) {
        const finalState = t.allowedTransitions.length === 0;
        const statusForm = $('[data-status-form]');
        statusForm.hidden = finalState;
        $('[data-final-hint]').hidden = !finalState;
        $('[data-assign-panel]').hidden = finalState;
        $('[data-note-panel]').hidden = finalState;

        const select = statusForm.elements.status;
        select.innerHTML = t.allowedTransitions.map((o) => '<option value="' + o.code + '">' + escapeHtml(o.label) + '</option>').join('');
        updateNoteHint();

        const assign = $('[data-assign-form]').elements.technicianId;
        if (t.technician) assign.value = t.technician.id;
    }

    function updateNoteHint() {
        const status = $('[data-status-form]').elements.status.value;
        const required = NOTE_REQUIRED.includes(status);
        $('[data-note-hint]').textContent = required
            ? (status === 'RESOLVED' ? 'Obrigatória: descreva a solução aplicada.' : 'Obrigatória: informe o motivo do cancelamento.')
            : 'Opcional. Obrigatória para resolver ou cancelar.';
        $('[data-status-form]').elements.note.required = required;
    }

    function bind(selector, send, successMessage) {
        const form = $(selector);
        const error = form.querySelector('[data-form-error]');
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            error.textContent = '';
            const button = form.querySelector('[type="submit"]');
            button.disabled = true;
            try {
                const data = Object.fromEntries(new FormData(form).entries());
                await send(data);
                form.querySelectorAll('textarea').forEach((t) => { t.value = ''; });
                render(await api.get('/api/v1/tickets/' + encodeURIComponent(id)));
                flash(successMessage);
            } catch (e) {
                error.textContent = e.errors.length ? e.errors.map((x) => x.message).join(' ') : e.message;
            } finally {
                button.disabled = false;
            }
        });
    }

    async function init() {
        try {
            const [t, meta] = await Promise.all([
                api.get('/api/v1/tickets/' + encodeURIComponent(id)),
                window.Thindesk.filters.metadata(),
            ]);
            const assign = $('[data-assign-form]').elements.technicianId;
            meta.technicians.filter((x) => x.active).forEach((x) => {
                const option = document.createElement('option');
                option.value = x.id;
                option.textContent = x.name + ' (' + x.level + ')';
                assign.appendChild(option);
            });
            render(t);
            if (new URL(window.location.href).searchParams.has('created')) flash('Chamado ' + t.protocol + ' aberto com sucesso.');
        } catch (e) {
            const box = $('[data-load-error]');
            box.textContent = e.status === 404 ? 'Chamado não encontrado.' : e.message;
            box.hidden = false;
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        $('[data-status-form]').elements.status.addEventListener('change', updateNoteHint);
        bind('[data-status-form]', (d) => api.patch('/api/v1/tickets/' + encodeURIComponent(id) + '/status',
            { status: d.status, note: d.note || null }), 'Situação atualizada.');
        bind('[data-assign-form]', (d) => api.patch('/api/v1/tickets/' + encodeURIComponent(id) + '/assignment',
            { technicianId: d.technicianId }), 'Técnico atribuído.');
        bind('[data-note-form]', (d) => api.post('/api/v1/tickets/' + encodeURIComponent(id) + '/notes',
            { text: d.text }), 'Consideração técnica registrada.');
        init();
    });
})();
