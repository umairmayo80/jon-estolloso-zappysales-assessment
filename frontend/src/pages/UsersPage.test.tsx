import { useLocation, MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UsersPage } from './UsersPage';
import { usersApi } from '../api/users';

vi.mock('../api/users', () => ({ usersApi: { list: vi.fn() } }));

const listMock = vi.mocked(usersApi.list);
const response = {
  content: [{ id: 'marisa-1', firstName: 'Marisa', lastName: 'Watson', email: 'marisa@example.test', addressCount: 2, deleted: false, version: 0, updatedAt: '2026-09-03T10:14:00Z' }],
  page: 2,
  size: 20,
  totalElements: 41,
  totalPages: 3,
  sort: 'lastName,asc',
};

function LocationProbe() {
  return <output data-testid="location-search">{useLocation().search}</output>;
}

function renderDirectory(initialPath = '/users?query=Marisa&status=active&sort=lastName,asc&page=2') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes><Route path="/users" element={<><UsersPage /><LocationProbe /></>} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('UsersPage', () => {
  beforeEach(() => {
    listMock.mockResolvedValue(response);
    Object.defineProperty(window, 'matchMedia', { writable: true, value: vi.fn().mockImplementation(() => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn(), addListener: vi.fn(), removeListener: vi.fn(), dispatchEvent: vi.fn() })) });
    window.scrollTo = vi.fn();
  });
  afterEach(() => vi.restoreAllMocks());

  it('uses the URL state for its API request and renders readable mobile person cards', async () => {
    renderDirectory();

    await screen.findByText('Marisa Watson');
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ query: 'Marisa', status: 'active', sort: 'lastName,asc', page: 2, size: 20 }));
    expect(screen.getByLabelText('Profile cards')).toBeVisible();
    expect(screen.queryByRole('grid')).not.toBeInTheDocument();
    expect(screen.getByText('2 addresses')).toBeVisible();
  });

  it('writes filter changes back to the directory URL', async () => {
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByText('Marisa Watson');

    await user.click(screen.getByLabelText('Status'));
    await user.click(await screen.findByRole('option', { name: 'Archived profiles' }));

    await waitFor(() => expect(screen.getByTestId('location-search')).toHaveTextContent('status=deleted'));
    expect(listMock).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'deleted', page: 0 }));
  });
});
