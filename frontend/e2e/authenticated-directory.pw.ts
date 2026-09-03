import { expect, test, type Page } from '@playwright/test';

async function login(page: Page) {
  const email = process.env.E2E_ADMIN_EMAIL ?? 'admin@example.test';
  const password = process.env.E2E_ADMIN_PASSWORD ?? 'ChangeMe123!';

  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await page.getByLabel('Work email').fill(email);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in securely' }).click();

  await expect(page).toHaveURL(/\/users/);
}

test('administrator can sign in and open the directory', async ({ page }) => {
  await login(page);
  await expect(page.getByRole('heading', { name: 'People directory' })).toBeVisible();
});

test.describe('live profile lifecycle', () => {
  test.setTimeout(90_000);

  test('administrator can create, edit, archive, restore, and sign out', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium', 'The complete workflow is covered once at desktop density.');
    await login(page);

    const suffix = `${Date.now()}-${testInfo.parallelIndex}`;
    const firstName = 'E2E';
    const lastName = `Profile${suffix}`;
    const updatedLastName = `Updated${suffix}`;
    const email = `e2e.${suffix}@example.test`;
    const addressLabel = `E2E office ${suffix}`;

    await page.getByRole('link', { name: 'Add profile' }).click();
    await page.getByLabel('First name').fill(firstName);
    await page.getByLabel('Last name').fill(lastName);
    await page.getByLabel('Email').fill(email);
    await page.getByRole('button', { name: 'Create profile' }).click();

    await expect(page).toHaveURL(/\/users\/[^/]+$/);
    await expect(page.getByRole('heading', { name: `${firstName} ${lastName}` })).toBeVisible();

    await page.getByRole('link', { name: 'Edit profile' }).click();
    await page.getByLabel('Last name').fill(updatedLastName);
    await page.getByRole('button', { name: 'Save changes' }).click();
    await expect(page.getByRole('heading', { name: `${firstName} ${updatedLastName}` })).toBeVisible();

    await page.getByRole('link', { name: 'Add address' }).click();
    await page.getByLabel('Address label').fill(addressLabel);
    await page.getByLabel('Address line 1').fill('100 Test Avenue');
    await page.getByLabel('City').fill('Testville');
    await page.getByLabel('Country code').fill('US');
    await page.getByRole('button', { name: 'Add address' }).last().click();
    await expect(page.getByText(addressLabel, { exact: true })).toBeVisible();

    await page.getByRole('button', { name: `Archive ${addressLabel} address` }).click();
    const archiveDialog = page.getByRole('dialog');
    await expect(archiveDialog).toBeVisible();
    await archiveDialog.getByRole('button', { name: 'Archive', exact: true }).click();
    await expect(page.getByText('Archived addresses', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: `Restore ${addressLabel} address` })).toBeVisible();

    await page.getByRole('button', { name: `Restore ${addressLabel} address` }).click();
    const restoreDialog = page.getByRole('dialog');
    await expect(restoreDialog).toBeVisible();
    await restoreDialog.getByRole('button', { name: 'Restore', exact: true }).click();
    await expect(page.getByText('Archived addresses', { exact: true })).toHaveCount(0);
    await expect(page.getByText(addressLabel, { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  });

  test('mobile navigation presents cards and a full-screen route-backed editor', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile-chrome', 'This responsive assertion is covered in the mobile project.');
    await login(page);

    const cards = page.getByLabel('Profile cards');
    await expect(cards).toBeVisible();
    await expect(cards.locator('.MuiCardActionArea-root').first()).toBeVisible();
    await cards.locator('.MuiCardActionArea-root').first().click();
    await expect(page).toHaveURL(/\/users\/[^/]+$/);

    await page.getByRole('link', { name: 'Edit profile' }).click();
    const editor = page.getByRole('dialog', { name: 'Edit profile' });
    await expect(editor).toBeVisible();
    await expect(editor).toHaveCSS('position', 'fixed');
    const box = await editor.boundingBox();
    const viewport = page.viewportSize();
    expect(box?.width).toBeGreaterThanOrEqual((viewport?.width ?? 0) - 1);
    expect(box?.height).toBeGreaterThanOrEqual((viewport?.height ?? 0) - 1);
    await editor.getByRole('button', { name: 'Close editor' }).click();

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
});
