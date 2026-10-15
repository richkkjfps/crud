(() => {
 const nav = document.querySelector('nav[aria-label="Menu principal"]');
 if (!nav) return;
 nav.id = 'store-navigation';
 const toggle = document.createElement('button');
 toggle.type = 'button';
 toggle.className = 'secondary mobile-menu-toggle';
 toggle.textContent = 'Menu';
 toggle.setAttribute('aria-controls', nav.id);
 document.querySelector('aside .brand').after(toggle);
 const mobile = window.matchMedia('(max-width:600px)');
 let expanded = false;
 const render = () => {
   nav.hidden = mobile.matches && !expanded;
   toggle.setAttribute('aria-expanded', String(!nav.hidden));
 };
 toggle.addEventListener('click', () => { expanded = !expanded; render(); });
 mobile.addEventListener('change', render);
 nav.addEventListener('click', event => {
   if (mobile.matches && event.target.closest('button.nav[data-view],button.nav[data-commerce]')) {
     expanded = false;
     render();
     document.querySelector('main').focus({preventScroll:true});
   }
 });
 const sync = () => nav.querySelectorAll('button.nav').forEach(button => {
   if (button.classList.contains('active')) button.setAttribute('aria-current','page');
   else button.removeAttribute('aria-current');
 });
 new MutationObserver(sync).observe(nav, {subtree:true, attributes:true, attributeFilter:['class']});
 sync(); render();
 document.querySelectorAll('.table-wrap').forEach((region, index) => {
   region.tabIndex = 0;
   region.setAttribute('role','region');
   region.setAttribute('aria-labelledby', index === 0 ? 'list-title' : 'commerce-title');
 });
})();
