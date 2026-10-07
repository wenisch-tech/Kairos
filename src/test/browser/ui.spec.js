import { test, expect } from '@playwright/test';

async function navigate(page, url) {
  const response = await page.goto(url, { waitUntil:'domcontentloaded' });
  await expect(page.locator('body')).toBeVisible();
  return response;
}

async function login(page) {
  await navigate(page, '/login');
  await page.getByLabel('Email', { exact:true }).fill('admin@kairos.local');
  await page.getByLabel('Password', { exact:true }).fill('admin');
  await Promise.all([
    page.waitForURL(url => !url.pathname.endsWith('/login'), { waitUntil:'domcontentloaded' }),
    page.getByRole('button', { name:'Sign In', exact:true }).click()
  ]);
}

test.beforeEach(async ({ page }) => {
  // Keep browser cases deterministic and avoid retaining server-side SSE
  // subscriptions between repeated responsive screenshot navigations.
  await page.addInitScript(() => {
    window.EventSource = class extends EventTarget {
      constructor() { super(); window.testStream = this; }
      close() {}
    };
  });
});

test.beforeAll(async ({ browser }) => {
  const page = await browser.newPage();
  await login(page);
  await navigate(page, '/admin/resources');
  if (await page.locator('a[href^="/admin/resources/edit/"]').count()) { await page.close(); return; }
  await page.locator('[data-ui-target="#addGroupModal"]').click();
  await page.getByRole('dialog').getByLabel('Group Name').fill('Core infrastructure');
  await page.getByRole('dialog').getByRole('button', { name:'Add Group', exact:true }).click();
  for (const name of ['Public API', 'Documentation', 'Container registry']) {
    await page.locator('[data-ui-target="#addResourceModal"]').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Name', { exact:true }).fill(name);
    await dialog.getByLabel('Target', { exact:true }).fill('http://127.0.0.1:18080/actuator/health');
    if (name !== 'Documentation') await dialog.getByLabel('Groups', { exact:true }).selectOption({ label:'Core infrastructure' });
    await dialog.getByRole('button', { name:'Add', exact:true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  }
  await page.close();
});

test('public navigation, filters, views, ranges, and persistent theme', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await navigate(page, '/');
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  await expect(page.getByRole('heading', { name:'Service status' })).toBeVisible();
  await expect(page.locator('[data-role="snapshot-total"]')).toHaveText('3');
  await page.getByRole('button', { name:'Cards', exact:true }).click();
  await expect(page.locator('.resource-card:visible')).toHaveCount(3);
  await page.getByRole('textbox', { name:'Filter resources by name' }).fill('Documentation');
  await expect(page.locator('.resource-card:visible')).toHaveCount(1);
  await page.getByRole('textbox', { name:'Filter resources by name' }).press('Escape');
  await expect(page.locator('.resource-card:visible')).toHaveCount(3);
  await page.getByRole('button', { name:'Groups', exact:true }).click();
  await expect(page.locator('.group-header-only-list')).toBeVisible();
  await page.getByRole('button', { name:'Timeline', exact:true }).click();
  await page.getByRole('button', { name:'7d', exact:true }).click();
  await expect(page.getByRole('button', { name:'7d', exact:true })).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button', { name:'Toggle color theme' }).click();
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme','dark');
  await page.getByRole('link', { name:'Outages', exact:true }).click();
  await expect(page).toHaveURL(/\/outages/);
  expect(errors).toEqual([]);
});

test('landing layout keeps its compact cards, full-width header, and anchored footer', async ({ page }) => {
  await page.setViewportSize({ width:1440, height:1000 });
  await navigate(page, '/');
  expect((await page.locator('.public-nav-inner').boundingBox()).width).toBe(1440);
  await page.getByRole('button', { name:'Cards', exact:true }).click();

  const groupedCards = page.locator('.resource-cards-grid-grouped .resource-card:visible');
  await expect(groupedCards).toHaveCount(2);
  const [firstCard, secondCard, cardGrid] = await Promise.all([
    groupedCards.nth(0).boundingBox(),
    groupedCards.nth(1).boundingBox(),
    page.locator('.resource-cards-grid-grouped:visible').boundingBox()
  ]);
  expect(firstCard.y).toBe(secondCard.y);
  expect(firstCard.width).toBeLessThan(cardGrid.width / 3);

  const footer = page.locator('.landing-footer-meta');
  await expect(footer).toBeVisible();
  expect(await footer.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(3);
  const [embed, credit] = await Promise.all([
    footer.locator('.landing-embed-tools').boundingBox(),
    footer.locator('.landing-credit').boundingBox()
  ]);
  expect(credit.x).toBeGreaterThan(embed.x + embed.width);

  await page.locator('.dashboard-shell > :not(.dashboard-hero):not(.landing-footer-meta)').evaluateAll(elements => {
    for (const element of elements) element.style.display = 'none';
  });
  const anchoredFooter = await footer.boundingBox();
  expect(anchoredFooter.y + anchoredFooter.height).toBeGreaterThan(960);

  await page.setViewportSize({ width:834, height:1000 });
  await navigate(page, '/');
  const [hero, controls] = await Promise.all([
    page.locator('.dashboard-hero').boundingBox(),
    page.locator('.dashboard-meta').boundingBox()
  ]);
  expect(Math.abs(hero.width - controls.width)).toBeLessThanOrEqual(1);
});

test('login branding stays compact', async ({ page }) => {
  await navigate(page, '/login');
  const logo = await page.locator('.login-logo').boundingBox();
  expect(logo.width).toBe(40);
  expect(logo.height).toBe(40);
});

test('admin dialog restores focus and resource edits persist', async ({ page }) => {
  await login(page);
  await navigate(page, '/admin/resources');
  const trigger = page.locator('[data-ui-target="#addResourceModal"]');
  await trigger.click();
  await expect(page.getByRole('dialog', { name:'Add Resource', exact:true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await page.locator('a[href^="/admin/resources/edit/"]').first().click();
  await page.getByLabel('Name', { exact:true }).fill('Documentation — service health');
  await page.getByRole('button', { name:/save/i }).click();
  await expect(page.locator('.admin-content')).toContainText('Documentation — service health');
});

test('every admin page renders without browser errors', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await login(page);
  for (const route of ['settings','resources','resource-types','resource-discovery','outages','check-history','announcements','announcements/new','users','api-keys','proxy-config','custom-headers','embed','about','notification-providers','notification-providers/new','notification-policies','notification-policies/new']) {
    const response = await navigate(page, `/admin/${route}`);
    expect(response.status(), route).toBe(200);
    await expect(page.locator('#admin-sidebar')).toBeVisible();
    await expect(page.locator('.admin-content')).toBeVisible();
    await page.setViewportSize({ width:390, height:900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), route).toBe(true);
    await page.setViewportSize({ width:1440, height:1000 });
  }
  expect(errors).toEqual([]);
});

test('announcement editor preserves rich content', async ({ page }) => {
  await login(page);
  await navigate(page, '/admin/announcements/new');
  await page.evaluate(() => window.Quill.find(document.querySelector('#editor')).clipboard.dangerouslyPasteHTML('<p><strong>Maintenance completed.</strong> All services are operating normally.</p><ul><li>API checks passed</li></ul>'));
  await page.getByRole('button', { name:'Save Announcement' }).click();
  await expect(page).toHaveURL(/\/admin\/announcements$/);
  await navigate(page, '/announcements');
  await expect(page.locator('.announcement-content')).toContainText('Maintenance completed.');
  await expect(page.locator('.announcement-content strong')).toHaveText('Maintenance completed.');
  await expect(page.locator('.announcement-content li')).toHaveText('API checks passed');
});

test('live SSE updates refresh status indicators and totals', async ({ page }) => {
  await navigate(page, '/');
  await expect(page.locator('.resource-row.resource-loading')).toHaveCount(0);
  const row = page.locator('.resource-row[data-resource-id]').first();
  await row.scrollIntoViewIfNeeded();
  const id = await row.getAttribute('data-resource-id');
  await page.evaluate(id => {
    window.testStream.dispatchEvent(new MessageEvent('resource-update', {
      data: JSON.stringify({ resourceId:Number(id), currentStatus:'not-available', uptimePercentage:98.75, timelineBlocks:[], activeOutageSince:'2026-10-07T10:00:00' })
    }));
  }, id);
  await expect(row.locator('[data-role="status-dot"]')).toHaveClass(/status-not-available/);
  await expect(page.locator('[data-role="snapshot-down"]')).not.toHaveText('0');
});

test('instant check results can be submitted as a resource', async ({ page }) => {
  await login(page);
  await navigate(page, '/admin/settings');
  await page.getByLabel('Enable instant check form on landing page').check();
  await page.getByRole('button', { name:/save/i }).click();
  await navigate(page, '/');
  await page.locator('.instant-check-disclosure summary').click();
  await page.getByRole('textbox', { name:'Resource target' }).fill('http://127.0.0.1:18080/actuator/health');
  await page.locator('#instant-check-submit').click();
  await expect(page.getByRole('dialog', { name:'Instant Check Result' })).toBeVisible();
  await expect(page.locator('#instant-check-result-status')).toContainText('AVAILABLE');
  await page.locator('#instant-check-track-button').click();
  await expect(page.getByRole('dialog', { name:'Submit New Resource' })).toBeVisible();
  await page.getByRole('dialog').getByLabel('Name', { exact:true }).fill('Submitted health check');
  await page.getByRole('dialog').getByRole('button', { name:'Submit', exact:true }).click();
  await expect(page.locator('.ui-alert-success')).toBeVisible();
});

test('group disclosure, resource details and embed previews remain usable', async ({ page }, testInfo) => {
  await login(page);
  await navigate(page, '/');
  const toggle = page.getByRole('button', { name:'Toggle group resources' }).first();
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded','false');
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded','true');
  await page.locator('.resource-row a[href^="/resources/"]').first().click();
  await expect(page.locator('.resource-detail')).toBeVisible();
  await page.screenshot({ path:testInfo.outputPath('resource-detail.png'), fullPage:true });
  await page.setViewportSize({ width:390, height:900 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await navigate(page, '/admin/embed');
  await expect(page.frameLocator('#embedPreviewIframe').locator('.status-widget')).toBeVisible();
  await page.getByRole('button', { name:'Toggle color theme' }).click();
  await expect(page.locator('#embedPreviewIframe')).toHaveAttribute('src',/mode=dark/);
});

for (const theme of ['light','dark']) {
  for (const width of [390, 834, 1440]) {
    test(`responsive ${theme} ${width}px`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height:1000 });
      await login(page);
      await page.evaluate(theme => localStorage.setItem('theme', theme), theme);
      for (const [route,name] of [['/','dashboard'],['/admin/resources','resources'],['/admin/settings','settings']]) {
        await navigate(page, route);
        await expect(page.locator('html')).toHaveAttribute('data-theme',theme);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), route).toBe(true);
        await page.screenshot({ path:testInfo.outputPath(`${name}.png`), fullPage:true });
      }
      if (width === 390) {
        await page.getByRole('button', { name:'Toggle admin navigation' }).click();
        await expect(page.locator('#admin-sidebar')).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.locator('#admin-sidebar')).toBeHidden();
      }
    });
  }
}
