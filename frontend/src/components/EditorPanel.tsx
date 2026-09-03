import { type ReactNode, useId } from 'react';
import { Box, Dialog, Drawer, IconButton, Stack, Typography, useMediaQuery } from '@mui/material';
import CloseRoundedIcon from '@mui/icons-material/CloseRounded';
import { useTheme } from '@mui/material/styles';

interface EditorPanelProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  onClose: () => void;
}

export function EditorPanel({ title, subtitle, children, onClose }: EditorPanelProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const titleId = useId();
  const subtitleId = useId();
  const content = (
    <Box sx={{ minHeight: '100%', bgcolor: 'background.paper' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start', justifyContent: 'space-between', position: 'sticky', zIndex: 1, top: 0, p: 2.5, borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.paper', backdropFilter: 'blur(10px)' }}>
        <Box sx={{ minWidth: 0 }}><Typography id={titleId} component="h2" variant="h2">{title}</Typography><Typography id={subtitleId} variant="body2" color="text.secondary" sx={{ mt: .5 }}>{subtitle}</Typography></Box>
        <IconButton aria-label="Close editor" onClick={onClose}><CloseRoundedIcon /></IconButton>
      </Stack>
      {children}
    </Box>
  );

  if (isMobile) {
    return <Dialog fullScreen open onClose={onClose} aria-labelledby={titleId} aria-describedby={subtitleId} scroll="paper" slotProps={{ paper: { sx: { bgcolor: 'background.paper' } } }}>{content}</Dialog>;
  }
  return <Drawer anchor="right" open onClose={onClose} slotProps={{ paper: { role: 'dialog', 'aria-modal': true, 'aria-labelledby': titleId, 'aria-describedby': subtitleId, sx: { width: 'min(100vw, 560px)', bgcolor: 'background.paper' } } }}>{content}</Drawer>;
}
