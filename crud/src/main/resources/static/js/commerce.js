'use strict';

// Cadastros e vendas da empresa conectada. A API aplica a separação dos dados.
(() => {
    const element = (id) => document.getElementById(id);
    const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const settings = {
        customers: {
            title: 'Clientes',
            description: 'Compradores da sua loja e seus saldos devedores.',
            action: '+ Novo cliente'
        },
        suppliers: {
            title: 'Fornecedores',
            description: 'Contatos, entregas e tudo o que cada fornecedor oferece.',
            action: '+ Novo fornecedor'
        },
        sales: {
            title: 'Vendas',
            description: 'Vendas à vista e no fiado, com baixa automática do estoque.',
            action: '+ Nova venda'
        },
        receivables: {
            title: 'Contas a receber',
            description: 'Acompanhe vencimentos e registre pagamentos parciais ou totais.',
            action: '+ Nova venda'
        }
    };
    let current = null;
    let records = { customers: [], suppliers: [], sales: [], products: [] };
    let pending = false;
    let checkoutOpen = false;
    let profits = null;
    let contactId = null;
    let contactKind = null;
    let saleRequestId = null;
    let paymentRequestId = null;
    let paymentSaleId = null;
    let loadingVersion = 0;

    function node(tag, text, className) {
        const result = document.createElement(tag);
        if (text !== undefined && text !== null) result.textContent = text;
        if (className) result.className = className;
        return result;
    }

    function notify(message, error = false) {
        element('commerce-notice').textContent = message;
        element('commerce-notice').className = error ? 'commerce-error' : '';
    }

    function action(label, handler) {
        const button = node('button', label, 'secondary');
        button.type = 'button';
        button.disabled = pending;
        button.addEventListener('click', async () => {
            try {
                await handler();
            } catch (error) {
                notify(error.message, true);
            }
        });
        return button;
    }

    function date(value) {
        if (!value) return 'Sem vencimento';
        return value.split('-').reverse().join('/');
    }

    function status(sale) {
        return { PAID: 'Quitado', OPEN: 'Em aberto', OVERDUE: 'Vencido' }[sale.status];
    }

    function row(values, headings, actions) {
        const result = node('tr');
        values.forEach((value, index) => {
            const cell = node('td', value);
            cell.dataset.label = headings[index];
            result.append(cell);
        });
        const buttons = node('td', null, 'actions');
        buttons.dataset.label = 'Ações';
        buttons.append(...actions);
        result.append(buttons);
        return result;
    }

    function render() {
        if (!current) return;
        const config = settings[current];
        element('sales-focus').hidden = current !== 'sales';
        element('profit-summary').hidden = current !== 'sales' || me.role === 'USER' || !profits;
        if (profits) {
            element('profit-revenue').textContent = money.format(profits.revenue);
            element('profit-cost').textContent = money.format(profits.knownCost);
            element('profit-gross').textContent = money.format(profits.grossProfit);
            element('profit-warning').textContent =
                'Lucro bruto antes de despesas e impostos; inclui vendas no fiado.' +
                (Number(profits.unknownCostItems)
                    ? ' Há ' +
                      profits.unknownCostItems +
                      ' itens de vendas sem custo histórico, excluídos do lucro conhecido.'
                    : '');
        }
        element('page-title').textContent = config.title;
        element('page-description').textContent = config.description;
        element('commerce-title').textContent = config.title;
        element('commerce-description').textContent = config.description;
        element('commerce-new').textContent = config.action;
        document.querySelectorAll('[data-commerce]').forEach((button) => {
            button.classList.toggle('active', button.dataset.commerce === current);
        });
        document
            .querySelectorAll('[data-view]')
            .forEach((button) => button.classList.remove('active'));
        const isContact = current === 'customers' || current === 'suppliers';
        const supplier = current === 'suppliers';
        const headings = isContact
            ? supplier
                ? ['Fornecedor', 'Telefone', 'O que fornece', 'Ações']
                : ['Cliente', 'Telefone', 'Saldo devedor', 'Ações']
            : [
                  'Venda',
                  'Cliente',
                  'Data',
                  'Vencimento',
                  'Total',
                  'Recebido',
                  'Saldo',
                  'Situação',
                  'Ações'
              ];
        const header = node('tr');
        headings.forEach((heading) => header.append(node('th', heading)));
        element('commerce-head').replaceChildren(header);
        const term = element('commerce-search').value.trim().toLocaleLowerCase('pt-BR');
        const source = isContact ? records[current] : records.sales;
        const list = source.filter((item) => {
            if (current === 'receivables' && Number(item.balance) <= 0) return false;
            const fields = isContact
                ? [item.name, item.phone, item.email, item.suppliedItems, item.notes]
                : [item.id, item.customerName, status(item), item.notes];
            return fields.some((value) =>
                String(value ?? '')
                    .toLocaleLowerCase('pt-BR')
                    .includes(term)
            );
        });
        const rows = list.map((item) => {
            if (isContact) {
                const values = [
                    item.name,
                    item.phone || '—',
                    supplier ? item.suppliedItems || 'Não informado' : money.format(item.balance)
                ];
                const detail = supplier ? () => showSupplier(item) : () => showCustomer(item);
                return row(values, headings, [
                    action(supplier ? 'Ver fornecedor' : 'Extrato', detail),
                    action('Editar', () => editContact(item))
                ]);
            }
            return row(
                [
                    '#' + item.id,
                    item.customerName || 'Venda sem cliente',
                    date(item.saleDate),
                    date(item.dueDate),
                    money.format(item.total),
                    money.format(item.paid),
                    money.format(item.balance),
                    status(item)
                ],
                headings,
                [
                    action(Number(item.balance) > 0 ? 'Ver / receber' : 'Detalhes', () =>
                        showSale(item.id)
                    )
                ]
            );
        });
        element('commerce-rows').replaceChildren(...rows);
        element('commerce-empty').hidden = list.length > 0;
        element('commerce-count').textContent = list.length + ' registros';
    }

    async function load() {
        const version = ++loadingVersion;
        notify('Atualizando…');
        element('commerce-new').disabled = true;
        try {
            const [customers, suppliers, sales, products, report] = await Promise.all([
                api('/api/customers'),
                api('/api/suppliers'),
                api('/api/sales'),
                api('/products'),
                me.role !== 'USER' ? api('/api/profits') : Promise.resolve(null)
            ]);
            if (version !== loadingVersion) return;
            records = { customers, suppliers, sales, products };
            profits = report;
            notify('');
            render();
        } catch (error) {
            notify(error.message, true);
        } finally {
            if (version === loadingVersion) element('commerce-new').disabled = false;
        }
    }

    async function show(kind) {
        if (pending || !me) return;
        if (kind === 'sales') {
            try {
                await window.StockifyCash.reload();
                if (!window.StockifyCash.ensureOpen()) return false;
            } catch (error) {
                notice(error.message, true);
                return false;
            }
        }
        current = kind;
        element('catalog-panel').hidden = true;
        element('commerce-panel').hidden = false;
        element('new-entry').hidden = true;
        element('commerce-search').value = '';
        render();
        await load();
        return true;
    }

    function hide() {
        if (pending) return false;
        current = null;
        loadingVersion++;
        element('catalog-panel').hidden = false;
        element('commerce-panel').hidden = true;
        element('new-entry').hidden = false;
        document
            .querySelectorAll('[data-commerce]')
            .forEach((button) => button.classList.remove('active'));
        return true;
    }

    function setPending(value) {
        pending = value;
        document
            .querySelectorAll(
                '[data-commerce-close], #contact-save, #sale-save, #payment-save, #sale-add-item, #commerce-rows button, #ledger-content button, .nav, #commerce-new, #commerce-refresh'
            )
            .forEach((button) => (button.disabled = value));
    }

    function editContact(contact = null) {
        contactKind = current === 'suppliers' ? 'suppliers' : 'customers';
        contactId = contact?.id ?? null;
        element('contact-form').reset();
        element('contact-error').textContent = '';
        element('supplier-fields').hidden = contactKind !== 'suppliers';
        element('contact-title').textContent =
            (contact ? 'Editar ' : 'Cadastrar ') +
            (contactKind === 'suppliers' ? 'fornecedor' : 'cliente');
        for (const field of ['name', 'phone', 'email', 'address', 'notes'])
            element('contact-' + field).value = contact?.[field] || '';
        element('contact-supplied').value = contact?.suppliedItems || '';
        element('contact-editor').showModal();
        element('contact-name').focus();
    }

    function addSaleItem() {
        if (element('sale-items').children.length >= 100) return;
        const line = node('div', null, 'sale-item');
        line.dataset.productId = '';
        const picker = node('div', null, 'product-picker');
        const search = node('input');
        search.type = 'search';
        search.placeholder = 'Nome, código de barras ou SKU';
        search.setAttribute('aria-label', 'Pesquisar produto');
        const results = node('div', null, 'product-results');
        const info = node('small', 'Escolha um produto da sua loja.', 'product-selected');
        const quantity = node('input');
        quantity.type = 'number';
        quantity.min = '1';
        quantity.max = '2147483647';
        quantity.step = '1';
        quantity.value = '1';
        quantity.required = true;
        quantity.setAttribute('aria-label', 'Quantidade da venda');
        function choose(product) {
            line.dataset.productId = product.id;
            search.value = product.name;
            results.replaceChildren();
            info.textContent =
                money.format(product.price) + ' por unidade · estoque ' + product.quantity;
            updateTotal();
            quantity.focus();
            quantity.select();
        }
        function matches() {
            const term = search.value.trim().toLocaleLowerCase('pt-BR');
            return records.products
                .filter((p) =>
                    [p.name, p.barcode, p.sku].some((v) =>
                        String(v || '')
                            .toLocaleLowerCase('pt-BR')
                            .includes(term)
                    )
                )
                .slice(0, 12);
        }
        function suggest() {
            results.replaceChildren();
            if (!search.value.trim()) return;
            const products = matches();
            if (!products.length) results.append(node('p', 'Nenhum produto encontrado.'));
            products.forEach((p) => {
                const button = action(
                    p.name + ' · ' + money.format(p.price) + ' · estoque ' + p.quantity,
                    () => choose(p)
                );
                button.disabled = Number(p.quantity) <= 0;
                button.title = Number(p.quantity) <= 0 ? 'Sem estoque: faça uma reposição.' : '';
                results.append(button);
            });
        }
        search.addEventListener('input', () => {
            line.dataset.productId = '';
            info.textContent = 'Escolha um resultado da busca.';
            suggest();
            updateTotal();
        });
        search.addEventListener('focus', suggest);
        search.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                event.stopPropagation();
                const first = matches().find((p) => Number(p.quantity) > 0);
                if (first) choose(first);
            }
        });
        quantity.addEventListener('input', updateTotal);
        picker.append(search, info, results);
        const remove = action('Remover', () => {
            line.remove();
            updateTotal();
        });
        line.append(picker, quantity, remove);
        element('sale-items').append(line);
        updateTotal();
        search.focus();
    }
    function updateTotal() {
        let cents = 0;
        for (const line of element('sale-items').children) {
            const p = records.products.find((item) => String(item.id) === line.dataset.productId);
            cents +=
                Math.round(Number(p?.price || 0) * 100) *
                Number(line.querySelector('input[type=number]').value || 0);
        }
        element('sale-total').textContent = money.format(cents / 100);
    }
    function checkout() {
        const lines = [...element('sale-items').children];
        if (
            !lines.length ||
            lines.some(
                (line) =>
                    !line.dataset.productId ||
                    Number(line.querySelector('input[type=number]').value) <= 0
            )
        ) {
            element('sale-error').textContent =
                'Escolha os produtos e informe suas quantidades antes de finalizar.';
            return;
        }
        checkoutOpen = true;
        element('sale-error').textContent = '';
        element('sale-checkout-fields').hidden = false;
        element('sale-save').hidden = false;
        element('sale-checkout').hidden = true;
        element('sale-type').focus();
    }
    function changeSaleType() {
        const credit = element('sale-type').value === 'CREDIT';
        element('sale-customer').required = credit;
        element('sale-due-field').hidden = !credit;
        element('sale-initial-field').hidden = !credit;
    }

    function newSale() {
        if (!window.StockifyCash.ensureOpen()) return;
        if (!records.products.length) {
            notify('Cadastre um produto e informe o estoque antes de vender.', true);
            return;
        }
        element('sale-form').reset();
        element('sale-error').textContent = '';
        saleRequestId = crypto.randomUUID();
        checkoutOpen = false;
        element('sale-checkout-fields').hidden = true;
        element('sale-save').hidden = true;
        element('sale-checkout').hidden = false;
        const blank = node('option', 'Sem cliente (somente à vista)');
        blank.value = '';
        element('sale-customer').replaceChildren(blank);
        records.customers.forEach((customer) => {
            const option = node('option', customer.name);
            option.value = customer.id;
            element('sale-customer').append(option);
        });
        element('sale-items').replaceChildren();
        changeSaleType();
        element('sale-editor').showModal();
        addSaleItem();
    }

    function openLedger(title, content) {
        element('ledger-title').textContent = title;
        element('ledger-content').replaceChildren(...content);
        element('payment-form').hidden = true;
        element('payment-error').textContent = '';
        if (!element('ledger-dialog').open) element('ledger-dialog').showModal();
    }

    function contactInfo(contact) {
        const blocks = [];
        for (const [label, value] of [
            ['Telefone', contact.phone],
            ['E-mail', contact.email],
            ['Endereço', contact.address],
            ['Observações', contact.notes]
        ]) {
            if (value) {
                blocks.push(node('h3', label), node('p', value, 'ledger-note'));
            }
        }
        return blocks;
    }

    function showSupplier(supplier) {
        openLedger(supplier.name, [
            node('h3', 'O que fornece'),
            node('p', supplier.suppliedItems || 'Nenhum item anotado.', 'ledger-note'),
            ...contactInfo(supplier)
        ]);
    }

    async function showCustomer(customer) {
        const sales = await api('/api/customers/' + customer.id + '/sales');
        const balance = sales.reduce(
            (sum, sale) => sum + Math.round(Number(sale.balance) * 100),
            0
        );
        const content = [
            node('p', 'Saldo devedor: ' + money.format(balance / 100), 'ledger-balance'),
            ...contactInfo(customer),
            node('h3', 'Compras e pagamentos')
        ];
        if (!sales.length) content.push(node('p', 'Nenhuma venda registrada.'));
        sales.forEach((sale) => {
            const card = node('article', null, 'ledger-card');
            card.append(
                node('p', 'Venda #' + sale.id + ' · ' + date(sale.saleDate) + ' · ' + status(sale)),
                node(
                    'p',
                    'Compra: ' +
                        money.format(sale.total) +
                        ' · Recebido: ' +
                        money.format(sale.paid) +
                        ' · Saldo: ' +
                        money.format(sale.balance)
                ),
                action('Ver compra e pagamentos', () => showSale(sale.id))
            );
            content.push(card);
        });
        openLedger('Extrato · ' + customer.name, content);
    }

    async function showSale(id) {
        const details = await api('/api/sales/' + id);
        displaySale(details);
    }

    function displaySale(details) {
        const { sale, items, payments } = details;
        const content = [
            node('p', (sale.customerName || 'Venda sem cliente') + ' · ' + date(sale.saleDate)),
            node(
                'p',
                (sale.paymentType === 'CREDIT' ? 'Fiado / a prazo' : 'À vista') +
                    ' · ' +
                    status(sale)
            ),
            node(
                'p',
                'Total: ' +
                    money.format(sale.total) +
                    ' · Recebido: ' +
                    money.format(sale.paid) +
                    ' · Saldo: ' +
                    money.format(sale.balance),
                'ledger-balance'
            )
        ];
        if (sale.dueDate) content.push(node('p', 'Vencimento: ' + date(sale.dueDate)));
        if (sale.notes) content.push(node('p', sale.notes, 'ledger-note'));
        content.push(node('h3', 'Produtos'));
        items.forEach((item) =>
            content.push(
                node(
                    'p',
                    item.quantity +
                        ' × ' +
                        item.productName +
                        ' · ' +
                        money.format(item.unitPrice) +
                        ' cada'
                )
            )
        );
        content.push(node('h3', 'Pagamentos recebidos'));
        if (!payments.length) content.push(node('p', 'Nenhum pagamento recebido.'));
        payments.forEach((payment) =>
            content.push(
                node(
                    'p',
                    date(payment.paymentDate) +
                        ' · ' +
                        money.format(payment.amount) +
                        (payment.notes ? ' · ' + payment.notes : ''),
                    'ledger-note'
                )
            )
        );
        openLedger('Venda #' + sale.id, content);
        paymentSaleId = sale.id;
        paymentRequestId = crypto.randomUUID();
        element('payment-form').reset();
        element('payment-form').hidden = Number(sale.balance) <= 0;
        element('payment-amount').max = sale.balance;
    }

    element('contact-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (pending) return;
        const payload = {};
        for (const field of ['name', 'phone', 'email', 'address', 'notes'])
            payload[field] = element('contact-' + field).value.trim();
        payload.suppliedItems = element('contact-supplied').value.trim();
        setPending(true);
        element('contact-error').textContent = '';
        try {
            await api('/api/' + contactKind + (contactId ? '/' + contactId : ''), {
                method: contactId ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            element('contact-editor').close();
            await load();
            notify('Cadastro salvo.');
        } catch (error) {
            element('contact-error').textContent = error.message;
        } finally {
            setPending(false);
        }
    });

    element('sale-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (pending) return;
        if (!checkoutOpen) {
            checkout();
            return;
        }
        if ([...element('sale-items').children].some((line) => !line.dataset.productId)) {
            element('sale-error').textContent = 'Selecione cada produto nos resultados da busca.';
            return;
        }
        const items = [...element('sale-items').children].map((line) => ({
            productId: Number(line.dataset.productId),
            quantity: Number(line.querySelector('input[type=number]').value)
        }));
        if (!items.length) {
            element('sale-error').textContent = 'Adicione um produto à venda.';
            return;
        }
        const credit = element('sale-type').value === 'CREDIT';
        const payload = {
            customerId: element('sale-customer').value
                ? Number(element('sale-customer').value)
                : null,
            paymentType: element('sale-type').value,
            paymentMethod: element('sale-method').value,
            dueDate: credit ? element('sale-due').value || null : null,
            initialPayment: credit ? Number(element('sale-initial').value || 0) : 0,
            notes: element('sale-notes').value.trim(),
            requestId: saleRequestId,
            items
        };
        setPending(true);
        element('sale-error').textContent = '';
        try {
            const details = await api('/api/sales', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            element('sale-editor').close();
            await load();
            displaySale(details);
            notify('Venda registrada e estoque atualizado.');
            await window.StockifyCash.reload();
            // Atualiza também o catálogo usado pelo formulário antigo de produtos.
            await refresh();
        } catch (error) {
            element('sale-error').textContent = error.message;
        } finally {
            setPending(false);
        }
    });

    element('payment-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        if (pending) return;
        setPending(true);
        element('payment-error').textContent = '';
        try {
            const details = await api('/api/sales/' + paymentSaleId + '/payments', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    amount: Number(element('payment-amount').value),
                    paymentMethod: element('payment-method').value,
                    notes: element('payment-notes').value.trim(),
                    requestId: paymentRequestId
                })
            });
            displaySale(details);
            await load();
            notify('Pagamento registrado.');
            await window.StockifyCash.reload();
        } catch (error) {
            element('payment-error').textContent = error.message;
        } finally {
            setPending(false);
        }
    });

    document
        .querySelectorAll('[data-commerce]')
        .forEach((button) => button.addEventListener('click', () => show(button.dataset.commerce)));
    document.querySelectorAll('[data-commerce-close]').forEach((button) =>
        button.addEventListener('click', () => {
            if (!pending) element(button.dataset.commerceClose).close();
        })
    );
    for (const id of ['contact-editor', 'sale-editor', 'ledger-dialog'])
        element(id).addEventListener('cancel', (event) => {
            if (pending) event.preventDefault();
        });
    element('commerce-new').addEventListener('click', () => {
        if (!pending) {
            if (current === 'customers' || current === 'suppliers') editContact();
            else newSale();
        }
    });
    element('commerce-refresh').addEventListener('click', () => {
        if (!pending) load();
    });
    element('commerce-search').addEventListener('input', render);
    element('sale-add-item').addEventListener('click', addSaleItem);
    element('sale-type').addEventListener('change', changeSaleType);
    element('sale-checkout').addEventListener('click', checkout);
    element('sales-focus-new').addEventListener('click', newSale);
    window.StockifyCommerce = {
        active: () => current !== null,
        hide,
        show,
        checkout,
        newSale,
        readyCheckout: () => checkoutOpen,
        pending: () => pending
    };
})();
