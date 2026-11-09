document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-toggle-password]').forEach((button) => {
        const input = document.getElementById(button.dataset.togglePassword);
        if (!input) return;
        button.addEventListener('click', () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.textContent = show ? 'Ocultar' : 'Mostrar';
            button.setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha');
        });
    });

    document.querySelectorAll('form[data-confirm]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });

    document.querySelectorAll('[data-sidebar-toggle]').forEach((button) => {
        button.addEventListener('click', () => document.body.classList.toggle('sidebar-open'));
    });
    document.querySelectorAll('[data-sidebar-close]').forEach((el) => {
        el.addEventListener('click', () => document.body.classList.remove('sidebar-open'));
    });

    const userMenu = document.querySelector('[data-user-menu]');
    if (userMenu) {
        document.addEventListener('click', (event) => {
            if (!userMenu.contains(event.target)) userMenu.removeAttribute('open');
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') userMenu.removeAttribute('open');
        });
    }

    document.querySelectorAll('select[data-autosubmit]').forEach((select) => {
        select.addEventListener('change', () => select.form.submit());
    });
});
