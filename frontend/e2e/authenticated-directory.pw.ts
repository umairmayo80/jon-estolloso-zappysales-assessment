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

test('administrator can sign in and open the directory', async ({ page }, testInfo) => {
  await login(page);
  await expect(page.getByRole('heading', { name: 'People directory' })).toBeVisible();
  if (testInfo.project.name === 'chromium') {
    await expect(page.getByText('Sardar Umair', { exact: true })).toBeVisible();
  }
});

test('invalid credentials are rejected', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Work email').fill('admin@example.test');
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill('NotTheLocalPassword123!');
  await page.getByRole('button', { name: 'Sign in securely' }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('alert')).toBeVisible();
});

test('desktop directory sorting updates the server request and loads only table rows', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'The grid is replaced with person cards on mobile.');

  const requestedSorts: string[] = [];
  let releaseEmailRequest: (() => void) | undefined;
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: { id: 'admin-1', email: 'admin@example.test', displayName: 'Sardar Umair', role: 'ADMIN' } }));
  await page.route('**/api/v1/users**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const sort = requestUrl.searchParams.get('sort') ?? '';
    requestedSorts.push(sort);
    if (sort === 'email,asc') await new Promise<void>((resolve) => { releaseEmailRequest = resolve; });

    await route.fulfill({ json: {
      content: [{ id: 'maya-1', firstName: 'Maya', lastName: 'Chen', email: 'maya.chen@example.test', addressCount: 2, deleted: false, version: 0, updatedAt: '2026-09-03T13:15:00Z' }],
      page: Number(requestUrl.searchParams.get('page') ?? 0),
      size: 20,
      totalElements: 1,
      totalPages: 1,
      sort,
    } });
  });

  await page.goto('/users?status=active&page=2');
  const personHeader = page.getByRole('columnheader', { name: 'Person' });
  const emailHeader = page.getByRole('columnheader', { name: 'Email' });
  const updatedHeader = page.getByRole('columnheader', { name: 'Last updated' });
  await expect(page.getByRole('grid')).toBeVisible();
  await expect(personHeader).toHaveAttribute('aria-sort', 'ascending');
  await expect(page.getByRole('columnheader', { name: 'Addresses' })).not.toHaveClass('MuiDataGrid-columnHeader--sortable');

  await personHeader.click();
  await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe('firstName,desc');
  await expect.poll(() => requestedSorts).toContain('firstName,desc');

  await emailHeader.click();
  await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe('email,asc');
  await expect.poll(() => releaseEmailRequest).toBeTruthy();
  const loadingRows = page.locator('.directory-grid .MuiDataGrid-skeletonLoadingOverlay');
  await expect(loadingRows).toBeVisible();
  const [headerBox, loadingRowsBox] = await Promise.all([emailHeader.boundingBox(), loadingRows.boundingBox()]);
  expect(loadingRowsBox?.y).toBeGreaterThanOrEqual((headerBox?.y ?? 0) + (headerBox?.height ?? 0) - 1);
  releaseEmailRequest?.();
  await expect(loadingRows).toBeHidden();

  await updatedHeader.click();
  await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe('updatedAt,asc');
  await expect.poll(() => requestedSorts).toContain('updatedAt,asc');
});

test('desktop person cells keep both text lines within their grid cell', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium', 'The grid is replaced with person cards on mobile.');

  await page.route('**/api/v1/auth/me', (route) => route.fulfill({ json: { id: 'admin-1', email: 'admin@example.test', displayName: 'Sardar Umair', role: 'ADMIN' } }));
  await page.route('**/api/v1/users**', (route) => route.fulfill({ json: {
    content: [{ id: 'maya-1', firstName: 'Maya', lastName: 'Chen', email: 'maya.chen@example.test', addressCount: 2, deleted: false, version: 0, updatedAt: '2026-09-03T13:15:00Z' }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    sort: 'firstName,asc',
  } }));

  await page.goto('/users?status=all&page=0');
  const personCell = page.locator('.directory-grid .MuiDataGrid-row').first().locator('[data-field="firstName"]');
  await expect(personCell).toBeVisible();

  const [cell, name, detail] = await Promise.all([
    personCell.boundingBox(),
    personCell.getByText('Maya Chen', { exact: true }).boundingBox(),
    personCell.getByText('Profile record', { exact: true }).boundingBox(),
  ]);
  if (!cell || !name || !detail) throw new Error('Expected the complete person cell to be visible.');

  expect(name.y).toBeGreaterThanOrEqual(cell.y - 0.5);
  expect(detail.y + detail.height).toBeLessThanOrEqual(cell.y + cell.height + 0.5);
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
    await expect(editor).toHaveClass(/MuiDialog-paperFullScreen/);
    const box = await editor.boundingBox();
    const viewport = page.viewportSize();
    expect(box?.width).toBeGreaterThanOrEqual((viewport?.width ?? 0) - 1);
    expect(box?.height).toBeGreaterThanOrEqual((viewport?.height ?? 0) - 1);
    await editor.getByRole('button', { name: 'Close editor' }).click();

    await page.getByRole('button', { name: 'Sign out' }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
});
