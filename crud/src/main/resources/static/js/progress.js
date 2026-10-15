'use strict';
(() => {
    const panel = document.getElementById('store-journey');
    if (!panel) return;
    const keys = ['products', 'customers', 'sales'];
    const snapshots = Object.fromEntries(keys.map(key => [key, null]));
    let initialized = false, refreshing = false, lastCount = null;
    const revisions = Object.fromEntries(keys.map(key => [key, 0]));
    const byId = id => document.getElementById(id);
    function render() {
        const known = keys.filter(key => snapshots[key] !== null);
        const completed = keys.filter(key => snapshots[key]?.length > 0);
        for (const [index, key] of keys.entries()) {
            const row = panel.querySelector('[data-step="' + key + '"]');
            const done = completed.includes(key);
            row.classList.toggle('is-complete', done);
            row.querySelector('.step-mark').textContent = done ? '✓' : String(index + 1);
            row.querySelector('.step-state').textContent = snapshots[key] === null
                ? 'Dados indisponíveis' : done ? 'Concluído' : 'Próximo passo';
        }
        byId('journey-count').textContent = completed.length + ' de 3';
        byId('journey-progress').value = completed.length;
        byId('journey-progress').setAttribute('aria-valuetext', completed.length + ' de 3 etapas confirmadas' + (known.length < 3 ? '; há dados indisponíveis' : ''));
        byId('journey-summary').textContent = known.length < 3
            ? 'Alguns dados estão indisponíveis. Você pode tentar atualizar.'
            : completed.length === 3 ? 'Tudo pronto. Continue cuidando da sua loja.'
            : 'Pequenos passos para deixar sua gestão em dia.';
        if (known.length === 3) {
            if (lastCount !== null && completed.length > lastCount)
                byId('journey-feedback').textContent = 'Boa! Mais uma etapa concluída. Sua loja está ficando organizada.';
            else if (lastCount !== completed.length) byId('journey-feedback').textContent = '';
            lastCount = completed.length;
        }
    }
    function update(values) {
        for (const key of keys) if (Array.isArray(values[key])) {
            snapshots[key] = values[key];
            revisions[key]++;
        }
        if (initialized) render();
    }
    async function refresh() {
        if (refreshing || !me) return;
        refreshing = true;
        byId('journey-refresh').disabled = true;
        byId('journey-refresh').textContent = 'Atualizando…';
        const before = {...revisions};
        try {
            const results = await Promise.allSettled(keys.map(key => api(key === 'products' ? '/products' : '/api/' + key)));
            results.forEach((result, index) => {
                const key = keys[index];
                if (before[key] !== revisions[key]) return;
                snapshots[key] = result.status === 'fulfilled' && Array.isArray(result.value) ? result.value : null;
            });
            render();
        } finally {
            refreshing = false;
            byId('journey-refresh').disabled = false;
            byId('journey-refresh').textContent = 'Atualizar progresso';
        }
    }
    function init() {
        if (initialized) return;
        initialized = true;
        panel.hidden = false;
        render();
        refresh();
    }
    panel.querySelectorAll('[data-journey-action]').forEach(button => button.addEventListener('click', async () => {
        if (!me || busy || loading || window.StockifyCommerce?.pending() || document.querySelector('dialog[open]')) return;
        const key = button.dataset.journeyAction;
        if (key === 'sales') byId('quick-sale').click();
        else if (key === 'customers') {
            await window.StockifyCommerce.show('customers');
            byId('commerce-search').focus();
        } else {
            document.querySelector('[data-view="products"]').click();
            byId('search').focus();
        }
    }));
    byId('journey-refresh').addEventListener('click', refresh);
    byId('journey-details').addEventListener('toggle', event => {
        panel.querySelector('.journey-expand').textContent = event.target.open ? 'Recolher etapas' : 'Ver etapas';
    });
    window.StockifyProgress = {init, update};
})();
