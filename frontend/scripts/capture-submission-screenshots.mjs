import { access, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '@playwright/test';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const frontendDirectory = resolve(scriptDirectory, '..');
const repositoryDirectory = resolve(frontendDirectory, '..');
const screenshotDirectory = resolve(repositoryDirectory, 'docs', 'submission', 'assets', 'screenshots');

const baseUrl = normalizeBaseUrl(process.env.CAPTURE_BASE_URL ?? 'http://localhost:5173');
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
const adminEmail = process.env.CAPTURE_ADMIN_EMAIL ?? 'admin@example.test';
const adminPassword = process.env.CAPTURE_ADMIN_PASSWORD ?? 'ChangeMe123!';
const activeFixtureEmail = process.env.CAPTURE_ACTIVE_FIXTURE_EMAIL ?? 'maya.chen@example.test';
const captureTimeout = 15_000;

const desktop = {
  label: 'desktop',
  viewport: { width: 1920, height: 1080 },
  deviceScaleFactor: 1,
  isMobile: false,
  hasTouch: false,
};

const tablet768 = {
  label: 'tablet-768',
  viewport: { width: 768, height: 1024 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
};

const tablet1024 = {
  label: 'tablet-1024',
  viewport: { width: 1024, height: 1365 },
  deviceScaleFactor: 1.5,
  isMobile: false,
  hasTouch: true,
};

const mobile = {
  label: 'mobile',
  viewport: { width: 390, height: 844 },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
};

function normalizeBaseUrl(value) {
  const parsed = new URL(value);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`CAPTURE_BASE_URL must use http or https, received: ${value}`);
  }
  return parsed.toString().replace(/\/$/, '');
}

function appUrl(path) {
  return new URL(path, `${baseUrl}/`).toString();
}

function assert(condition, message) {
  if (!condition) throw new Error(`Capture precondition failed: ${message}`);
}

async function assertVisible(locator, label) {
  try {
    await locator.first().waitFor({ state: 'visible', timeout: captureTimeout });
  } catch {
    throw new Error(`Capture precondition failed: ${label} was not found or is not visible.`);
  }
}

async function assertNoHorizontalOverflow(page, label) {
  const dimensions = await page.evaluate(() => ({
    body: document.body?.scrollWidth ?? 0,
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }));
  const width = Math.max(dimensions.body, dimensions.document);
  assert(width <= dimensions.viewport + 1, `${label} has horizontal overflow (${width}px content in a ${dimensions.viewport}px viewport).`);
}

async function assertColorMode(page, colorMode) {
  await page.waitForFunction((expectedMode) => document.documentElement.dataset.muiColorScheme === expectedMode, colorMode, { timeout: captureTimeout });
}

async function takeScreenshot(page, fileName, { colorMode, fullPage = false } = {}) {
  if (colorMode) await assertColorMode(page, colorMode);
  await page.evaluate(async () => {
    await document.fonts?.ready;
    window.scrollTo(0, 0);
  });
  await assertNoHorizontalOverflow(page, fileName);
  await page.screenshot({
    path: resolve(screenshotDirectory, fileName),
    fullPage,
    animations: 'disabled',
    caret: 'hide',
    scale: 'device',
  });
  console.log(`Captured ${fileName}`);
}

async function createContext(browser, device, colorMode) {
  const context = await browser.newContext({
    baseURL: baseUrl,
    viewport: device.viewport,
    deviceScaleFactor: device.deviceScaleFactor,
    colorScheme: colorMode,
    isMobile: device.isMobile,
    hasTouch: device.hasTouch,
  });
  await context.addInitScript((mode) => {
    localStorage.setItem('profile-directory-mode', mode);
    localStorage.removeItem('profile-directory-color-scheme');
    localStorage.removeItem('profile-directory-color-scheme-light');
    localStorage.removeItem('profile-directory-color-scheme-dark');
  }, colorMode);
  return context;
}

async function waitForLogin(page, colorMode) {
  await page.goto(appUrl('/login'), { waitUntil: 'domcontentloaded' });
  await assertVisible(page.getByRole('heading', { name: 'Welcome back' }), 'Login heading');
  await assertColorMode(page, colorMode);
}

