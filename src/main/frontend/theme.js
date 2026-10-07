// Run before styles load so returning visitors never see the wrong theme.
let theme = 'light';
try {
  const stored = localStorage.getItem('theme');
  const cookie = document.cookie.match(/(?:^|;\s*)theme=(light|dark)(?:;|$)/)?.[1];
  theme = ['light', 'dark'].includes(stored) ? stored : cookie || 'light';
} catch { /* Storage may be unavailable in embedded/private contexts. */ }
document.documentElement.dataset.theme = theme;
document.documentElement.style.colorScheme = theme;
