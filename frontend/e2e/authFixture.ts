import type { Route } from '@playwright/test';
export function authFixture(route: Route): Promise<void> | false {
  const path = new URL(route.request().url()).pathname;
  if (path.endsWith('/auth/me')) return route.fulfill({ json: { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', username: 'fixture_user' } });
  if (path.endsWith('/auth/csrf')) return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-token' } });
  return false;
}
