import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, CircularProgress, Stack, TextField } from '@mui/material';
import SaveRoundedIcon from '@mui/icons-material/SaveRounded';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError, getProblemMessage } from '../api/client';
import { usersApi } from '../api/users';
import type { ApiResult, NavigationState, UserDetail, UserInput } from '../types';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EditorPanel } from '../components/EditorPanel';
import { FormErrorSummary, fieldId } from '../components/FormErrorSummary';
import { PageLoading } from '../components/AsyncState';
import { useUnsavedChanges } from '../hooks/useUnsavedChanges';
import { useToast } from '../components/ToastProvider';

const userSchema = z.object({
  firstName: z.string().trim().min(1, 'Enter a first name.').max(100, 'First name must be 100 characters or fewer.'),
  lastName: z.string().trim().min(1, 'Enter a last name.').max(100, 'Last name must be 100 characters or fewer.'),
  email: z.string().trim().email('Enter a valid email address.').max(254, 'Email must be 254 characters or fewer.'),
});

function inputFromUser(user: UserDetail): UserInput {
  return { firstName: user.firstName, lastName: user.lastName, email: user.email };
}

function editorClosePath(userId?: string): string {
  return userId ? `/users/${userId}` : '/users';
}

export function UserEditorPage() {
  const { userId } = useParams();
  const isEditing = Boolean(userId);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [submitted, setSubmitted] = useState(false);
  const [serverError, setServerError] = useState<string>();
  const [hasConflict, setHasConflict] = useState(false);
  const userQuery = useQuery({ queryKey: ['user', userId], queryFn: () => usersApi.get(userId!), enabled: isEditing });
  const { register, handleSubmit, reset, setError, formState: { errors, isDirty, isSubmitting } } = useForm<UserInput>({ resolver: zodResolver(userSchema), defaultValues: { firstName: '', lastName: '', email: '' }, mode: 'onBlur' });
  const navigationState = location.state as NavigationState | null;
  const unsavedChanges = useUnsavedChanges(isDirty);

  useEffect(() => {
    // A background refetch must not wipe an administrator's in-progress draft.
    if (userQuery.data?.data && !isDirty && !hasConflict) reset(inputFromUser(userQuery.data.data));
  }, [hasConflict, isDirty, reset, userQuery.data?.data]);

  const mutation = useMutation({
    mutationFn: async (values: UserInput) => {
      if (!userId) return usersApi.create(values);
      const etag = userQuery.data?.etag;
      if (!etag) throw new Error('This profile is missing its current version. Refresh and try again.');
      return usersApi.update(userId, values, etag);
    },
    onSuccess: (result) => {
      const id = result.data.id;
      queryClient.setQueryData<ApiResult<UserDetail>>(['user', id], result);
      void queryClient.invalidateQueries({ queryKey: ['users'] });
      showToast({ severity: 'success', message: isEditing ? 'Profile changes saved.' : 'Profile created.' });
      reset(inputFromUser(result.data));
      unsavedChanges.navigateWithoutPrompt(() => navigate(`/users/${id}`, { replace: true, state: navigationState }));
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 412) {
        setHasConflict(true);
        void userQuery.refetch();
        setServerError(undefined);
        return;
      }
      if (error instanceof ApiError && error.fieldErrors) {
        Object.entries(error.fieldErrors).forEach(([name, message]) => {
          if (name === 'firstName' || name === 'lastName' || name === 'email') setError(name, { type: 'server', message });
        });
      }
      setServerError(getProblemMessage(error, 'We could not save this profile. Please try again.'));
    },
  });

  const close = () => navigate(editorClosePath(userId), { replace: true, state: navigationState });
  const useLatestValues = () => {
    if (userQuery.data?.data) reset(inputFromUser(userQuery.data.data));
    setHasConflict(false);
    setServerError(undefined);
  };
  const onSubmit = async (values: UserInput) => {
    setSubmitted(false);
    setServerError(undefined);
    await mutation.mutateAsync(values).catch(() => undefined);
  };

  if (isEditing && userQuery.isLoading) return <EditorPanel title="Edit profile" subtitle="Loading profile details" onClose={close}><Box sx={{ p: 3 }}><PageLoading rows={4} /></Box></EditorPanel>;
  if (isEditing && (userQuery.isError || !userQuery.data?.data)) return <EditorPanel title="Edit profile" subtitle="Profile unavailable" onClose={close}><Box sx={{ p: 3 }}><Alert severity="error">We could not load this profile. Close the editor and try again.</Alert></Box></EditorPanel>;

  return (
    <>
      <EditorPanel title={isEditing ? 'Edit profile' : 'Add profile'} subtitle={isEditing ? 'Update the profile information below.' : 'Create a new managed profile.'} onClose={close}>
        <Box component="form" noValidate onSubmit={handleSubmit(onSubmit, () => setSubmitted(true))} sx={{ p: { xs: 2, sm: 3 }, pb: 4 }}>
          <FormErrorSummary errors={errors} submitted={submitted} />
          {hasConflict && <Alert severity="warning" sx={{ mb: 2.5 }} action={<Button color="inherit" size="small" onClick={useLatestValues} disabled={userQuery.isFetching}>Use latest values</Button>}>This profile changed elsewhere. Your unsaved edits are still in this form; the latest version is being used for the next save.</Alert>}
          {serverError && <Alert severity="error" sx={{ mb: 2.5 }}>{serverError}</Alert>}
          <Stack spacing={2.25}>
            <TextField id={fieldId<UserInput>('firstName')} label="First name" fullWidth autoComplete="given-name" autoFocus error={Boolean(errors.firstName)} helperText={errors.firstName?.message ?? 'Required'} {...register('firstName')} />
            <TextField id={fieldId<UserInput>('lastName')} label="Last name" fullWidth autoComplete="family-name" error={Boolean(errors.lastName)} helperText={errors.lastName?.message ?? 'Required'} {...register('lastName')} />
            <TextField id={fieldId<UserInput>('email')} label="Email" type="email" fullWidth autoComplete="email" error={Boolean(errors.email)} helperText={errors.email?.message ?? 'Required. This email remains unique even when a profile is archived.'} {...register('email')} />
          </Stack>
          <Stack direction={{ xs: 'column-reverse', sm: 'row' }} spacing={1.25} sx={{ justifyContent: 'flex-end', mt: 4 }}>
            <Button onClick={close} disabled={isSubmitting}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={isSubmitting || (hasConflict && userQuery.isFetching)} startIcon={isSubmitting ? <CircularProgress color="inherit" size={16} /> : <SaveRoundedIcon />}>{isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Create profile'}</Button>
          </Stack>
        </Box>
      </EditorPanel>
      <ConfirmDialog open={unsavedChanges.isBlocked} title="Discard unsaved changes?" description="Your edits have not been saved. If you leave now, they will be lost." confirmLabel="Discard changes" tone="error" onCancel={unsavedChanges.keepEditing} onConfirm={unsavedChanges.discardChanges} />
    </>
  );
}