async function authenticate(page) {
  await page.getByLabel('Work email').fill(adminEmail);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(adminPassword);
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  await page.waitForURL((url) => url.pathname === '/users', { timeout: captureTimeout });
  await assertVisible(page.getByRole('heading', { name: 'People directory' }), 'Directory heading after sign-in');
}

async function resolveFixtures(page) {
  return page.evaluate(async ({ fixtureEmail }) => {
    const request = async (path) => {
      const response = await fetch(path, { credentials: 'include' });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(`${path} returned ${response.status}: ${body.detail ?? response.statusText}`);
      return body;
    };

    const activeDirectory = await request('/api/v1/users?status=active&sort=firstName,asc&page=0&size=100');
    const active = activeDirectory.content.find((user) => user.email === fixtureEmail);
    if (!active) throw new Error(`The expected active fixture ${fixtureEmail} was not found. Start the backend with the dev profile and demo data enabled.`);
    const directoryActive = activeDirectory.content[0];
    if (!directoryActive) throw new Error('The active directory unexpectedly contains no profiles.');

    const activeDetail = await request(`/api/v1/users/${encodeURIComponent(active.id)}`);
    const editableAddress = activeDetail.addresses.find((address) => !address.deleted);
    const archivedAddress = activeDetail.addresses.find((address) => address.deleted);
    if (!editableAddress) throw new Error(`The ${fixtureEmail} fixture needs at least one active address.`);
    if (!archivedAddress) throw new Error(`The ${fixtureEmail} fixture needs one archived address for restore evidence.`);

    const archivedDirectory = await request('/api/v1/users?status=deleted&sort=firstName,asc&page=0&size=100');
    const archived = archivedDirectory.content[0];
    if (!archived) throw new Error('No archived profile fixture was found. Seed at least one archived dev profile before capture.');

    const allDirectory = await request('/api/v1/users?status=all&sort=firstName,asc&page=0&size=100');
    const directoryAllActive = allDirectory.content.find((user) => !user.deleted);
    const directoryAllArchived = allDirectory.content.find((user) => user.deleted);
    if (!directoryAllActive || !directoryAllArchived) {
      throw new Error('The all-profiles directory needs both active and archived fixture records.');
    }

    return {
      active: {
        id: active.id,
        email: active.email,
        name: `${active.firstName} ${active.lastName}`,
      },
      archived: {
        id: archived.id,
        email: archived.email,
        name: `${archived.firstName} ${archived.lastName}`,
      },
      directory: {
        activeEmail: directoryActive.email,
        allActiveEmail: directoryAllActive.email,
        allArchivedEmail: directoryAllArchived.email,
      },
      editableAddress: { id: editableAddress.id, label: editableAddress.label },
      archivedAddress: { id: archivedAddress.id, label: archivedAddress.label },
    };
  }, { fixtureEmail: activeFixtureEmail });
}

async function startSession(browser, device, colorMode, loginScreenshot) {
  const context = await createContext(browser, device, colorMode);
  const page = await context.newPage();
  try {
    await waitForLogin(page, colorMode);
    if (loginScreenshot) await takeScreenshot(page, loginScreenshot, { colorMode });
    await authenticate(page);
    const fixtures = await resolveFixtures(page);
    return { context, page, fixtures };
  } catch (error) {
    await context.close();
    throw error;
  }
}

async function visitDirectory(page, status, requiredEmails) {
  const query = new URLSearchParams({ status, sort: 'firstName,asc', page: '0', size: '20' });
  await page.goto(appUrl(`/users?${query}`), { waitUntil: 'domcontentloaded' });
  await assertVisible(page.getByRole('heading', { name: 'People directory' }), `${status} directory heading`);
  for (const email of requiredEmails) await assertVisible(page.getByText(email, { exact: true }), `${status} directory email ${email}`);
}

async function visitProfile(page, profile) {
  await page.goto(appUrl(`/users/${profile.id}`), { waitUntil: 'domcontentloaded' });
  await assertVisible(page.getByRole('heading', { name: profile.name }), `Profile heading for ${profile.email}`);
  await assertVisible(page.getByText(profile.email, { exact: true }), `Profile email ${profile.email}`);
}

async function openEditor(page, title) {
  const editor = page.getByRole('dialog', { name: title });
  await assertVisible(editor, `${title} editor`);
  return editor;
}

