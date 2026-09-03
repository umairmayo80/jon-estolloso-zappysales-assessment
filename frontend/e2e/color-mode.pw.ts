import { expect, test, type Page } from '@playwright/test';

async function mockDirectory(page: Page) {
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: { id: 'admin-1', email: 'admin@example.test', displayName: 'Local Administrator', role: 'ADMIN' } }));
  await page.route('**/api/v1/users**', (route) => route.fulfill({ json: {
    content: [{ id: 'maya-1', firstName: 'Maya', lastName: 'Chen', email: 'maya.chen@example.test', addressCount: 2, deleted: false, version: 0, updatedAt: '2026-09-03T13:15:00Z' }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    sort: 'lastName,asc',
  } }));
  await page.route('**/api/v1/users/maya-1', (route) => route.fulfill({ json: {
    id: 'maya-1',
    firstName: 'Maya',
    lastName: 'Chen',
    email: 'maya.chen@example.test',
    deleted: false,
    version: 0,
    createdAt: '2026-09-03T13:15:00Z',
    updatedAt: '2026-09-03T13:15:00Z',
    addresses: [],
  }, headers: { etag: '"0"' } }));
}

async function clearColorMode(page: Page) {
  await page.addInitScript(() => {
    if (sessionStorage.getItem('profile-directory-color-mode-test-cleared')) return;
    localStorage.removeItem('profile-directory-mode');
    localStorage.removeItem('profile-directory-color-scheme');
    localStorage.removeItem('profile-directory-color-scheme-light');
    localStorage.removeItem('profile-directory-color-scheme-dark');
    sessionStorage.setItem('profile-directory-color-mode-test-cleared', 'true');
  });
}

test('uses the OS scheme first, persists choices, and restores System mode', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'The preference behavior is covered once at desktop density.');
  await page.emulateMedia({ colorScheme: 'dark' });
  await clearColorMode(page);
  await mockDirectory(page);
  await page.goto('/users');

  const html = page.locator('html');
  const trigger = page.getByRole('button', { name: /^Color mode/i });
  await expect(html).toHaveAttribute('data-dark', '');
  await expect(html).toHaveAttribute('data-mui-color-scheme', 'dark');
  await expect(trigger).toHaveAttribute('aria-label', /System.*currently dark/i);

  await trigger.focus();
  await page.keyboard.press('Enter');
  const menu = page.getByRole('menu');
  await expect(menu).toBeVisible();
  await expect(menu.getByRole('menuitemradio', { name: 'System' })).toHaveAttribute('aria-checked', 'true');
  await expect(menu.getByRole('menuitemradio', { name: 'Light' })).toHaveAttribute('aria-checked', 'false');
  await expect(menu.getByRole('menuitemradio', { name: 'Dark' })).toHaveAttribute('aria-checked', 'false');

  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(html).toHaveAttribute('data-light', '');
  await expect(html).toHaveAttribute('data-mui-color-scheme', 'light');
  await expect.poll(() => page.evaluate(() => localStorage.getItem('profile-directory-mode'))).toBe('light');
  await expect(trigger).toBeFocused();

  await page.reload();
  await expect(html).toHaveAttribute('data-light', '');
  await expect(html).toHaveAttribute('data-mui-color-scheme', 'light');

  await trigger.focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(html).toHaveAttribute('data-dark', '');
  await expect(html).toHaveAttribute('data-mui-color-scheme', 'dark');
  await expect.poll(() => page.evaluate(() => localStorage.getItem('profile-directory-mode'))).toBe('dark');

  await trigger.focus();
  await page.keyboard.press('Enter');
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();

  await trigger.press('Enter');
  await menu.getByRole('menuitemradio', { name: 'System' }).click();
  await expect(html).toHaveAttribute('data-dark', '');
  await expect(html).toHaveAttribute('data-mui-color-scheme', 'dark');
  await expect.poll(() => page.evaluate(() => localStorage.getItem('profile-directory-mode'))).toBe('system');

  await page.emulateMedia({ colorScheme: 'light' });
  await expect(html).toHaveAttribute('data-light', '');
  await expect(html).toHaveAttribute('data-mui-color-scheme', 'light');
});

test('exposes the same accessible color-mode control on the login page', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await clearColorMode(page);
  await page.goto('/login');

  const trigger = page.getByRole('button', { name: /^Color mode/i });
  await expect(trigger).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await trigger.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('menuitemradio', { name: 'System' })).toHaveAttribute('aria-checked', 'true');
  await page.keyboard.press('Escape');
  await expect(trigger).toBeFocused();
});

test('names the desktop profile editor drawer', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'The desktop drawer is not used on mobile.');
  await page.emulateMedia({ colorScheme: 'dark' });
  await clearColorMode(page);
  await mockDirectory(page);
  await page.goto('/users/maya-1/edit');

  await expect(page.getByRole('dialog', { name: 'Edit profile' })).toBeVisible();
});

test('keeps the mobile header color control within the viewport', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-chrome', 'This layout is specific to the mobile header.');
  await clearColorMode(page);
  await mockDirectory(page);
  await page.goto('/users');

  const trigger = page.getByRole('button', { name: /^Color mode/i });
  await expect(trigger).toBeVisible();
  const signOut = page.getByRole('button', { name: 'Sign out' });
  const navigation = page.getByRole('button', { name: 'Open navigation' });
  const [box, signOutBox, navigationBox, viewport, scrolls] = await Promise.all([
    trigger.boundingBox(),
    signOut.boundingBox(),
    navigation.boundingBox(),
    page.viewportSize(),
    page.evaluate(() => ({ width: document.documentElement.scrollWidth, viewport: window.innerWidth })),
  ]);
  expect(navigationBox?.x).toBeGreaterThanOrEqual(0);
  expect(box?.x).toBeGreaterThanOrEqual(0);
  expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual((viewport?.width ?? 0) + 0.5);
  expect(signOutBox?.x).toBeGreaterThanOrEqual((box?.x ?? 0) + (box?.width ?? 0) + 7.5);
  expect(scrolls.width).toBeLessThanOrEqual(scrolls.viewport);
});
