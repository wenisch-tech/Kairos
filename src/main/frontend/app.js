import Alpine from 'alpinejs';
import focus from '@alpinejs/focus';
import { renderIcons } from './icons.js';
import './monitoring.js';

Alpine.plugin(focus);
Alpine.data('dialog', () => ({
  open: false,
  hide() { this.open = false; }
}));
Alpine.data('disclosure', () => ({
  open: false,
  init() { this.open = this.$el.classList.contains('is-open'); }
}));
window.Alpine = Alpine;
window.KairosUI = {
  icons: renderIcons,
  dialog(id, open = true) {
    window.dispatchEvent(new CustomEvent('kairos-dialog', { detail: { id, open } }));
  },
  help() {
    document.querySelectorAll('[data-ui-toggle="popover"]').forEach(button => {
      if (button.dataset.helpReady) return;
      button.dataset.helpReady = 'true';
      const content = document.createElement('div');
      content.className = 'help-popover';
      content.id = `help-${document.querySelectorAll('.help-popover').length}`;
      // Help content is presentation text, never executable markup.
      const text = new DOMParser().parseFromString(button.dataset.uiContent || '', 'text/html').body.textContent;
      content.textContent = text;
      content.hidden = true;
      content.setAttribute('role', 'tooltip');
      button.setAttribute('aria-describedby', content.id);
      button.parentElement.append(content);
      const show = () => { content.hidden = false; };
      const hide = () => { content.hidden = true; };
      button.addEventListener('mouseenter', show);
      button.addEventListener('focus', show);
      button.addEventListener('mouseleave', hide);
      button.addEventListener('blur', hide);
      button.addEventListener('click', () => { content.hidden = !content.hidden; });
      button.addEventListener('keydown', event => { if (event.key === 'Escape') hide(); });
    });
  }
};

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.ui-modal').forEach(dialog => {
    const title = dialog.querySelector('.ui-modal-title');
    if (title) { title.id ||= `${dialog.id}-title`; dialog.setAttribute('aria-labelledby', title.id); }
  });
  document.querySelectorAll('.ui-collapse').forEach(panel => {
    panel.setAttribute('x-data', 'disclosure');
    panel.setAttribute('x-show', 'open');
    panel.setAttribute('@kairos-collapse.window', 'if ($event.detail.id === $el.id) open = !open');
    if (!panel.classList.contains('is-open')) panel.setAttribute('x-cloak', '');
  });
  // Associate older server-rendered forms' adjacent labels without changing field names.
  document.querySelectorAll('label.ui-form-label').forEach((label, i) => {
    if (label.htmlFor) return;
    const input = label.parentElement.querySelector('input:not([type="hidden"]), select, textarea');
    if (input) { input.id ||= `field-${i}`; label.htmlFor = input.id; }
  });
  document.querySelectorAll('[data-ui-toggle="collapse"]').forEach(button => {
    const id = button.dataset.uiTarget?.replace(/^#/, '');
    const target = document.getElementById(id);
    button.setAttribute('aria-controls', id || '');
    button.setAttribute('aria-expanded', String(target?.classList.contains('is-open') || false));
  });
  document.querySelectorAll('.ui-alert').forEach(el => {
    el.setAttribute('role', el.classList.contains('ui-alert-danger') ? 'alert' : 'status');
    el.setAttribute('x-data', '{ visible: true }');
    el.setAttribute('x-show', 'visible');
    el.setAttribute('@kairos-dismiss', 'visible = false');
  });
  document.querySelectorAll('.public-links a.ui-nav-link').forEach(link => {
    if (new URL(link.href).pathname === location.pathname) {
      link.classList.add('active');
      link.setAttribute('aria-current', 'page');
    }
  });
  document.querySelectorAll('[data-role="status-dot"]').forEach(dot => {
    dot.setAttribute('role', 'img');
    dot.setAttribute('aria-label', 'Status: ' + (dot.classList.contains('status-available') ? 'available' : dot.classList.contains('status-not-available') ? 'not available' : 'unknown'));
  });
  document.querySelectorAll('.ui-btn-close:empty').forEach(el => { el.innerHTML = '<i data-icon="x" aria-hidden="true"></i>'; });
  renderIcons();
  Alpine.start();
  window.KairosUI.help();
});

document.addEventListener('click', event => {
  const trigger = event.target.closest('[data-ui-toggle], [data-ui-dismiss]');
  if (!trigger) return;
  if (trigger.dataset.uiToggle === 'modal') window.KairosUI.dialog(trigger.dataset.uiTarget.replace(/^#/, ''));
  if (trigger.dataset.uiDismiss === 'modal') window.KairosUI.dialog(trigger.closest('.ui-modal').id, false);
  if (trigger.dataset.uiDismiss === 'alert') trigger.closest('.ui-alert').dispatchEvent(new CustomEvent('kairos-dismiss'));
  if (trigger.dataset.uiToggle === 'collapse') {
    const id = trigger.dataset.uiTarget.replace(/^#/, '');
    window.dispatchEvent(new CustomEvent('kairos-collapse', { detail: { id } }));
    trigger.setAttribute('aria-expanded', String(trigger.getAttribute('aria-expanded') !== 'true'));
  }
});
