import { MemoryRouter } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginPage } from './LoginPage';

const auth = vi.hoisted(() => ({
  login: vi.fn(),
  admin: undefined as undefined,
  isLoading: false,
}));

vi.mock('../auth/AuthProvider', () => ({
  useAuth: () => ({ ...auth, logout: vi.fn(), refresh: vi.fn() }),
}));

function renderLogin() {
  return render(<MemoryRouter initialEntries={['/login']}><LoginPage /></MemoryRouter>);
}

describe('LoginPage', () => {
  beforeEach(() => auth.login.mockReset());

  it('shows a linked validation summary and inline errors when submitted empty', async () => {
    const user = userEvent.setup();
    renderLogin();

    await user.click(screen.getByRole('button', { name: 'Sign in securely' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Please review the form');
    // Each message is intentionally exposed both in the focusable summary and
    // beside its field, so keyboard and screen-reader users get both contexts.
    expect(screen.getAllByText('Enter a valid work email address.')).toHaveLength(2);
    expect(screen.getAllByText('Enter your password.')).toHaveLength(2);
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('provides an accessible password visibility toggle without changing autocomplete', async () => {
    const user = userEvent.setup();
    renderLogin();
    const password = screen.getByLabelText('Password') as HTMLInputElement;

    expect(password.type).toBe('password');
    expect(password.autocomplete).toBe('current-password');
    await user.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password.type).toBe('text');
    expect(screen.getByRole('button', { name: 'Hide password' })).toBeVisible();
  });
});
