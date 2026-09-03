import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Checkbox, CircularProgress, FormControlLabel, Grid, Stack, TextField } from '@mui/material';
import SaveRoundedIcon from '@mui/icons-material/SaveRounded';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { ApiError, getProblemMessage } from '../api/client';
import { usersApi } from '../api/users';
import type { Address, AddressInput, NavigationState } from '../types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EditorPanel } from '../components/EditorPanel';
import { FormErrorSummary, fieldId } from '../components/FormErrorSummary';
import { PageLoading } from '../components/AsyncState';
import { useUnsavedChanges } from '../hooks/useUnsavedChanges';
import { useToast } from '../components/ToastProvider';

const addressSchema = z.object({
  label: z.string().trim().min(1, 'Enter a label, such as Home or Office.').max(80, 'Label must be 80 characters or fewer.'),
  line1: z.string().trim().min(1, 'Enter the first address line.').max(180, 'Address line must be 180 characters or fewer.'),
  line2: z.string().trim().max(180, 'Address line must be 180 characters or fewer.').optional(),
  city: z.string().trim().min(1, 'Enter a city.').max(120, 'City must be 120 characters or fewer.'),
  region: z.string().trim().max(120, 'Region must be 120 characters or fewer.').optional(),
  postalCode: z.string().trim().max(32, 'Postal code must be 32 characters or fewer.').optional(),
  countryCode: z.string().trim().regex(/^[A-Za-z]{2}$/, 'Use a two-letter ISO country code, such as US.').transform((value) => value.toUpperCase()),
  primary: z.boolean(),
  displayOrder: z.number().int().min(0).optional(),
});

function inputFromAddress(address: Address): AddressInput {
  return {
    label: address.label,
    line1: address.line1,
    line2: address.line2 ?? '',
    city: address.city,
    region: address.region ?? '',
    postalCode: address.postalCode ?? '',
    countryCode: address.countryCode,
    primary: address.primary,
    displayOrder: address.displayOrder,
  };
}

