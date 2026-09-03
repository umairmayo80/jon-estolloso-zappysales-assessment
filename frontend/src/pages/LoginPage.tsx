import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button, CircularProgress, IconButton, InputAdornment, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VerifiedUserOutlinedIcon from '@mui/icons-material/VerifiedUserOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { getProblemMessage } from '../api/client';
import { useAuth } from '../auth/AuthProvider';
import { FormErrorSummary, fieldId } from '../components/FormErrorSummary';

const loginSchema = z.object({
  email: z.string().trim().email('Enter a valid work email address.'),
  password: z.string().min(1, 'Enter your password.'),
});
type LoginValues = z.infer<typeof loginSchema>;

interface LoginLocationState { from?: { pathname: string; search: string }; }

export function LoginPage() {
  const { admin, isLoading, login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string>();
  const [submitted, setSubmitted] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
    mode: 'onBlur',
  });
  const destination = (location.state as LoginLocationState | null)?.from;

  if (!isLoading && admin) return <Navigate to={`${destination?.pathname ?? '/users'}${destination?.search ?? ''}`} replace />;

  const onSubmit = async (values: LoginValues) => {
    setSubmitted(false);
    setServerError(undefined);
    try {
      await login(values.email, values.password);
      navigate(`${destination?.pathname ?? '/users'}${destination?.search ?? ''}`, { replace: true });
    } catch (error) {
      setServerError(getProblemMessage(error, 'We could not sign you in. Check your details and try again.'));
    }
  };

  return (
    <Box sx={{ display: 'grid', minHeight: '100vh', gridTemplateColumns: { md: 'minmax(440px, 1.05fr) minmax(440px, 0.95fr)' }, bgcolor: 'background.paper' }}>
      <Box sx={{ display: { xs: 'none', md: 'flex' }, flexDirection: 'column', justifyContent: 'space-between', p: { md: 6, lg: 8 }, color: '#E2E8F0', bgcolor: '#111827' }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
          <Box aria-hidden="true" sx={{ display: 'grid', placeItems: 'center', width: 32, height: 32, borderRadius: 1.5, bgcolor: 'primary.main', color: '#fff', fontSize: 10, fontWeight: 800 }}>PD</Box>
          <Typography sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>Profile Directory</Typography>
        </Stack>
        <Box sx={{ maxWidth: 490 }}>
          <Typography component="h1" sx={{ mb: 2, color: '#fff', fontSize: { md: 32, lg: 38 }, fontWeight: 700, letterSpacing: '-0.045em', lineHeight: 1.13 }}>Keep every profile and address in one dependable place.</Typography>
          <Typography sx={{ color: '#94A3B8', fontSize: 16, lineHeight: 1.6 }}>A focused administrative workspace for maintaining people records and their associated addresses with care.</Typography>
        </Box>
        <Stack direction="row" spacing={3} sx={{ color: '#CBD5E1' }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><LockOutlinedIcon sx={{ fontSize: 16, color: '#60A5FA' }} /><Typography variant="body2">Secure cookie session</Typography></Stack>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><VerifiedUserOutlinedIcon sx={{ fontSize: 16, color: '#60A5FA' }} /><Typography variant="body2">Administrator access</Typography></Stack>
        </Stack>
      </Box>
      <Box sx={{ display: 'flex', alignItems: { xs: 'flex-start', md: 'center' }, justifyContent: 'center', p: { xs: 2, sm: 4, md: 6 }, bgcolor: 'background.default' }}>
        <Paper component="section" elevation={0} sx={{ width: '100%', maxWidth: 400, border: { xs: '1px solid #E4ECFC', md: 'none' }, bgcolor: { xs: 'background.paper', md: 'transparent' }, p: { xs: 3, sm: 4, md: 0 } }} aria-labelledby="login-heading">
          <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', display: { md: 'none' }, mb: 5 }}>
            <Box aria-hidden="true" sx={{ display: 'grid', placeItems: 'center', width: 30, height: 30, borderRadius: 1.5, bgcolor: 'primary.main', color: '#fff', fontSize: 10, fontWeight: 800 }}>PD</Box>
            <Typography sx={{ fontWeight: 700 }}>Profile Directory</Typography>
          </Stack>
          <Typography component="h2" id="login-heading" variant="h1" sx={{ fontSize: 26, mb: 1 }}>Welcome back</Typography>
          <Typography color="text.secondary" sx={{ mb: 4 }}>Sign in with your administrator account.</Typography>
          <Box component="form" noValidate onSubmit={handleSubmit(onSubmit, () => setSubmitted(true))}>
            <FormErrorSummary errors={errors} submitted={submitted} />
            {serverError && <Typography role="alert" color="error.main" variant="body2" sx={{ mb: 2 }}>{serverError}</Typography>}
            <TextField id={fieldId<LoginValues>('email')} label="Work email" type="email" fullWidth autoComplete="email" autoFocus error={Boolean(errors.email)} helperText={errors.email?.message} {...register('email')} sx={{ mb: 2.25 }} />
            <TextField id={fieldId<LoginValues>('password')} label="Password" type={showPassword ? 'text' : 'password'} fullWidth autoComplete="current-password" error={Boolean(errors.password)} helperText={errors.password?.message} {...register('password')} sx={{ mb: 3 }} slotProps={{ input: { endAdornment: <InputAdornment position="end"><Tooltip title={showPassword ? 'Hide password' : 'Show password'}><IconButton aria-label={showPassword ? 'Hide password' : 'Show password'} edge="end" onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}</IconButton></Tooltip></InputAdornment> } }} />
            <Button type="submit" fullWidth variant="contained" disabled={isSubmitting || isLoading} startIcon={(isSubmitting || isLoading) ? <CircularProgress color="inherit" size={16} /> : <LockOutlinedIcon />}>
              {isSubmitting ? 'Signing in…' : 'Sign in securely'}
            </Button>
          </Box>
          <Typography variant="caption" component="p" color="text.secondary" sx={{ mt: 2.5, textAlign: 'center' }}>Your session is protected with HttpOnly cookies.</Typography>
        </Paper>
      </Box>
    </Box>
  );
}
