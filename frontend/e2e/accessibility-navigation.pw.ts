import { expect, test, type Page } from '@playwright/test';

// These tests alter viewport size and exercise browser history. Keep their
// mocked navigation journeys independent of each other rather than competing
// for a development server during a responsive transition.
test.describe.configure({ mode: 'serial' });

interface MockProfile {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  deleted: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
  addresses: [];
}

function createProfile(email = 'maya.chen@example.test'): MockProfile {
  return {
    id: 'maya-1',
    firstName: 'Maya',
    lastName: 'Chen',
    email,
    deleted: false,
    version: 0,
    createdAt: '2026-09-03T13:15:00Z',
    updatedAt: '2026-09-03T13:15:00Z',
    addresses: [],
  };
}

async function mockAuthenticatedProfile(page: Page, email?: string) {
  let profile = createProfile(email);

  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: { id: 'admin-1', email: 'admin@example.test', displayName: 'Sardar Umair', role: 'ADMIN' } }));
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ json: { token: 'test-csrf-token' } }));
  await page.route('**/api/v1/users**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === '/api/v1/users' && request.method() === 'GET') {
      await route.fulfill({ json: {
        content: [{ id: profile.id, firstName: profile.firstName, lastName: profile.lastName, email: profile.email, addressCount: 0, deleted: profile.deleted, version: profile.version, updatedAt: profile.updatedAt }],
        page: Number(url.searchParams.get('page') ?? 0),
        size: Number(url.searchParams.get('size') ?? 20),
        totalElements: 1,
        totalPages: 1,
        sort: url.searchParams.get('sort') ?? 'firstName,asc',
      } });
      return;
    }

    if (path === `/api/v1/users/${profile.id}` && request.method() === 'GET') {
      await route.fulfill({ json: profile, headers: { ETag: `"${profile.version}"` } });
      return;
    }

    if (path === `/api/v1/users/${profile.id}` && request.method() === 'PATCH') {
      const patch = request.postDataJSON() as Partial<Pick<MockProfile, 'firstName' | 'lastName' | 'email'>>;
      profile = { ...profile, ...patch, version: profile.version + 1, updatedAt: '2026-09-03T14:00:00Z' };
      await route.fulfill({ json: profile, headers: { ETag: `"${profile.version}"` } });
      return;
    }

    await route.fulfill({ status: 404, json: { status: 404, title: 'Not found' } });
  });
}

async function openProfileEditor(page: Page) {
  await page.goto('/users/maya-1');
  await expect(page.getByRole('heading', { name: 'Maya Chen' })).toBeVisible();
  await page.getByRole('link', { name: 'Edit profile' }).click();
  await expect(page.getByRole('dialog', { name: 'Edit profile' })).toBeVisible();
  await expect(page.getByLabel('First name')).toHaveValue('Maya');
}

test('responsive profile cards expose email and do not overflow at 390px, 768px, and 1024px', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Custom responsive widths are covered once in the desktop browser project.');
  const longEmail = 'maya.chen.with.a.very.long.mailbox.for.responsive.layout.testing@example.test';
  await mockAuthenticatedProfile(page, longEmail);

  for (const viewport of [{ width: 390, height: 844 }, { width: 768, height: 1024 }, { width: 1024, height: 1365 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/users');

    const cards = page.getByLabel('Profile cards');
    await expect(cards).toBeVisible();
    await expect(cards.getByText(longEmail)).toBeVisible();
    const card = page.getByTestId('profile-card-maya-1');
    await expect(card).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await card.getByRole('button').click();
    await expect(page).toHaveURL(/\/users\/maya-1$/);
    await expect(page.getByText(longEmail, { exact: true }).first()).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  }
});

test('full-page navigation focuses route headings while mobile editors isolate focus and the background', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-chrome', 'The full-screen editor behavior is specific to the mobile project.');
  await mockAuthenticatedProfile(page);
  await page.goto('/users/maya-1/edit');

  const editor = page.getByRole('dialog', { name: 'Edit profile' });
  await expect(editor).toBeVisible();
  await expect(page.getByLabel('First name')).toBeFocused();
  expect(await page.locator('#main-content').evaluate((element) => {
    let node: Element | null = element;
    while (node) {
      if (node.getAttribute('aria-hidden') === 'true') return true;
      node = node.parentElement;
    }
    return false;
  })).toBe(true);

  await editor.getByRole('button', { name: 'Close editor' }).focus();
  await page.keyboard.press('Shift+Tab');
  expect(await editor.evaluate((element) => element.contains(document.activeElement))).toBe(true);

  await page.keyboard.press('Escape');
  await expect(page).toHaveURL(/\/users\/maya-1$/);
  await expect(page.getByRole('heading', { name: 'Maya Chen' })).toBeFocused();
});

test('dirty navigation supports browser Back, stay, discard, and a successful-save bypass', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'The navigation behavior is covered once at desktop density.');
  await mockAuthenticatedProfile(page);
  await openProfileEditor(page);

  const lastName = page.getByLabel('Last name');
  await lastName.fill('Draft');
  await page.goBack();

  const discardDialog = page.getByRole('dialog', { name: 'Discard unsaved changes?' });
  await expect(discardDialog).toBeVisible();
  await discardDialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(lastName).toHaveValue('Draft');
  await expect(page).toHaveURL(/\/users\/maya-1\/edit$/);

  await page.getByRole('button', { name: 'Close editor' }).click();
  await expect(discardDialog).toBeVisible();
  await discardDialog.getByRole('button', { name: 'Discard changes' }).click();
  await expect(page).toHaveURL(/\/users\/maya-1$/);

  await page.getByRole('link', { name: 'Edit profile' }).click();
  await expect(page.getByRole('dialog', { name: 'Edit profile' })).toBeVisible();
  await page.getByLabel('Last name').fill('Updated');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page).toHaveURL(/\/users\/maya-1$/);
  await expect(page.getByRole('heading', { name: 'Maya Updated' })).toBeVisible();
  await expect(page.getByRole('dialog', { name: 'Discard unsaved changes?' })).toHaveCount(0);
});

test('directory and profile pages move focus to their primary heading after full-page navigation', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'Heading focus is browser-size independent and covered once at desktop density.');
  await mockAuthenticatedProfile(page);
  await page.goto('/users');

  await expect(page.getByRole('heading', { name: 'People directory' })).toBeFocused();
  await page.getByRole('button', { name: 'Open' }).click();
  await expect(page.getByRole('heading', { name: 'Maya Chen' })).toBeFocused();
});
