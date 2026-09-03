import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { AppBar, Avatar, Box, Button, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Stack, Toolbar, Tooltip, Typography, useMediaQuery } from '@mui/material';
import MenuRoundedIcon from '@mui/icons-material/MenuRounded';
import PeopleAltOutlinedIcon from '@mui/icons-material/PeopleAltOutlined';
import LogoutRoundedIcon from '@mui/icons-material/LogoutRounded';
import { useTheme } from '@mui/material/styles';
import { useAuth } from '../auth/AuthProvider';

const railWidth = 240;

function DirectoryMark() {
  return <Box aria-hidden="true" sx={{ display: 'grid', placeItems: 'center', width: 30, height: 30, borderRadius: 1.5, bgcolor: 'primary.main', color: 'primary.contrastText', fontSize: 10, fontWeight: 800 }}>PD</Box>;
}

function initials(name: string): string {
  return name.split(' ').map((part) => part.at(0)).join('').slice(0, 2).toUpperCase() || 'AD';
}

function Navigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <List sx={{ px: 1.5, pt: 2 }} aria-label="Primary navigation">
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: 1.25, pb: 1, fontSize: 10, fontWeight: 700, letterSpacing: '.09em', textTransform: 'uppercase' }}>Workspace</Typography>
      <ListItemButton
        component={NavLink}
        to="/users"
        onClick={onNavigate}
        sx={{ minHeight: 44, borderRadius: 1, color: 'text.secondary', '&.active': { color: 'primary.dark', bgcolor: '#EFF6FF', fontWeight: 700, '&::before': { content: '""', position: 'absolute', left: -12, top: 8, bottom: 8, width: 3, borderRadius: '0 4px 4px 0', bgcolor: 'primary.main' } } }}
      >
        <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}><PeopleAltOutlinedIcon fontSize="small" /></ListItemIcon>
        <ListItemText primary="Directory" slotProps={{ primary: { sx: { fontSize: 13, fontWeight: 'inherit' } } }} />
      </ListItemButton>
    </List>
  );
}

export function AppShell() {
  const { admin, logout } = useAuth();
  const navigate = useNavigate();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const railContents = (
    <Stack sx={{ height: '100%' }}>
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', height: 64, px: 3 }}>
        <DirectoryMark />
        <Typography sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>Profile Directory</Typography>
      </Stack>
      <Divider />
      <Navigation onNavigate={() => setMobileNavOpen(false)} />
      <Box sx={{ mt: 'auto', p: 2, borderTop: '1px solid', borderColor: 'divider' }}>
        <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center' }}>
          <Avatar sx={{ width: 32, height: 32, bgcolor: '#E0E7FF', color: '#3730A3', fontSize: 11, fontWeight: 700 }}>{initials(admin?.displayName ?? 'Administrator')}</Avatar>
          <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap variant="body2" sx={{ fontWeight: 700 }}>{admin?.displayName ?? 'Administrator'}</Typography><Typography noWrap variant="caption" color="text.secondary">Administrator</Typography></Box>
          <Tooltip title="Sign out"><IconButton aria-label="Sign out" size="small" onClick={() => void handleLogout()}><LogoutRoundedIcon fontSize="small" /></IconButton></Tooltip>
        </Stack>
      </Box>
    </Stack>
  );

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <a className="skip-link" href="#main-content">Skip to main content</a>
      {!isMobile && <Drawer variant="permanent" open slotProps={{ paper: { sx: { width: railWidth, borderRight: '1px solid', borderColor: 'divider', boxSizing: 'border-box' } } }}>{railContents}</Drawer>}
      {isMobile && <Drawer variant="temporary" open={mobileNavOpen} onClose={() => setMobileNavOpen(false)} ModalProps={{ keepMounted: true }} slotProps={{ paper: { sx: { width: 288 } } }}>{railContents}</Drawer>}
      <Box sx={{ ml: { md: `${railWidth}px` }, minHeight: '100vh' }}>
        <AppBar position="sticky" elevation={0} color="inherit" sx={{ borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'rgba(255,255,255,0.94)', backdropFilter: 'blur(12px)' }}>
          <Toolbar sx={{ minHeight: { xs: '58px !important', md: '64px !important' }, px: { xs: 1.5, md: 3.75 } }}>
            {isMobile && <IconButton edge="start" aria-label="Open navigation" onClick={() => setMobileNavOpen(true)} sx={{ mr: 1 }}><MenuRoundedIcon /></IconButton>}
            <Typography variant="body2" color="text.secondary"><Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>Directory</Box><Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}> / Profile management</Box></Typography>
            <Box sx={{ flex: 1 }} />
            {isMobile && <Button aria-label="Sign out" color="inherit" size="small" startIcon={<LogoutRoundedIcon />} onClick={() => void handleLogout()}>Sign out</Button>}
          </Toolbar>
        </AppBar>
        <Box component="main" id="main-content" tabIndex={-1} sx={{ outline: 'none', maxWidth: 1320, mx: 'auto', px: { xs: 1.5, sm: 3, lg: 4 }, py: { xs: 2.5, md: 4 } }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
