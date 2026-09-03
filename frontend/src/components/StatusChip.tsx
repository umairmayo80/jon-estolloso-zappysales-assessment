import { Chip } from '@mui/material';

export function StatusChip({ deleted }: { deleted: boolean }) {
  return (
    <Chip
      size="small"
      label={deleted ? 'Archived' : 'Active'}
      sx={deleted
        ? { height: 24, bgcolor: '#F1F5F9', color: '#475569', fontWeight: 700, fontSize: 11 }
        : { height: 24, bgcolor: '#DCFCE7', color: '#166534', fontWeight: 700, fontSize: 11 }}
    />
  );
}
