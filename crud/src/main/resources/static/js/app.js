'use strict';
const $ = (id) => document.getElementById(id);
const views = {
    products: {
        title: 'Produtos',
        singular: 'produto',
        description: 'Um catálogo para computadores, celulares, alimentos e muito mais.',
        list: 'Todos os produtos',
        path: '/products'
    },
    categories: {
        title: 'Categorias',
        singular: 'categoria',
        description: 'Organize seu catálogo com categorias para qualquer tipo de produto.',
        list: 'Suas categorias',
        path: '/api/categories'
    },
    clients: {
        title: 'Empresas',
        singular: 'empresa',
        description: 'Crie painéis separados e gerencie o acesso das empresas.',
        list: 'Painéis das empresas',
        path: '/api/clients'
    },
    users: {
        title: 'Usuários',
        singular: 'usuário',
        description: 'Crie contas e controle quem pode acessar o sistema.',
        list: 'Acessos ao sistema',
        path: '/users'
    }
};
let view = 'products',
    me = null,
    editing = null,
    busy = false,
    loading = false;
let data = { products: [], categories: [], users: [], clients: [] };
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
function notice(text, error = false) {
    $('notice').textContent = text;
    $('notice').className = error ? 'error' : '';
}
async function api(url, options = {}) {
    if (options.method && options.method !== 'GET') {
        const tokenResponse = await fetch('/auth/csrf');
        if (!tokenResponse.ok || tokenResponse.redirected) {
            location.assign('/login.html');
            throw new Error('Entre novamente para continuar.');
        }
        const token = await tokenResponse.json();
        options.headers = { ...options.headers, [token.headerName]: token.token };
    }
    const response = await fetch(url, options);
    if (
        response.status === 401 ||
        (url !== '/logout' &&
            response.redirected &&
            new URL(response.url).pathname === '/login.html')
    ) {
        location.assign('/login.html');
        throw new Error('Sua sessão expirou. Entre novamente.');
    }
    const text = await response.text();
    let result = null;
    if (text) {
        try {
            result = JSON.parse(text);
        } catch {
            result = text;
        }
    }
    if (!response.ok) {
        const error = new Error(
            result?.message ||
                (response.status === 403
                    ? 'Você não tem permissão para esta ação.'
                    : 'Não foi possível concluir. Verifique a conexão com o servidor.')
        );
        error.status = response.status;
        throw error;
    }
    return result;
}
function options(id, items, placeholder) {
    const select = $(id),
        previous = select.value;
    select.replaceChildren();
    const blank = document.createElement('option');
    blank.value = '';
    blank.textContent = placeholder;
    select.append(blank);
    for (const item of items) {
        const option = document.createElement('option');
        option.value = item.id;
        option.textContent = item.name;
        select.append(option);
    }
    select.value = previous;
}
function updateChoices() {
    options('category', data.categories, 'Sem categoria');
    options('category-filter', data.categories, 'Todas as categorias');
}
function label(items, id) {
    return items.find((item) => item.id === id)?.name || '—';
}
function button(text, handler, danger = false) {
    const node = document.createElement('button');
    node.type = 'button';
    node.className = danger ? 'delete' : 'secondary';
    node.textContent = text;
    node.disabled = busy || loading;
    node.addEventListener('click', handler);
    return node;
}
function render() {
    if (window.StockifyCommerce?.active()) return;
    const config = views[view];
    $('page-title').textContent = config.title;
    $('page-description').textContent = config.description;
    $('list-title').textContent = config.list;
    $('list-description').textContent =
        view === 'users'
            ? 'Contas individuais, com perfis e acesso controlado.'
            : view === 'clients'
              ? 'Cada empresa tem seus próprios dados e acessos.'
              : 'Cadastros da sua empresa.';
    $('new-entry').textContent =
        '+ ' +
        (view === 'categories'
            ? 'Nova categoria'
            : view === 'clients'
              ? 'Nova empresa'
              : view === 'users'
                ? 'Novo usuário'
                : 'Novo produto');
    $('product-filters').hidden = view !== 'products';
    $('scan-product').disabled = busy || loading || !me;
    document.querySelectorAll('[data-view]').forEach((node) => {
        node.classList.toggle('active', node.dataset.view === view);
        node.disabled = busy || loading;
    });
    $('new-entry').disabled = busy || loading || !me;
    $('refresh').disabled = busy || loading || !me;
    const headings =
        view === 'products'
            ? [
                  'ID',
                  'Produto',
                  'Código de barras',
                  'Categoria',
                  'Marca',
                  'Estoque',
                  'Venda',
                  ...(me.role !== 'USER' ? ['Custo', 'Lucro / un.'] : []),
                  'Ações'
              ]
            : view === 'users'
              ? ['Nome', 'E-mail', 'Perfil', 'Acesso', 'Ações']
              : view === 'clients'
                ? ['Empresa', 'Código de acesso', 'Painel', 'Ações']
                : ['ID', 'Nome', 'Produtos', 'Ações'];
    $('table-head').replaceChildren();
    const header = document.createElement('tr');
    headings.forEach((text, index) => {
        const cell = document.createElement('th');
        cell.textContent = text;
        if (index === headings.length - 1) cell.className = 'actions';
        header.append(cell);
    });
    $('table-head').append(header);
    const term = $('search').value.trim().toLocaleLowerCase('pt-BR');
    const list = data[view].filter((item) => {
        const matches = [
            item.name,
            item.slug,
            item.email,
            item.barcode,
            item.sku,
            item.model,
            item.description,
            label(data.categories, item.categoryId),
            item.brandName,
            String(item.id)
        ].some((value) =>
            String(value || '')
                .toLocaleLowerCase('pt-BR')
                .includes(term)
        );
        return (
            matches &&
            (view !== 'products' ||
                !$('category-filter').value ||
                String(item.categoryId) === $('category-filter').value)
        );
    });
    if (view === 'products') list.sort((a, b) =>
        label(data.categories, a.categoryId).localeCompare(label(data.categories, b.categoryId), 'pt-BR') ||
        a.name.localeCompare(b.name, 'pt-BR')
    );
    $('entries').replaceChildren();
    let previousCategory = undefined;
    for (const item of list) {
        if (view === 'products' && previousCategory !== (item.categoryId ?? null)) {
            previousCategory = item.categoryId ?? null;
            const group = document.createElement('tr');
            group.className = 'inventory-group';
            const title = document.createElement('td');
            title.colSpan = headings.length;
            const categoryItems = list.filter(p => (p.categoryId ?? null) === previousCategory);
            const quantity = categoryItems.reduce((total, p) => total + Number(p.quantity || 0), 0);
            const value = categoryItems.reduce((total, p) => total + Number(p.quantity || 0) * Number(p.price || 0), 0);
            title.textContent = (previousCategory == null ? 'Sem categoria' : label(data.categories, previousCategory)) +
                ' · ' + categoryItems.length + ' produto(s) · ' + quantity + ' unidade(s) · valor de venda do estoque: ' + currency.format(value);
            title.style.setProperty('--group-color', ['#6195ef', '#9b70d8', '#50a889', '#db9950'][Math.abs(previousCategory || 0) % 4]);
            group.append(title);
            $('entries').append(group);
        }
        const row = document.createElement('tr');
        row.className = view + '-row';
        const values =
            view === 'products'
                ? [
                      '#' + item.id,
                      item.name + (item.model ? ' · ' + item.model : ''),
                      item.barcode || '—',
                      label(data.categories, item.categoryId),
                      item.brandName || '—',
                      item.quantity ?? 0,
                      currency.format(item.price),
                      ...(me.role !== 'USER'
                          ? [
                                item.costPrice == null
                                    ? 'Não informado'
                                    : currency.format(item.costPrice),
                                item.costPrice == null
                                    ? '—'
                                    : currency.format(Number(item.price) - Number(item.costPrice))
                            ]
                          : [])
                  ]
                : view === 'users'
                  ? [
                        item.name,
                        item.email,
                        item.role === 'OWNER'
                            ? 'Administrador Stockify'
                            : item.role === 'ADMIN'
                              ? 'Proprietário da loja'
                              : 'Funcionário',
                        item.active ? 'Ativo' : 'Sem acesso'
                    ]
                  : view === 'clients'
                    ? [item.name, item.slug, item.active ? 'Ativo' : 'Desativado']
                    : [
                          '#' + item.id,
                          item.name,
                          data.products.filter((product) => product.categoryId === item.id).length
                      ];
        values.forEach((text, index) => {
            const cell = document.createElement('td');
            cell.dataset.label = headings[index];
            cell.textContent = text;
            if (view === 'users' && index === 3)
                cell.className = item.active ? 'status-active' : 'status-inactive';
            row.append(cell);
        });
        const actions = document.createElement('td');
        actions.className = 'actions';
        const edit = button('Editar', () => openEditor(item));
        edit.setAttribute('aria-label', 'Editar ' + item.name);
        actions.append(edit);
        if (view === 'products')
            actions.append(button('Repor estoque', () => window.StockifyOperations.restock(item)));
        if (view !== 'users' && view !== 'clients') {
            const remove = button('Excluir', () => removeEntry(item), true);
            remove.setAttribute('aria-label', 'Excluir ' + item.name);
            actions.append(remove);
        }
        if (view === 'clients') {
            const copy = button('Copiar link', async () => {
                try {
                    await navigator.clipboard.writeText(
                        location.origin + '/login.html?company=' + encodeURIComponent(item.slug)
                    );
                    notice('Link de acesso copiado.');
                } catch {
                    notice(
                        'Link: ' +
                            location.origin +
                            '/login.html?company=' +
                            encodeURIComponent(item.slug)
                    );
                }
            });
            actions.append(copy);
        }
        row.append(actions);
        $('entries').append(row);
    }
    $('empty').hidden = list.length > 0;
    $('empty').textContent = loading
        ? 'Carregando…'
        : term
          ? 'Nenhum registro encontrado.'
          : 'Nenhum registro para exibir. Cadastre o primeiro.';
    $('count').textContent = list.length + ' de ' + data[view].length + ' registros';
}
async function refresh() {
    if (loading) return;
    loading = true;
    render();
    notice('Atualizando cadastros…');
    try {
        const urls = ['/products', '/api/categories'];
        if (me.role === 'ADMIN' || me.role === 'OWNER') urls.push('/users');
        if (me.role === 'OWNER') urls.push('/api/clients');
        const results = await Promise.all(urls.map((url) => api(url)));
        if (results.some((result) => !Array.isArray(result)))
            throw new Error('O servidor retornou um cadastro inválido.');
        data = {
            products: results[0],
            categories: results[1],
            users: results[2] || [],
            clients: me.role === 'OWNER' ? results[3] || [] : []
        };
        updateChoices();
        window.StockifyProgress?.update({ products: data.products });
        notice('');
        return true;
    } catch (error) {
        notice(error.message, true);
        return false;
    } finally {
        loading = false;
        render();
    }
}
function openEditor(item = null) {
    editing = item;
    $('entry-form').reset();
    $('form-error').textContent = '';
    const isProduct = view === 'products',
        isUser = view === 'users',
        isClient = view === 'clients';
    $('client-fields').hidden = !isClient;
    $('client-fields').disabled = !isClient;
    $('initial-admin-fields').hidden = !!item;
    $('initial-admin-fields').disabled = !isClient || !!item;
    $('client-slug').required = isClient;
    $('client-slug').disabled = !!item;
    for (const id of ['client-admin-name', 'client-admin-email', 'client-admin-password'])
        $(id).required = isClient && !item;
    $('name-label').textContent = isClient
        ? 'Nome da empresa'
        : isUser
          ? 'Nome completo'
          : view === 'categories'
            ? 'Nome da categoria'
            : 'Nome do produto';
    $('product-fields').hidden = !isProduct;
    $('product-fields').disabled = !isProduct;
    $('user-fields').hidden = !isUser;
    $('user-fields').disabled = !isUser;
    $('name').maxLength = isProduct ? 255 : 100;
    $('price').required = isProduct;
    $('cost-price').required = isProduct && !item && me.role !== 'USER';
    $('cost-price').disabled = me.role === 'USER';
    $('email').required = isUser;
    $('password').required = isUser && !item;
    $('form-title').textContent = (item ? 'Editar ' : 'Cadastrar ') + views[view].singular;
    $('barcode-help').textContent =
        'Use o leitor ou digite o código e pressione Enter para consultar.';
    $('quantity').value = '0';
    $('password-help').textContent = item
        ? 'Deixe em branco para manter a senha. Uma nova senha encerra as sessões abertas.'
        : 'Crie uma senha com pelo menos 8 caracteres.';
    $('role').querySelector?.('option[value=OWNER]')?.remove();
    if (isUser && item?.role === 'OWNER') {
        const option = document.createElement('option');
        option.value = 'OWNER';
        option.textContent = 'Administrador Stockify';
        $('role').append(option);
    }
    updateChoices();
    if (isClient && item) {
        $('client-slug').value = item.slug;
        $('client-active').value = String(item.active);
    }
    $('client-active').disabled = isClient && (!item || item.slug === me.company);
    if (item) {
        $('name').value = item.name;
        if (isProduct) {
            $('price').value = item.price;
            $('cost-price').value = item.costPrice ?? '';
            window.StockifyOperations?.productProfit();
            for (const field of ['barcode', 'sku', 'model', 'description'])
                $(field).value = item[field] || '';
            $('quantity').value = item.quantity ?? 0;
            $('category').value = item.categoryId || '';
            $('brand-name').value = item.brandName || '';
        }
        if (isUser) {
            $('email').value = item.email;
            $('role').value = item.role;
            $('active').value = String(item.active);
        }
    }
    const own = isUser && item?.id === me.id;
    $('role').disabled = own;
    $('active').disabled = own;
    $('editor').showModal();
    $('name').focus();
}
function setBusy(value) {
    busy = value;
    $('save').disabled = value;
    document.querySelectorAll('[data-close]').forEach((node) => (node.disabled = value));
    render();
}
async function removeEntry(item) {
    if (
        busy ||
        loading ||
        !confirm('Excluir "' + item.name + '"? Essa ação não pode ser desfeita.')
    )
        return;
    setBusy(true);
    try {
        await api(views[view].path + '/' + item.id, { method: 'DELETE' });
        if (await refresh()) notice('Registro excluído.');
    } catch (error) {
        notice(error.message, true);
    } finally {
        setBusy(false);
    }
}
$('entry-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    const name = $('name').value.trim();
    if (!name) {
        $('form-error').textContent = 'Informe um nome.';
        return;
    }
    let payload = { name };
    if (view === 'products') {
        const rawPrice = $('price').value.replace(',', '.');
        const price = Number(rawPrice);
        const quantity = Number($('quantity').value);
        if (
            !/^\d+(\.\d{1,2})?$/.test(rawPrice) ||
            !Number.isFinite(price) ||
            price < 0 ||
            price > 999999999999.99
        ) {
            $('form-error').textContent = 'Informe um preço válido com até duas casas decimais.';
            return;
        }
        if (!Number.isInteger(quantity) || quantity < 0 || quantity > 2147483647) {
            $('form-error').textContent = 'Informe uma quantidade inteira válida.';
            return;
        }
        payload = {
            name,
            price,
            ...(me.role !== 'USER' && $('cost-price').value !== ''
                ? { costPrice: Number($('cost-price').value) }
                : {}),
            barcode: $('barcode').value.trim() || null,
            sku: $('sku').value.trim() || null,
            model: $('model').value.trim() || null,
            description: $('description').value.trim() || null,
            quantity,
            categoryId: $('category').value ? Number($('category').value) : null,
            brandName: $('brand-name').value.trim() || null
        };
    }
    if (view === 'clients') {
        payload = editing
            ? { name, active: $('client-active').value === 'true' }
            : {
                  name,
                  slug: $('client-slug').value.trim().toLowerCase(),
                  adminName: $('client-admin-name').value.trim(),
                  adminEmail: $('client-admin-email').value.trim(),
                  password: $('client-admin-password').value
              };
        if (
            !editing &&
            (payload.password.length < 8 || new TextEncoder().encode(payload.password).length > 72)
        ) {
            $('form-error').textContent =
                'A senha precisa ter pelo menos 8 caracteres e no máximo 72 bytes.';
            return;
        }
    }
    if (view === 'users') {
        const senha = $('password').value;
        if (
            (!editing || senha) &&
            (senha.length < 8 || new TextEncoder().encode(senha).length > 72)
        ) {
            $('form-error').textContent =
                'A senha precisa ter pelo menos 8 caracteres e no máximo 72 bytes.';
            return;
        }
        payload = {
            name,
            email: $('email').value.trim(),
            senha,
            role: $('role').value,
            active: $('active').value === 'true'
        };
    }
    setBusy(true);
    $('form-error').textContent = '';
    try {
        await api(views[view].path + (editing ? '/' + editing.id : ''), {
            method: editing ? 'PUT' : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        $('editor').close();
        const refreshed = await refresh();
        if (
            (view === 'users' && editing?.id === me.id) ||
            (view === 'clients' && editing?.slug === me.company)
        ) {
            me = await api('/api/me');
            showAccount();
        }
        if (refreshed) notice('Cadastro salvo.');
    } catch (error) {
        $('form-error').textContent = error.message;
        if (!$('editor').open) notice(error.message, true);
    } finally {
        setBusy(false);
    }
});
function showAccount() {
    $('account-name').textContent = me.name;
    $('account-role').textContent =
        me.role === 'OWNER'
            ? 'Administrador Stockify'
            : me.role === 'ADMIN'
              ? 'Proprietário da loja'
              : 'Funcionário';
    $('company-label').textContent = me.companyName || 'Stockify';
    $('avatar').textContent = me.name.slice(0, 1).toUpperCase();
    document
        .querySelectorAll('[data-admin]')
        .forEach((node) => (node.hidden = !['ADMIN', 'OWNER'].includes(me.role)));
    document
        .querySelectorAll('[data-owner]')
        .forEach((node) => (node.hidden = me.role !== 'OWNER'));
}
for (const id of ['search', 'category-filter'])
    $(id).addEventListener(id === 'search' ? 'input' : 'change', render);
document.querySelectorAll('[data-view]').forEach((node) =>
    node.addEventListener('click', () => {
        if (busy || loading) return;
        if (window.StockifyCommerce && !window.StockifyCommerce.hide()) return;
        view = node.dataset.view;
        $('search').value = '';
        $('search').placeholder =
            view === 'products' ? 'Nome, código de barras ou referência…' : 'Pesquisar…';
        notice('');
        render();
    })
);
document.querySelectorAll('[data-close]').forEach((node) =>
    node.addEventListener('click', () => {
        if (!busy) $('editor').close();
    })
);
$('editor').addEventListener('cancel', (event) => {
    if (busy) event.preventDefault();
});
$('scan-product').addEventListener('click', () => {
    openEditor();
    $('barcode').focus();
});
$('barcode').addEventListener('keydown', async (event) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    if (busy) return;
    const code = $('barcode').value.trim();
    if (!code) {
        $('barcode-help').textContent = 'Informe um código de barras.';
        return;
    }
    setBusy(true);
    $('barcode-help').textContent = 'Consultando código…';
    try {
        const existing = await api('/products/lookup?barcode=' + encodeURIComponent(code));
        openEditor(existing);
        $('barcode-help').textContent = 'Produto encontrado. Você pode atualizar o cadastro.';
    } catch (error) {
        if (error.status === 404) {
            $('barcode-help').textContent = 'Código disponível. Preencha os dados para cadastrar.';
            $('name').focus();
        } else $('barcode-help').textContent = error.message;
    } finally {
        setBusy(false);
    }
});
$('new-entry').addEventListener('click', () => {
    if (!window.StockifyCommerce?.active()) openEditor();
});
$('refresh').addEventListener('click', refresh);
$('logout').addEventListener('click', async () => {
    $('logout').disabled = true;
    try {
        await api('/logout', { method: 'POST' });
        location.assign('/login.html?logout');
    } catch (error) {
        notice(error.message, true);
        $('logout').disabled = false;
    }
});
async function init() {
    try {
        me = await api('/api/me');
        showAccount();
        await refresh();
        await window.StockifyCash.init();
        window.StockifyProgress?.init();
    } catch (error) {
        notice(error.message, true);
    }
}
init();
