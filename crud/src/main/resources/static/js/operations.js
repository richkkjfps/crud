'use strict';

(() => {
    let stockProduct = null;
    let requestId = null;
    let saving = false;
    const format = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const element = (id) => document.getElementById(id);

    function estimate(cost, price, target) {
        const output = element(target);
        if (cost === '' || price === '') {
            output.textContent = 'Informe custo e venda para calcular o lucro bruto por unidade.';
            return;
        }
        const sale = Number(price),
            profit = sale - Number(cost);
        output.textContent =
            'Lucro bruto por unidade: ' +
            format.format(profit) +
            (sale > 0
                ? ' · margem sobre a venda: ' +
                  ((profit / sale) * 100).toFixed(1).replace('.', ',') +
                  '%'
                : '');
        output.classList.toggle('negative-profit', profit < 0);
    }

    function productProfit() {
        estimate(element('cost-price').value, element('price').value, 'product-profit');
    }

    function restock(product) {
        if (saving) return;
        stockProduct = product;
        requestId = crypto.randomUUID();
        element('stock-form').reset();
        element('stock-product').textContent =
            product.name + ' · estoque atual: ' + product.quantity;
        element('stock-cost').value = product.costPrice ?? '';
        element('stock-cost').required = me?.role !== 'USER';
        element('stock-cost').disabled = me?.role === 'USER';
        element('stock-price').disabled = me?.role === 'USER';
        element('stock-price').value = product.price;
        element('stock-error').textContent = '';
        estimate(element('stock-cost').value, element('stock-price').value, 'stock-profit');
        element('stock-editor').showModal();
        element('stock-quantity').focus();
    }

    element('stock-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (saving || !stockProduct) return;
        saving = true;
        element('stock-save').disabled = true;
        element('stock-close').disabled = true;
        element('stock-error').textContent = '';
        try {
            await api('/api/products/' + stockProduct.id + '/restock', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    quantity: Number(element('stock-quantity').value),
                    costPrice: me?.role === 'USER' ? null : Number(element('stock-cost').value),
                    price: Number(element('stock-price').value),
                    requestId
                })
            });
            element('stock-editor').close();
            await refresh();
            await window.StockifyCash.reload();
            notice('Estoque reposto. Custo e preço de venda atualizados.');
        } catch (error) {
            element('stock-error').textContent = error.message;
        } finally {
            saving = false;
            element('stock-save').disabled = false;
            element('stock-close').disabled = false;
        }
    });
    element('stock-close').addEventListener('click', () => {
        if (!saving) element('stock-editor').close();
    });
    element('stock-editor').addEventListener('cancel', (event) => {
        if (saving) event.preventDefault();
    });
    for (const id of ['cost-price', 'price']) element(id).addEventListener('input', productProfit);
    for (const id of ['stock-cost', 'stock-price'])
        element(id).addEventListener('input', () =>
            estimate(element('stock-cost').value, element('stock-price').value, 'stock-profit')
        );
    element('editor').addEventListener('toggle', productProfit);

    async function startSale() {
        if (!me || saving || window.StockifyCommerce.pending() || busy || loading) return;
        if (document.querySelector('dialog[open]')) return;
        if (await window.StockifyCommerce.show('sales')) window.StockifyCommerce.newSale();
    }

    element('quick-sale').addEventListener('click', startSale);
    element('shortcuts-button').addEventListener('click', () => {
        if (!document.querySelector('dialog[open]')) element('shortcuts-dialog').showModal();
    });
    element('shortcuts-close').addEventListener('click', () => element('shortcuts-dialog').close());
    document.addEventListener('keydown', async (event) => {
        if (!me || event.repeat || event.ctrlKey || event.altKey || event.metaKey) return;
        const saleOpen = element('sale-editor').open;
        const modal = document.querySelector('dialog[open]');
        if (
            event.key === 'Enter' &&
            saleOpen &&
            event.target.type !== 'search' &&
            event.target.tagName !== 'TEXTAREA'
        ) {
            if (!window.StockifyCommerce.readyCheckout()) {
                event.preventDefault();
                window.StockifyCommerce.checkout();
            }
            return;
        }
        if (!['F1', 'F2', 'F3', 'F4', 'F5', 'F6', 'F8', 'F9'].includes(event.key)) return;
        event.preventDefault();
        if (saving || window.StockifyCommerce.pending() || busy || loading) return;
        try {
            if (event.key === 'F6' && saleOpen) {
                window.StockifyCommerce.checkout();
                return;
            }
            if (event.key === 'F8' && saleOpen && window.StockifyCommerce.readyCheckout()) {
                element('sale-form').requestSubmit();
                return;
            }
            if (event.key === 'F5') {
                const search = saleOpen
                    ? element('sale-items').querySelector('input[type=search]')
                    : element(window.StockifyCommerce.active() ? 'commerce-search' : 'search');
                search?.focus();
                return;
            }
            if (modal) return;
            if (event.key === 'F1') await startSale();
            if (event.key === 'F2') document.querySelector('[data-view=products]').click();
            if (event.key === 'F3') await window.StockifyCommerce.show('customers');
            if (event.key === 'F4') await window.StockifyCommerce.show('suppliers');
            if (event.key === 'F9') element('shortcuts-dialog').showModal();
        } catch (error) {
            notice(error.message, true);
        }
    });
    window.StockifyOperations = { restock, productProfit };
})();
