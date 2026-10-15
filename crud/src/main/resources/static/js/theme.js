(function () {
 'use strict';
 const key = 'stockify-theme';
 let saved;
 try { saved = localStorage.getItem(key); } catch (_) {}
 let theme = saved === 'dark' ? 'dark' : 'light';
 const apply = () => { document.documentElement.dataset.theme = theme; };
 apply();
 document.addEventListener('DOMContentLoaded', () => {
  const button = document.createElement('button');
  button.type = 'button'; button.className = 'secondary theme-toggle';
  const update = () => {
   button.innerHTML = theme === 'dark'
    ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4M17.6 6.4 19 5"/></svg>'
    : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.2 14.1A8.5 8.5 0 0 1 9.9 3.8a8.5 8.5 0 1 0 10.3 10.3Z"/></svg>';
   button.title = theme === 'dark' ? 'Ativar tema claro' : 'Ativar tema escuro';
   button.setAttribute('aria-label', theme === 'dark' ? 'Ativar tema claro' : 'Ativar tema escuro');
   button.setAttribute('aria-pressed', String(theme === 'dark'));
  };
  button.addEventListener('click', () => {
   theme = theme === 'dark' ? 'light' : 'dark'; apply(); update();
   try { localStorage.setItem(key, theme); } catch (_) {}
  });
  update();
  const sidebar = document.querySelector('aside');
  if (sidebar) sidebar.insertBefore(button, sidebar.querySelector('.aside-footer'));
  else { button.classList.add('theme-toggle-access'); const card = document.querySelector('.access-card'); if (card) card.prepend(button); else document.body.appendChild(button); }
 });
})();