async function openProfileCreate(page) {
  await page.getByRole('link', { name: 'Add profile' }).click();
  return openEditor(page, 'Add profile');
}

async function openProfileEdit(page) {
  await page.getByRole('link', { name: 'Edit profile' }).first().click();
  return openEditor(page, 'Edit profile');
}

async function openAddressCreate(page) {
  await page.getByRole('link', { name: 'Add address' }).click();
  return openEditor(page, 'Add address');
}

async function openAddressEdit(page, address) {
  await page.getByRole('link', { name: `Edit ${address.label} address` }).click();
  return openEditor(page, 'Edit address');
}

async function openConfirmation(page, trigger, title) {
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: title });
  await assertVisible(dialog, title);
  return dialog;
}

async function dismissConfirmation(dialog) {
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await dialog.waitFor({ state: 'hidden', timeout: captureTimeout });
}

async function captureDesktopLight(browser) {
  const { context, page, fixtures } = await startSession(browser, desktop, 'light', '01-login-light-desktop.png');
  try {
    await visitDirectory(page, 'active', [fixtures.directory.activeEmail]);
    const search = page.getByRole('textbox', { name: 'Search people by name or email' });
    await search.fill('Maya');
    await page.waitForURL((url) => url.searchParams.get('query') === 'Maya', { timeout: captureTimeout });
    await assertVisible(page.getByText(fixtures.active.email, { exact: true }), 'searched active directory email');
    await takeScreenshot(page, '02-directory-active-light-desktop.png', { colorMode: 'light' });

    await visitDirectory(page, 'all', [fixtures.directory.allActiveEmail, fixtures.directory.allArchivedEmail]);
    await takeScreenshot(page, '03-directory-all-light-desktop.png', { colorMode: 'light' });

    await visitDirectory(page, 'deleted', [fixtures.archived.email]);
    await takeScreenshot(page, '04-directory-archived-light-desktop.png', { colorMode: 'light' });

    await visitProfile(page, fixtures.active);
    await takeScreenshot(page, '05-profile-detail-light-desktop.png', { colorMode: 'light', fullPage: true });

    await visitDirectory(page, 'active', [fixtures.directory.activeEmail]);
    await openProfileCreate(page);
    await takeScreenshot(page, '06-profile-create-light-desktop.png', { colorMode: 'light' });

    await visitProfile(page, fixtures.active);
    await openProfileEdit(page);
    await takeScreenshot(page, '07-profile-edit-light-desktop.png', { colorMode: 'light' });

    await visitProfile(page, fixtures.active);
    await openAddressCreate(page);
    await takeScreenshot(page, '08-address-add-light-desktop.png', { colorMode: 'light', fullPage: true });

    await visitProfile(page, fixtures.active);
    await openAddressEdit(page, fixtures.editableAddress);
    await takeScreenshot(page, '09-address-edit-light-desktop.png', { colorMode: 'light', fullPage: true });

    await visitProfile(page, fixtures.active);
    let dialog = await openConfirmation(page, page.getByRole('button', { name: `Archive ${fixtures.editableAddress.label} address` }), 'Archive this record?');
    await takeScreenshot(page, '10-address-archive-confirm-light-desktop.png', { colorMode: 'light' });
    await dismissConfirmation(dialog);

    dialog = await openConfirmation(page, page.getByRole('button', { name: 'Archive profile' }), 'Archive this record?');
    await takeScreenshot(page, '11-profile-archive-confirm-light-desktop.png', { colorMode: 'light' });
    await dismissConfirmation(dialog);

    await visitProfile(page, fixtures.archived);
    dialog = await openConfirmation(page, page.getByRole('button', { name: 'Restore profile' }).first(), 'Restore this record?');
    await takeScreenshot(page, '12-profile-restore-confirm-light-desktop.png', { colorMode: 'light' });
    await dismissConfirmation(dialog);

    await visitProfile(page, fixtures.active);
    dialog = await openConfirmation(page, page.getByRole('button', { name: `Restore ${fixtures.archivedAddress.label} address` }), 'Restore this record?');
    await takeScreenshot(page, '24-address-restore-confirm-light-desktop.png', { colorMode: 'light' });
    await dismissConfirmation(dialog);
  } finally {
    await context.close();
  }
}

