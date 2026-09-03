import { Alert, Box, Button, Skeleton, Stack, Typography } from '@mui/material';
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded';
import { getProblemMessage } from '../api/client';

export function PageLoading({ rows = 5 }: { rows?: number }) {
  return (
    <Stack spacing={1.5} aria-label="Loading content" aria-busy="true">
      {Array.from({ length: rows }, (_, index) => <Skeleton key={index} variant="rounded" height={64} animation="wave" />)}
    </Stack>
  );
}

export function PageError({ error, onRetry, title = 'We could not load this page.' }: { error: unknown; onRetry: () => void; title?: string }) {
  return (
    <Box sx={{ maxWidth: 600, py: 5 }}>
      <Alert severity="error" action={<Button color="inherit" size="small" startIcon={<RefreshRoundedIcon />} onClick={onRetry}>Try again</Button>}>
        <Typography component="p" sx={{ fontWeight: 700 }}>{title}</Typography>
        <Typography variant="body2">{getProblemMessage(error)}</Typography>
      </Alert>
    </Box>
  );
}
