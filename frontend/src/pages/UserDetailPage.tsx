import { type ReactNode, useMemo, useState } from 'react';
import { Link as RouterLink, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Chip, Divider, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import EditRoundedIcon from '@mui/icons-material/EditRounded';
import AddRoundedIcon from '@mui/icons-material/AddRounded';
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded';
import RestoreRoundedIcon from '@mui/icons-material/RestoreRounded';
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined';
import { ApiError, getProblemMessage } from '../api/client';
import { usersApi } from '../api/users';
import type { Address, ApiResult, NavigationState, UserDetail } from '../types';
import { PageError, PageLoading } from '../components/AsyncState';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { InitialAvatar } from '../components/InitialAvatar';
import { StatusChip } from '../components/StatusChip';
import { useToast } from '../components/ToastProvider';

type PendingAction = { kind: 'archive-user' | 'restore-user' } | { kind: 'archive-address' | 'restore-address'; address: Address } | null;

function formatTimestamp(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function formatAddress(address: Address): string {
  return [address.line1, address.line2, [address.city, address.region].filter(Boolean).join(', '), address.postalCode, address.countryCode].filter(Boolean).join(' · ');
}

export function UserDetailPage() {
  const { userId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const userQuery = useQuery({ queryKey: ['user', userId], queryFn: () => usersApi.get(userId), enabled: Boolean(userId) });
  const record = userQuery.data;
  const user = record?.data;
  const etag = record?.etag;
  const returnState = location.state as NavigationState | null;

  const actionMutation = useMutation({
    mutationFn: async (action: Exclude<PendingAction, null>) => {
      if (!('address' in action)) {
        if (!etag) throw new Error('This record is missing its current version. Refresh the page and try again.');
        return action.kind === 'archive-user' ? usersApi.remove(userId, etag) : usersApi.restore(userId, etag);
      }
      const addressRecord = await usersApi.getAddress(userId, action.address.id);
      if (!addressRecord.etag) throw new Error('This address is missing its current version. Refresh the page and try again.');
      if (action.kind === 'archive-address') return usersApi.removeAddress(userId, action.address.id, addressRecord.etag);
      return usersApi.restoreAddress(userId, action.address.id, addressRecord.etag);
    },
    onSuccess: (result, action) => {
      const isRestore = action.kind.startsWith('restore');
      if (action.kind === 'archive-user' || action.kind === 'restore-user') {
        queryClient.setQueryData<ApiResult<UserDetail>>(['user', userId], (previous) => previous ? { ...previous, etag: result.etag ?? previous.etag, data: { ...previous.data, deleted: !isRestore, deletedAt: isRestore ? null : new Date().toISOString() } } : previous);
      }
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      void queryClient.invalidateQueries({ queryKey: ['user', userId] });
      setPendingAction(null);
      showToast({ severity: 'success', message: isRestore ? 'The record has been restored.' : 'The record has been archived.' });
    },
    onError: (error) => {
      setPendingAction(null);
      if (error instanceof ApiError && error.status === 412) {
        void userQuery.refetch();
        showToast({ severity: 'warning', message: 'This record changed elsewhere. We reloaded the latest version.' });
      } else {
        showToast({ severity: 'error', message: getProblemMessage(error) });
      }
    },
  });

  const orderedAddresses = useMemo(() => (user?.addresses ?? []).slice().sort((a, b) => a.displayOrder - b.displayOrder), [user?.addresses]);
  const activeAddresses = orderedAddresses.filter((address) => !address.deleted);
  const archivedAddresses = orderedAddresses.filter((address) => address.deleted);
  const goBack = () => navigate(returnState?.returnTo ?? '/users', { state: returnState?.scrollY === undefined ? undefined : { restoreScroll: returnState.scrollY } satisfies NavigationState });

  if (userQuery.isLoading) return <PageLoading rows={6} />;
  if (userQuery.isError || !user) return <PageError error={userQuery.error} onRetry={() => void userQuery.refetch()} title="We could not load this profile." />;

  const editState = { returnTo: returnState?.returnTo ?? '/users', scrollY: returnState?.scrollY } satisfies NavigationState;
  return (
    <Box>
      <Button startIcon={<ArrowBackRoundedIcon />} color="inherit" onClick={goBack} sx={{ mb: 2, minHeight: 40, px: 1 }}>Back to directory</Button>
      {user.deleted && <Alert severity="warning" sx={{ mb: 2.5 }}>This profile is archived. It is hidden from the default directory and can be restored without losing its record.</Alert>}
      <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2.5} sx={{ alignItems: 'flex-start' }}>
        <Box sx={{ flex: 1, width: '100%', minWidth: 0 }}>
          <Card sx={{ mb: 2.25 }}>
            <CardContent sx={{ p: { xs: 2, sm: 3 }, '&:last-child': { pb: { xs: 2, sm: 3 } } }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}>
                <Stack direction="row" spacing={1.75} sx={{ alignItems: 'center' }}><InitialAvatar firstName={user.firstName} lastName={user.lastName} size={48} /><Box><Typography component="h1" variant="h1" sx={{ fontSize: 25 }}>{user.firstName} {user.lastName}</Typography><Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}><Typography variant="body2" color="text.secondary">{user.email}</Typography><StatusChip deleted={user.deleted} /></Stack></Box></Stack>
                {user.deleted ? <Button variant="contained" startIcon={<RestoreRoundedIcon />} onClick={() => setPendingAction({ kind: 'restore-user' })}>Restore profile</Button> : <Button component={RouterLink} to={`/users/${user.id}/edit`} state={editState} variant="outlined" startIcon={<EditRoundedIcon />}>Edit profile</Button>}
              </Stack>
            </CardContent>
          </Card>
          <SectionCard title="Profile details" subtitle="Information visible to administrators" action={!user.deleted ? <Button component={RouterLink} to={`/users/${user.id}/edit`} state={editState} size="small" startIcon={<EditRoundedIcon />}>Edit</Button> : undefined}>
            <Stack divider={<Divider flexItem />} direction={{ xs: 'column', sm: 'row' }} sx={{ flexWrap: 'wrap' }}>
              <Detail label="First name" value={user.firstName} />
              <Detail label="Last name" value={user.lastName} />
              <Detail label="Email" value={user.email} />
              <Detail label="Created" value={formatTimestamp(user.createdAt)} />
            </Stack>
          </SectionCard>
          <SectionCard title="Addresses" subtitle={`${activeAddresses.length} ${activeAddresses.length === 1 ? 'address' : 'addresses'} on file`} action={!user.deleted ? <Button component={RouterLink} to={`/users/${user.id}/addresses/new`} state={editState} size="small" variant="contained" startIcon={<AddRoundedIcon />}>Add address</Button> : undefined}>
            {activeAddresses.length ? activeAddresses.map((address, index) => <AddressRow key={address.id} user={user} address={address} index={index + 1} navigationState={editState} archived={false} onArchive={() => setPendingAction({ kind: 'archive-address', address })} />) : <EmptyAddresses disabled={user.deleted} />}
            {archivedAddresses.length > 0 && <Box sx={{ p: 2, bgcolor: 'background.default', borderTop: '1px solid', borderColor: 'divider' }}><Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '.07em' }}>Archived addresses</Typography><Stack spacing={1} sx={{ mt: 1.5 }}>{archivedAddresses.map((address) => <AddressRow key={address.id} user={user} address={address} index={address.displayOrder} navigationState={editState} archived onArchive={() => setPendingAction({ kind: 'restore-address', address })} />)}</Stack></Box>}
          </SectionCard>
        </Box>
        <Box component="aside" sx={{ width: { xs: '100%', lg: 300 }, flexShrink: 0 }} aria-label="Profile record actions">
          <Card><CardContent><Typography variant="subtitle1" sx={{ mb: 1 }}>Profile status</Typography><Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{user.deleted ? 'Restore this profile to return it to the active directory.' : 'Archive this profile to remove it from the default directory. The record remains recoverable.'}</Typography>{user.deleted ? <Button fullWidth startIcon={<RestoreRoundedIcon />} onClick={() => setPendingAction({ kind: 'restore-user' })}>Restore profile</Button> : <Button fullWidth color="error" variant="outlined" startIcon={<DeleteOutlineRoundedIcon />} onClick={() => setPendingAction({ kind: 'archive-user' })}>Archive profile</Button>}</CardContent></Card>
        </Box>
      </Stack>
      <Outlet />
      <ConfirmDialog
        open={Boolean(pendingAction)}
        title={pendingAction?.kind.startsWith('restore') ? 'Restore this record?' : 'Archive this record?'}
        description={pendingAction?.kind === 'archive-user' ? 'The profile will be hidden from the default directory. Its information remains available for restoration.' : pendingAction?.kind === 'restore-user' ? 'The profile will return to the active directory.' : pendingAction?.kind === 'archive-address' ? `The ${pendingAction.address.label} address will be archived and can be restored later.` : `The ${pendingAction?.kind === 'restore-address' ? pendingAction.address.label : ''} address will become active again.`}
        confirmLabel={pendingAction?.kind.startsWith('restore') ? 'Restore' : 'Archive'}
        tone={pendingAction?.kind.startsWith('restore') ? 'primary' : 'error'}
        busy={actionMutation.isPending}
        onCancel={() => setPendingAction(null)}
        onConfirm={() => pendingAction && actionMutation.mutate(pendingAction)}
      />
    </Box>
  );
}