async function captureDesktopDark(browser) {
  const { context, page, fixtures } = await startSession(browser, desktop, 'dark');
  try {
    await visitDirectory(page, 'active', [fixtures.directory.activeEmail]);
    await takeScreenshot(page, '13-directory-dark-desktop.png', { colorMode: 'dark' });

    await visitProfile(page, fixtures.active);
    await takeScreenshot(page, '14-profile-detail-dark-desktop.png', { colorMode: 'dark', fullPage: true });

    await openProfileEdit(page);
    await takeScreenshot(page, '15-profile-editor-dark-desktop.png', { colorMode: 'dark' });
  } finally {
    await context.close();
  }
}

async function captureTabletEvidence(browser) {
  let session = await startSession(browser, tablet768, 'light');
  try {
    await visitDirectory(session.page, 'active', [session.fixtures.directory.activeEmail]);
    await assertVisible(session.page.getByLabel('Profile cards'), '768px profile cards');
    await takeScreenshot(session.page, '16-directory-active-light-tablet-768.png', { colorMode: 'light' });
  } finally {
    await session.context.close();
  }

  session = await startSession(browser, tablet1024, 'light');
  try {
    await visitDirectory(session.page, 'deleted', [session.fixtures.archived.email]);
    await assertVisible(session.page.getByLabel('Profile cards'), '1024px profile cards');
    await takeScreenshot(session.page, '17-directory-archived-light-tablet-1024.png', { colorMode: 'light' });

    await visitProfile(session.page, session.fixtures.active);
    await openProfileEdit(session.page);
    await takeScreenshot(session.page, '25-profile-editor-light-tablet-1024.png', { colorMode: 'light' });
  } finally {
    await session.context.close();
  }

}

async function captureMobileEvidence(browser) {
  let session = await startSession(browser, mobile, 'light', '18-login-light-mobile.png');
  await session.context.close();

  session = await startSession(browser, mobile, 'dark');
  try {
    await visitDirectory(session.page, 'active', [session.fixtures.directory.activeEmail]);
    await assertVisible(session.page.getByLabel('Profile cards'), 'Mobile profile cards');
    await takeScreenshot(session.page, '19-directory-dark-mobile.png', { colorMode: 'dark' });

    await visitProfile(session.page, session.fixtures.active);
    await takeScreenshot(session.page, '20-profile-detail-dark-mobile.png', { colorMode: 'dark', fullPage: true });

    await openProfileEdit(session.page);
    // The full-screen dialog is fixed to the viewport. Full-page capture would
    // repeat it above unrelated background content below the viewport.
    await takeScreenshot(session.page, '21-profile-editor-dark-mobile.png', { colorMode: 'dark' });

    await visitProfile(session.page, session.fixtures.active);
    await openAddressEdit(session.page, session.fixtures.editableAddress);
    await takeScreenshot(session.page, '22-address-editor-dark-mobile.png', { colorMode: 'dark' });

    await visitProfile(session.page, session.fixtures.active);
    let dialog = await openConfirmation(session.page, session.page.getByRole('button', { name: `Archive ${session.fixtures.editableAddress.label} address` }), 'Archive this record?');
    await takeScreenshot(session.page, '26-address-archive-confirm-dark-mobile.png', { colorMode: 'dark' });
    await dismissConfirmation(dialog);

    await visitProfile(session.page, session.fixtures.active);
    await openProfileEdit(session.page);
    const lastName = session.page.getByLabel('Last name');
    await lastName.fill(`${await lastName.inputValue()} draft`);
    await session.page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
    dialog = session.page.getByRole('dialog', { name: 'Discard unsaved changes?' });
    await assertVisible(dialog, 'Dirty navigation confirmation');
    await takeScreenshot(session.page, '23-dirty-confirm-dark-mobile.png', { colorMode: 'dark' });
    await dismissConfirmation(dialog);
  } finally {
    await session.context.close();
  }
}

async function main() {
  if (executablePath) await access(executablePath);
  await mkdir(screenshotDirectory, { recursive: true });

  const launchOptions = {
    executablePath,
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  const browser = await chromium.launch(launchOptions);

  try {
    await captureDesktopLight(browser);
    await captureDesktopDark(browser);
    await captureTabletEvidence(browser);
    await captureMobileEvidence(browser);
  } finally {
    await browser.close();
  }

  console.log(`Captured documentation evidence to ${screenshotDirectory}`);
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
