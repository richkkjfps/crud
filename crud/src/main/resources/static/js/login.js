'use strict';
const $ = (id) => document.getElementById(id);
let mode = 'login',
    csrf = null,
    pending = false;
const params = new URLSearchParams(location.search);
$('company').value = params.get('company') || '';
if (params.has('error'))
    $('login-message').textContent = 'Empresa, e-mail ou senha incorretos, ou acesso não liberado.';
else if (params.has('logout')) $('login-message').textContent = 'Você saiu da sua conta.';
function changeMode(next) {
    if (pending) return;
    mode = next;
    const signup = mode === 'register';
    $('signup-name').hidden = !signup;
    $('full-name').required = signup;
    $('signup-help').hidden = !signup;
    $('forgot-password').hidden = signup;
    $('password').minLength = signup ? 8 : 0;
    $('password').autocomplete = signup ? 'new-password' : 'current-password';
    $('access-title').textContent = signup ? 'Crie sua conta' : 'Bem-vindo de volta';
    $('access-subtitle').textContent = signup
        ? 'Solicite acesso ao painel da sua empresa'
        : 'Entre na conta da sua empresa';
    $('login-submit').textContent = signup ? 'CRIAR CONTA' : 'ENTRAR';
    $('login-message').textContent = '';
    for (const id of ['login-tab', 'register-tab']) {
        const selected = (id === 'register-tab') === signup;
        $(id).classList.toggle('selected', selected);
        $(id).setAttribute('aria-selected', selected);
    }
}
$('login-tab').addEventListener('click', () => changeMode('login'));
$('register-tab').addEventListener('click', () => changeMode('register'));
$('toggle-password').addEventListener('click', () => {
    const input = $('password');
    input.type = input.type === 'password' ? 'text' : 'password';
    $('toggle-password').setAttribute(
        'aria-label',
        input.type === 'password' ? 'Mostrar senha' : 'Ocultar senha'
    );
});
$('forgot-password').addEventListener('click', () => {
    $('login-message').textContent =
        'Peça ao administrador da sua empresa para redefinir sua senha na área de Usuários.';
});
async function prepare() {
    try {
        const response = await fetch('/auth/csrf');
        if (!response.ok) throw new Error();
        csrf = await response.json();
        $('csrf').name = csrf.parameterName;
        $('csrf').value = csrf.token;
        $('login-submit').disabled = false;
    } catch {
        $('login-message').textContent =
            'Não foi possível conectar. Atualize a página para tentar novamente.';
    }
}
$('access-form').addEventListener('submit', async (event) => {
    if (pending || !csrf) {
        event.preventDefault();
        return;
    }
    $('company').value = $('company').value.trim().toLowerCase();
    if (mode === 'login') {
        pending = true;
        $('login-submit').disabled = true;
        $('login-submit').textContent = 'ENTRANDO…';
        return;
    }
    event.preventDefault();
    const password = $('password').value;
    if (password.length < 8 || new TextEncoder().encode(password).length > 72) {
        $('login-message').textContent =
            'Use uma senha com pelo menos 8 caracteres e no máximo 72 bytes.';
        return;
    }
    pending = true;
    $('login-submit').disabled = true;
    $('login-submit').textContent = 'ENVIANDO…';
    try {
        const response = await fetch('/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
            body: JSON.stringify({
                company: $('company').value,
                name: $('full-name').value.trim(),
                email: $('username').value.trim(),
                password
            })
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || 'Não foi possível enviar o cadastro.');
        $('login-message').textContent = result.message;
        $('password').value = '';
    } catch (error) {
        $('login-message').textContent = error.message;
    } finally {
        pending = false;
        $('login-submit').disabled = false;
        $('login-submit').textContent = 'CRIAR CONTA';
    }
});
prepare();
