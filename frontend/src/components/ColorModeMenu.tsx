import { useEffect, useId, useState } from 'react';
import { IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Tooltip } from '@mui/material';
import { useColorScheme } from '@mui/material/styles';
import BrightnessAutoRoundedIcon from '@mui/icons-material/BrightnessAutoRounded';
import CheckRoundedIcon from '@mui/icons-material/CheckRounded';
import DarkModeRoundedIcon from '@mui/icons-material/DarkModeRounded';
import LightModeRoundedIcon from '@mui/icons-material/LightModeRounded';

type ColorMode = 'system' | 'light' | 'dark';

const modeLabels: Record<ColorMode, string> = {
  system: 'System',
  light: 'Light',
  dark: 'Dark',
};

function modeIcon(mode: ColorMode) {
  if (mode === 'system') return <BrightnessAutoRoundedIcon />;
  return mode === 'dark' ? <DarkModeRoundedIcon /> : <LightModeRoundedIcon />;
}

function prepaintedMode(): 'light' | 'dark' {
  return typeof document !== 'undefined' && document.documentElement.dataset.muiColorScheme === 'dark' ? 'dark' : 'light';
}

export function ColorModeMenu() {
  const { mode, systemMode, colorScheme, setMode } = useColorScheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const menuId = useId();
  const selectedMode: ColorMode = mode ?? 'system';
  const effectiveMode: 'light' | 'dark' = selectedMode === 'system' ? (systemMode ?? colorScheme ?? prepaintedMode()) as 'light' | 'dark' : selectedMode;
  const effectiveLabel = modeLabels[effectiveMode];
  const triggerLabel = `Color mode: ${modeLabels[selectedMode]}. Currently ${effectiveLabel}. Change color mode`;
  const open = Boolean(anchorEl);

  useEffect(() => {
    if (!mode) return;
    const root = document.documentElement;
    root.setAttribute('data-mui-color-scheme', effectiveMode);
    root.style.colorScheme = effectiveMode;
  }, [effectiveMode, mode]);

  const chooseMode = (nextMode: ColorMode) => {
    setMode(nextMode);
    setAnchorEl(null);
  };

  return (
    <>
      <Tooltip title={triggerLabel}>
        <IconButton
          aria-controls={open ? menuId : undefined}
          aria-expanded={open ? 'true' : undefined}
          aria-haspopup="menu"
          aria-label={triggerLabel}
          color="inherit"
          onClick={(event) => setAnchorEl(event.currentTarget)}
        >
          {modeIcon(effectiveMode)}
        </IconButton>
      </Tooltip>
      <Menu
        id={menuId}
        anchorEl={anchorEl}
        open={open}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ list: { 'aria-label': 'Color mode' } }}
      >
        {(Object.keys(modeLabels) as ColorMode[]).map((option) => {
          const selected = selectedMode === option;
          return (
            <MenuItem key={option} role="menuitemradio" aria-checked={selected} selected={selected} onClick={() => chooseMode(option)}>
              <ListItemIcon>{selected ? <CheckRoundedIcon /> : modeIcon(option)}</ListItemIcon>
              <ListItemText>{modeLabels[option]}</ListItemText>
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
}