function SectionCard({ title, subtitle, action, children }: { title: string; subtitle: string; action?: ReactNode; children: ReactNode }) {
  return <Card component="section" sx={{ mb: 2.25 }}><Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', justifyContent: 'space-between', px: { xs: 2, sm: 2.5 }, py: 1.75, borderBottom: '1px solid', borderColor: 'divider' }}><Box><Typography component="h2" variant="h3">{title}</Typography><Typography variant="caption" color="text.secondary">{subtitle}</Typography></Box>{action}</Stack>{children}</Card>;
}

function Detail({ label, value }: { label: string; value: string }) {
  return <Box sx={{ flex: '1 1 50%', minWidth: { xs: '100%', sm: 220 }, px: { xs: 2, sm: 2.5 }, py: 2 }}><Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 700, letterSpacing: '.06em', textTransform: 'uppercase' }}>{label}</Typography><Typography variant="body2" sx={{ mt: .5, fontWeight: 600, overflowWrap: 'anywhere' }}>{value}</Typography></Box>;
}

function EmptyAddresses({ disabled }: { disabled: boolean }) {
  return <Box sx={{ px: 2.5, py: 4, textAlign: 'center' }}><HomeWorkOutlinedIcon aria-hidden="true" color="action" /><Typography sx={{ mt: 1, fontWeight: 700 }}>No addresses on file</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>{disabled ? 'Restore the profile before adding an address.' : 'Add an address when one is available.'}</Typography></Box>;
}