export function AddressEditorPage() {
  const { userId = '', addressId } = useParams();
  const isEditing = Boolean(addressId);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [submitted, setSubmitted] = useState(false);
  const [serverError, setServerError] = useState<string>();
  const [hasConflict, setHasConflict] = useState(false);
  const userQuery = useQuery({ queryKey: ['user', userId], queryFn: () => usersApi.get(userId), enabled: Boolean(userId) });
  const addressQuery = useQuery({ queryKey: ['address', userId, addressId], queryFn: () => usersApi.getAddress(userId, addressId!), enabled: isEditing && Boolean(userId) });
  const address = addressQuery.data?.data;
  const navigationState = location.state as NavigationState | null;
  const defaultOrder = useMemo(() => Math.max(0, ...(userQuery.data?.data.addresses.map((item) => item.displayOrder) ?? [-1])) + 1, [userQuery.data?.data.addresses]);
  const { control, register, handleSubmit, reset, setError, formState: { errors, isDirty, isSubmitting } } = useForm<AddressInput>({
    resolver: zodResolver(addressSchema),
    defaultValues: { label: '', line1: '', line2: '', city: '', region: '', postalCode: '', countryCode: 'US', primary: false, displayOrder: 0 },
    mode: 'onBlur',
  });
  const unsavedChanges = useUnsavedChanges(isDirty);

  useEffect(() => {
    // Preserve a draft while stale data is fetched after a concurrency conflict.
    if (address && !isDirty && !hasConflict) reset(inputFromAddress(address));
    else if (!isEditing && userQuery.data?.data && !isDirty && !hasConflict) reset({ label: '', line1: '', line2: '', city: '', region: '', postalCode: '', countryCode: 'US', primary: userQuery.data.data.addresses.filter((item) => !item.deleted).length === 0, displayOrder: defaultOrder });
  }, [address, defaultOrder, hasConflict, isDirty, isEditing, reset, userQuery.data?.data]);

  const mutation = useMutation({
    mutationFn: async (values: AddressInput) => {
      const input = { ...values, line2: values.line2?.trim() || undefined, region: values.region?.trim() || undefined, postalCode: values.postalCode?.trim() || undefined };
      if (!addressId) return usersApi.createAddress(userId, input);
      const etag = addressQuery.data?.etag;
      if (!etag) throw new Error('This profile is missing its current version. Refresh and try again.');
      return usersApi.updateAddress(userId, addressId, input, etag);
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ['user', userId] });
      void queryClient.invalidateQueries({ queryKey: ['address', userId, addressId] });
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      showToast({ severity: 'success', message: isEditing ? 'Address changes saved.' : 'Address added.' });
      reset(inputFromAddress(result.data));
      unsavedChanges.navigateWithoutPrompt(() => navigate(`/users/${userId}`, { replace: true, state: navigationState }));
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 412) {
        setHasConflict(true);
        void userQuery.refetch();
        void addressQuery.refetch();
        setServerError(undefined);
        return;
      }
      if (error instanceof ApiError && error.fieldErrors) {
        Object.entries(error.fieldErrors).forEach(([name, message]) => {
          if (['label', 'line1', 'line2', 'city', 'region', 'postalCode', 'countryCode', 'primary', 'displayOrder'].includes(name)) setError(name as keyof AddressInput, { type: 'server', message });
        });
      }
      setServerError(getProblemMessage(error, 'We could not save this address. Please try again.'));
    },
  });

  const close = () => navigate(`/users/${userId}`, { replace: true, state: navigationState });
  const useLatestValues = () => {
    if (address) reset(inputFromAddress(address));
    setHasConflict(false);
    setServerError(undefined);
  };
  const onSubmit = async (values: AddressInput) => {
    setSubmitted(false);
    setServerError(undefined);
    await mutation.mutateAsync(values).catch(() => undefined);
  };
  const isPrimary = useWatch({ control, name: 'primary' });

  if (userQuery.isLoading || (isEditing && addressQuery.isLoading)) return <EditorPanel title={isEditing ? 'Edit address' : 'Add address'} subtitle="Loading profile details" onClose={close}><Box sx={{ p: 3 }}><PageLoading rows={5} /></Box></EditorPanel>;
  if (userQuery.isError || addressQuery.isError || !userQuery.data?.data || (isEditing && !address)) return <EditorPanel title={isEditing ? 'Edit address' : 'Add address'} subtitle="Address unavailable" onClose={close}><Box sx={{ p: 3 }}><Alert severity="error">We could not load this address. Close the editor and try again.</Alert></Box></EditorPanel>;

  return (
    <>
      <EditorPanel title={isEditing ? 'Edit address' : 'Add address'} subtitle={isEditing ? `Update the ${address?.label ?? ''} address.` : `Add an address for ${userQuery.data.data.firstName} ${userQuery.data.data.lastName}.`} onClose={close}>
        <Box component="form" noValidate onSubmit={handleSubmit(onSubmit, () => setSubmitted(true))} sx={{ p: { xs: 2, sm: 3 }, pb: 4 }}>
          <FormErrorSummary errors={errors} submitted={submitted} />
          {hasConflict && <Alert severity="warning" sx={{ mb: 2.5 }} action={<Button color="inherit" size="small" onClick={useLatestValues} disabled={userQuery.isFetching || addressQuery.isFetching}>Use latest values</Button>}>This address changed elsewhere. Your unsaved edits are still in this form; the latest version is being used for the next save.</Alert>}
          {serverError && <Alert severity="error" sx={{ mb: 2.5 }}>{serverError}</Alert>}
          <Stack spacing={2.25}>
            <TextField id={fieldId<AddressInput>('label')} label="Address label" fullWidth autoFocus error={Boolean(errors.label)} helperText={errors.label?.message ?? 'For example, Home or Office.'} {...register('label')} />
            <TextField id={fieldId<AddressInput>('line1')} label="Address line 1" fullWidth autoComplete="address-line1" error={Boolean(errors.line1)} helperText={errors.line1?.message} {...register('line1')} />
            <TextField id={fieldId<AddressInput>('line2')} label="Address line 2" fullWidth autoComplete="address-line2" error={Boolean(errors.line2)} helperText={errors.line2?.message ?? 'Optional'} {...register('line2')} />
            <Grid container spacing={2.25}>
              <Grid size={{ xs: 12, sm: 6 }}><TextField id={fieldId<AddressInput>('city')} label="City" fullWidth autoComplete="address-level2" error={Boolean(errors.city)} helperText={errors.city?.message} {...register('city')} /></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><TextField id={fieldId<AddressInput>('region')} label="State, region, or province" fullWidth autoComplete="address-level1" error={Boolean(errors.region)} helperText={errors.region?.message ?? 'Optional'} {...register('region')} /></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><TextField id={fieldId<AddressInput>('postalCode')} label="Postal code" fullWidth autoComplete="postal-code" error={Boolean(errors.postalCode)} helperText={errors.postalCode?.message ?? 'Optional'} {...register('postalCode')} /></Grid>
              <Grid size={{ xs: 12, sm: 6 }}><TextField id={fieldId<AddressInput>('countryCode')} label="Country code" fullWidth autoComplete="country" slotProps={{ htmlInput: { maxLength: 2, style: { textTransform: 'uppercase' } } }} error={Boolean(errors.countryCode)} helperText={errors.countryCode?.message ?? 'Use the ISO 3166-1 alpha-2 code, for example US.'} {...register('countryCode')} /></Grid>
            </Grid>
            <Box sx={{ p: 1.75, border: '1px solid', borderColor: 'divider', borderRadius: 1, bgcolor: 'background.default' }}>
              <FormControlLabel control={<Checkbox {...register('primary')} checked={isPrimary} />} label="Make this the primary address" />
              <Box sx={{ ml: 4.5, mt: -.25, color: 'text.secondary', fontSize: 12 }}>A person can have one active primary address. Selecting this updates the current primary address.</Box>
            </Box>
          </Stack>
          <Stack direction={{ xs: 'column-reverse', sm: 'row' }} spacing={1.25} sx={{ justifyContent: 'flex-end', mt: 4 }}><Button onClick={close} disabled={isSubmitting}>Cancel</Button><Button type="submit" variant="contained" disabled={isSubmitting || (hasConflict && (userQuery.isFetching || addressQuery.isFetching))} startIcon={isSubmitting ? <CircularProgress color="inherit" size={16} /> : <SaveRoundedIcon />}>{isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Add address'}</Button></Stack>
        </Box>
      </EditorPanel>
      <ConfirmDialog open={unsavedChanges.isBlocked} title="Discard unsaved changes?" description="Your edits have not been saved. If you leave now, they will be lost." confirmLabel="Discard changes" tone="error" onCancel={unsavedChanges.keepEditing} onConfirm={unsavedChanges.discardChanges} />
    </>
  );
}
