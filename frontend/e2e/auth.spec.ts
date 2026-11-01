import { test, expect } from '@playwright/test';

test('registration, session navigation and logout clear private views and offline copies', async ({ page }) => {
  let signedIn = false; const writes: string[] = [];
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'browser-fixture' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ status: signedIn ? 200 : 401, json: signedIn ? { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', username: 'browser_user' } : {} });
    if (route.request().method() === 'POST') {
      writes.push(path); expect(route.request().headers()['x-csrf-token']).toBe('browser-fixture');
      if (path.endsWith('/login')) signedIn = true;
      if (path.endsWith('/logout')) signedIn = false;
      return route.fulfill({ status: path.endsWith('/register') ? 201 : 204 });
    }
    return route.fulfill({ json: path.endsWith('/monitoring') ? { schedulerEnabled: false, intervalMs: 300000 } : [] });
  });
  await page.setViewportSize({ width: 390, height: 844 }); await page.goto('/register');
  await page.getByLabel('Username', { exact: true }).fill('browser_user');
  await page.getByLabel('Password', { exact: true }).fill('Browser-fixture-password');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page).toHaveURL(/\/routes$/);
  await expect(page.getByText('Signed in as browser_user')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Saved journeys', exact: true })).toBeVisible();
  await page.evaluate(() => localStorage.setItem('custerra.journey-copies.v1', '[]'));
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Keep your saved evidence private.' })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('custerra.journey-copies.v1'))).toBeNull();
  await expect(page.getByRole('heading', { name: 'Saved journeys', exact: true })).toHaveCount(0);
  expect(writes).toEqual(['/api/v1/auth/register', '/api/v1/auth/login', '/api/v1/auth/logout']);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('login errors remain on the form; expired session clears saved data without redirect loops', async ({ page }) => {
  let signedIn = false; let expired = false;
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'browser-fixture' } });
    if (path.endsWith('/auth/me')) return route.fulfill({ status: signedIn && !expired ? 200 : 401, json: { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', username: 'browser_user' } });
    if (path.endsWith('/auth/login')) return route.fulfill({ status: signedIn ? 204 : 401 });
    if (expired) return route.fulfill({ status: 401, json: {} });
    return route.fulfill({ json: path.endsWith('/monitoring') ? { schedulerEnabled: false, intervalMs: 300000 } : [] });
  });
  await page.goto('/login'); await page.getByLabel('Username', { exact: true }).fill('browser_user');await page.getByLabel('Password', { exact: true }).fill('Browser-fixture-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('main').getByRole('alert')).toContainText('Invalid username or password.');
  signedIn = true;await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Saved journeys', exact: true })).toBeVisible();
  // Let the initial authenticated load finish before expiring the fixture.
  // Otherwise its in-flight response can remove the button before this click.
  await expect(page.getByRole('button', { name: 'Reload journeys' })).toBeEnabled();
  expired = true;await page.getByRole('button', { name: 'Reload journeys' }).click();
  await expect(page.getByText('Your session has ended.', { exact: false })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Saved journeys', exact: true })).toHaveCount(0);
  await expect(page).toHaveURL(/\/routes$/);
});