function AddressRow({ user, address, index, navigationState, archived, onArchive }: { user: UserDetail; address: Address; index: number; navigationState: NavigationState; archived: boolean; onArchive: () => void }) {
  return (
    <Stack component="article" direction="row" spacing={1.5} sx={{ alignItems: 'flex-start', px: { xs: 2, sm: 2.5 }, py: 2, borderBottom: archived ? 0 : '1px solid', borderColor: 'divider', opacity: archived ? 0.8 : 1 }}>
      <Box aria-hidden="true" sx={{ display: 'grid', placeItems: 'center', flex: '0 0 auto', width: 28, height: 28, border: '1px solid', borderColor: 'divider', borderRadius: 1, color: 'text.secondary', fontSize: 11, fontWeight: 700 }}>{index}</Box>
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="body2" sx={{ fontWeight: 700 }}>{address.label}</Typography>
          {address.primary && <Chip size="small" label="Primary" sx={(theme) => ({ height: 22, bgcolor: 'rgba(var(--mui-palette-success-mainChannel) / 0.14)', color: 'success.dark', fontSize: 10, fontWeight: 700, ...theme.applyStyles('dark', { bgcolor: 'rgba(var(--mui-palette-success-mainChannel) / 0.18)', color: 'success.light' }) })} />}
          {archived && <StatusChip deleted />}
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25, overflowWrap: 'anywhere' }}>{formatAddress(address)}</Typography>
      </Box>
      {!user.deleted && (
        <Stack direction="row" spacing={0.25}>
          {archived ? (
            <Tooltip title="Restore address"><IconButton aria-label={`Restore ${address.label} address`} color="primary" onClick={onArchive}><RestoreRoundedIcon /></IconButton></Tooltip>
          ) : (
            <>
              <Tooltip title="Edit address"><IconButton component={RouterLink} to={`/users/${user.id}/addresses/${address.id}/edit`} state={navigationState} aria-label={`Edit ${address.label} address`}><EditRoundedIcon /></IconButton></Tooltip>
              <Tooltip title="Archive address"><IconButton aria-label={`Archive ${address.label} address`} color="error" onClick={onArchive}><DeleteOutlineRoundedIcon /></IconButton></Tooltip>
            </>
          )}
        </Stack>
      )}
    </Stack>
  );
}
