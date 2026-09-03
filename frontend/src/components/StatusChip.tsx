import { Chip } from '@mui/material';

export function StatusChip({ deleted }: { deleted: boolean }) {
  return (
    <Chip
      size="small"
      label={deleted ? 'Archived' : 'Active'}
      sx={(theme) => ({
        height: 24,
        bgcolor: deleted ? 'action.selected' : 'rgba(var(--mui-palette-success-mainChannel) / 0.14)',
        color: deleted ? 'text.secondary' : 'success.dark',
        fontWeight: 700,
        fontSize: 11,
        ...theme.applyStyles('dark', {
          bgcolor: deleted ? 'rgba(var(--mui-palette-text-primaryChannel) / 0.08)' : 'rgba(var(--mui-palette-success-mainChannel) / 0.18)',
          color: deleted ? 'text.secondary' : 'success.light',
        }),
      })}
    />
  );
}
