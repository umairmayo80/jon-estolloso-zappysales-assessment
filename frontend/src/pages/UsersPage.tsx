import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Box, Button, Card, CardActionArea, CardContent, Divider, FormControl, InputAdornment, InputLabel, MenuItem, Select, Stack, TextField, Typography, useMediaQuery } from '@mui/material';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import FilterListRoundedIcon from '@mui/icons-material/FilterListRounded';
import ArrowForwardRoundedIcon from '@mui/icons-material/ArrowForwardRounded';
import { DataGrid, type GridColDef, type GridPaginationModel, type GridSortModel } from '@mui/x-data-grid';
import { useTheme } from '@mui/material/styles';
import { usersApi } from '../api/users';
import type { NavigationState, ProfileStatus, UserSummary } from '../types';
import { InitialAvatar } from '../components/InitialAvatar';
import { PageError, PageLoading } from '../components/AsyncState';
import { StatusChip } from '../components/StatusChip';

const DEFAULT_PAGE_SIZE = 20;
const DEFAULT_SORT = 'firstName,asc';
const SORTABLE_FIELDS = new Set(['firstName', 'email', 'updatedAt']);

function parsePage(value: string | null): number {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function parsePageSize(value: string | null): number {
  const parsed = Number.parseInt(value ?? '', 10);
  return [20, 50, 100].includes(parsed) ? parsed : DEFAULT_PAGE_SIZE;
}

function parseStatus(value: string | null): ProfileStatus {
  return value === 'deleted' || value === 'all' ? value : 'active';
}

function parseSort(value: string | null): string {
  const [field, direction] = (value ?? DEFAULT_SORT).split(',');
  return SORTABLE_FIELDS.has(field) && (direction === 'asc' || direction === 'desc') ? `${field},${direction}` : DEFAULT_SORT;
}

function formatUpdatedAt(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  const today = new Date();
  const sameDay = date.toDateString() === today.toDateString();
  return sameDay ? `Today, ${new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(date)}` : new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: date.getFullYear() !== today.getFullYear() ? 'numeric' : undefined }).format(date);
}

function PersonCell({ user, secondary = 'Profile record', wrapText = false }: { user: UserSummary; secondary?: string; wrapText?: boolean }) {
  return (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', minWidth: 0, flex: 1 }}>
      <InitialAvatar firstName={user.firstName} lastName={user.lastName} size={32} />
      <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap={!wrapText} variant="body2" sx={{ fontWeight: 700, overflowWrap: wrapText ? 'anywhere' : undefined }}>{user.firstName} {user.lastName}</Typography><Typography component="div" noWrap={!wrapText} variant="caption" color="text.secondary" sx={{ minWidth: 0, overflowWrap: wrapText ? 'anywhere' : undefined }}>{secondary}</Typography></Box>
    </Stack>
  );
}

export function UsersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  // The permanent 240px rail leaves too little room for the six-column grid
  // until the large desktop breakpoint. Cards keep 900–1199px layouts readable.
  const showMobileCards = useMediaQuery(theme.breakpoints.down('lg'));
  const query = searchParams.get('query') ?? '';
  const status = parseStatus(searchParams.get('status'));
  const sort = parseSort(searchParams.get('sort'));
  const page = parsePage(searchParams.get('page'));
  const size = parsePageSize(searchParams.get('size'));
  const [selectedId, setSelectedId] = useState<string>();
  const [filtersVisible, setFiltersVisible] = useState(false);

  const params = useMemo(() => ({ query, status, sort, page, size }), [page, query, size, sort, status]);
  const usersQuery = useQuery({ queryKey: ['users', params], queryFn: () => usersApi.list(params), placeholderData: (previous) => previous });

  useEffect(() => {
    const restoreScroll = (location.state as NavigationState | null)?.restoreScroll;
    if (typeof restoreScroll === 'number') requestAnimationFrame(() => window.scrollTo({ top: restoreScroll, behavior: 'auto' }));
  }, [location.key, location.state]);
  const updateParams = useCallback((updates: Record<string, string | undefined>) => {
    const next = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      if (value === undefined) next.delete(key);
      else next.set(key, value);
    });
    setSearchParams(next);
  }, [searchParams, setSearchParams]);

  const commitSearch = useCallback((value: string) => {
    updateParams({ query: value || undefined, page: '0' });
  }, [updateParams]);

  const openUser = useCallback((id: string) => {
    setSelectedId(id);
    navigate(`/users/${id}`, { state: { returnTo: `/users${location.search}`, scrollY: window.scrollY } satisfies NavigationState });
  }, [location.search, navigate]);

  const [sortField, sortDirection] = sort.split(',') as [string, 'asc' | 'desc'];
  const sortModel: GridSortModel = [{ field: sortField, sort: sortDirection }];
  const paginationModel: GridPaginationModel = { page, pageSize: size };
  const columns = useMemo<GridColDef<UserSummary>[]>(() => [
    { field: 'firstName', headerName: 'Person', flex: 1.1, minWidth: 220, renderCell: ({ row }) => <PersonCell user={row} /> },
    { field: 'email', headerName: 'Email', flex: 1.15, minWidth: 230 },
    { field: 'addressCount', headerName: 'Addresses', width: 112, align: 'center', headerAlign: 'center', sortable: false, renderCell: ({ value }) => <Typography variant="body2">{value} {value === 1 ? 'address' : 'addresses'}</Typography> },
    { field: 'deleted', headerName: 'Status', width: 110, sortable: false, renderCell: ({ value }) => <StatusChip deleted={Boolean(value)} /> },
    { field: 'updatedAt', headerName: 'Last updated', width: 150, renderCell: ({ value }) => <Typography variant="body2" color="text.secondary">{formatUpdatedAt(String(value))}</Typography> },
    { field: 'action', headerName: '', width: 88, sortable: false, filterable: false, renderCell: ({ row }) => <Button size="small" onClick={(event) => { event.stopPropagation(); openUser(row.id); }}>Open</Button> },
  ], [openUser]);

  const content = usersQuery.isLoading
    ? <PageLoading rows={6} />
    : usersQuery.isError
      ? <PageError error={usersQuery.error} onRetry={() => void usersQuery.refetch()} title="We could not load the directory." />
      : (
        <>
          {!showMobileCards && <DataGrid<UserSummary>
            className="directory-grid"
            autoHeight
            rows={usersQuery.data?.content ?? []}
            columns={columns}
            rowCount={usersQuery.data?.totalElements ?? 0}
            getRowId={(row) => row.id}
            paginationMode="server"
            sortingMode="server"
            paginationModel={paginationModel}
            onPaginationModelChange={(model) => updateParams({ page: String(model.page), size: String(model.pageSize) })}
            sortModel={sortModel}
            sortingOrder={['asc', 'desc']}
            onSortModelChange={(model) => {
              const item = model[0];
              if (item?.field && item.sort && SORTABLE_FIELDS.has(item.field)) updateParams({ sort: `${item.field},${item.sort}`, page: '0' });
            }}
            loading={usersQuery.isFetching}
            slotProps={{ loadingOverlay: { variant: 'skeleton' } }}
            pageSizeOptions={[20, 50, 100]}
            disableColumnMenu
            disableRowSelectionOnClick
            onRowClick={({ row }) => openUser(row.id)}
            getRowClassName={({ row }) => row.id === selectedId ? 'is-selected-row' : ''}
            sx={{ border: 0, '--DataGrid-rowBorderColor': 'var(--mui-palette-divider)', '& .MuiDataGrid-footerContainer': { borderTopColor: 'divider' }, '& .MuiDataGrid-cell': { display: 'flex', alignItems: 'center' } }}
          />}
          {showMobileCards && <Stack spacing={1.25} aria-label="Profile cards">
            {(usersQuery.data?.content ?? []).map((user) => <Card key={user.id} data-testid={`profile-card-${user.id}`} sx={{ minWidth: 0, maxWidth: '100%', overflow: 'hidden' }}><CardActionArea onClick={() => openUser(user.id)} sx={{ minHeight: 128, maxWidth: '100%' }}><CardContent sx={{ minWidth: 0, p: 2, '&:last-child': { pb: 2 } }}><Stack direction="row" spacing={1.25} sx={{ alignItems: 'flex-start', justifyContent: 'space-between', minWidth: 0 }}><PersonCell user={user} secondary={user.email} wrapText /><Box sx={{ flex: '0 0 auto' }}><StatusChip deleted={user.deleted} /></Box></Stack><Divider sx={{ my: 1.25 }} /><Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: .25, sm: 1 }} sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between', minWidth: 0, color: 'text.secondary' }}><Typography variant="caption">{user.addressCount} {user.addressCount === 1 ? 'address' : 'addresses'}</Typography><Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>Updated {formatUpdatedAt(user.updatedAt)}</Typography></Stack></CardContent></CardActionArea></Card>) }
            {!usersQuery.data?.content.length && <Box sx={{ py: 5, textAlign: 'center' }}><Typography sx={{ fontWeight: 700 }}>No profiles found</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>Try another search or status filter.</Typography></Box>}
          </Stack>}
        </>
      );

  const displayed = usersQuery.data;
  return (
    <Box>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2.5} sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'flex-start' }, mb: 3 }}>
        <Box><Typography component="h1" variant="h1" data-route-heading="true" tabIndex={-1}>People directory</Typography><Typography color="text.secondary" sx={{ mt: .75 }}>Manage profile records and their associated addresses.</Typography></Box>
        <Button component={RouterLink} to="/users/new" state={{ returnTo: `/users${location.search}`, scrollY: window.scrollY } satisfies NavigationState} variant="contained" startIcon={<AddRoundedIcon />}>Add profile</Button>
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mb: 2.5 }}>
        <Metric label="Profiles found" value={displayed?.totalElements ?? '—'} />
        <Metric label="On this page" value={displayed?.content.length ?? '—'} />
        <Metric label="Addresses shown" value={displayed ? displayed.content.reduce((total, user) => total + user.addressCount, 0) : '—'} />
      </Stack>
      <Card component="section" aria-label="Directory list">
        <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.25} sx={{ alignItems: { lg: 'center' }, p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
          <DirectorySearch key={query} initialValue={query} onCommit={commitSearch} />
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" startIcon={<FilterListRoundedIcon />} onClick={() => setFiltersVisible((visible) => !visible)} aria-expanded={filtersVisible}>Filters</Button>
            <FormControl size="small" sx={{ minWidth: 150, display: { xs: filtersVisible ? 'block' : 'none', lg: 'block' } }}>
              <InputLabel id="status-filter-label">Status</InputLabel>
              <Select labelId="status-filter-label" label="Status" value={status} onChange={(event) => updateParams({ status: event.target.value, page: '0' })}>
                <MenuItem value="active">Active profiles</MenuItem><MenuItem value="deleted">Archived profiles</MenuItem><MenuItem value="all">All profiles</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        </Stack>
        <Box sx={{ minHeight: 360 }}>{content}</Box>
        {showMobileCards && displayed && displayed.totalPages > 1 && <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between', p: 1.5, borderTop: '1px solid', borderColor: 'divider' }}><Typography variant="caption">Page {displayed.page + 1} of {displayed.totalPages}</Typography><Stack direction="row" spacing={1}><Button size="small" disabled={page === 0} onClick={() => updateParams({ page: String(page - 1) })}>Previous</Button><Button size="small" disabled={page + 1 >= displayed.totalPages} endIcon={<ArrowForwardRoundedIcon />} onClick={() => updateParams({ page: String(page + 1) })}>Next</Button></Stack></Stack>}
      </Card>
    </Box>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return <Card sx={{ flex: 1, minWidth: 0 }}><Box sx={{ px: 2, py: 1.5 }}><Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, letterSpacing: '.04em', textTransform: 'uppercase' }}>{label}</Typography><Typography sx={{ mt: .25, fontWeight: 700, fontSize: 20, letterSpacing: '-.025em' }}>{value}</Typography></Box></Card>;
}

function DirectorySearch({ initialValue, onCommit }: { initialValue: string; onCommit: (value: string) => void }) {
  const [value, setValue] = useState(initialValue);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const nextValue = value.trim();
      if (nextValue !== initialValue) onCommit(nextValue);
    }, 320);
    return () => window.clearTimeout(timer);
  }, [initialValue, onCommit, value]);

  return <TextField label="Search people" value={value} onChange={(event) => setValue(event.target.value)} placeholder="Name or email" fullWidth size="small" slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRoundedIcon color="action" /></InputAdornment> }, htmlInput: { 'aria-label': 'Search people by name or email' } }} />;
}
