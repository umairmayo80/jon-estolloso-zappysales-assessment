import { createTheme } from '@mui/material/styles';

declare module '@mui/material/styles' {
  interface CssThemeVariables {
    enabled: true;
  }
  interface Palette {
    canvas: string;
  }
  interface PaletteOptions {
    canvas?: string;
  }
}

export const appTheme = createTheme({
  cssVariables: { colorSchemeSelector: 'data' },
  colorSchemes: {
    light: {
      palette: {
        mode: 'light',
        primary: { main: '#2563EB', dark: '#1D4ED8', light: '#DBEAFE', contrastText: '#FFFFFF' },
        secondary: { main: '#3B82F6' },
        success: { main: '#047857', dark: '#065F46', light: '#D1FAE5', contrastText: '#FFFFFF' },
        error: { main: '#DC2626', dark: '#B91C1C', light: '#FEE2E2', contrastText: '#FFFFFF' },
        warning: { main: '#B45309', dark: '#92400E', light: '#FEF3C7', contrastText: '#FFFFFF' },
        background: { default: '#F8FAFC', paper: '#FFFFFF' },
        text: { primary: '#0F172A', secondary: '#475569' },
        divider: '#E4ECFC',
        canvas: '#F8FAFC',
      },
    },
    dark: {
      palette: {
        mode: 'dark',
        primary: { main: '#60A5FA', dark: '#3B82F6', light: '#BFDBFE', contrastText: '#0F172A' },
        secondary: { main: '#93C5FD', dark: '#60A5FA', light: '#DBEAFE', contrastText: '#0F172A' },
        success: { main: '#34D399', dark: '#10B981', light: '#A7F3D0', contrastText: '#052E16' },
        error: { main: '#F87171', dark: '#EF4444', light: '#FECACA', contrastText: '#450A0A' },
        warning: { main: '#FBBF24', dark: '#F59E0B', light: '#FDE68A', contrastText: '#451A03' },
        background: { default: '#0F172A', paper: '#172033' },
        text: { primary: '#F8FAFC', secondary: '#CBD5E1' },
        divider: '#334155',
        canvas: '#0F172A',
      },
    },
  },
  typography: {
    fontFamily: 'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    h1: { fontSize: '1.625rem', lineHeight: 1.2, fontWeight: 700, letterSpacing: '-0.035em' },
    h2: { fontSize: '1.25rem', lineHeight: 1.3, fontWeight: 700, letterSpacing: '-0.02em' },
    h3: { fontSize: '1rem', lineHeight: 1.35, fontWeight: 700, letterSpacing: '-0.01em' },
    subtitle1: { fontSize: '0.875rem', lineHeight: 1.5, fontWeight: 600 },
    body1: { fontSize: '0.875rem', lineHeight: 1.5 },
    body2: { fontSize: '0.8125rem', lineHeight: 1.5 },
    caption: { fontSize: '0.6875rem', lineHeight: 1.45, fontWeight: 500 },
    button: { fontWeight: 700, textTransform: 'none', letterSpacing: 0 },
  },
  shape: { borderRadius: 8 },
  spacing: 4,
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        '*': { boxSizing: 'border-box' },
        html: { minHeight: '100%', backgroundColor: 'var(--mui-palette-background-default)' },
        body: { minHeight: '100%', margin: 0, backgroundColor: 'var(--mui-palette-background-default)' },
        '#root': { minHeight: '100vh' },
        '::selection': { backgroundColor: 'rgba(var(--mui-palette-primary-mainChannel) / 0.25)', color: 'var(--mui-palette-text-primary)' },
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': {
            animationDuration: '0.01ms !important',
            animationIterationCount: '1 !important',
            scrollBehavior: 'auto !important',
            transitionDuration: '0.01ms !important',
          },
        },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { minHeight: 44, borderRadius: 8, paddingInline: 16, transition: 'background-color 180ms ease, border-color 180ms ease, color 180ms ease, transform 180ms ease' },
        contained: { '&:hover': { transform: 'translateY(-1px)' } },
      },
    },
    MuiIconButton: {
      styleOverrides: { root: { minWidth: 44, minHeight: 44, borderRadius: 8 } },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          minHeight: 44,
          backgroundColor: 'var(--mui-palette-background-paper)',
          transition: 'border-color 180ms ease, box-shadow 180ms ease',
          '&.Mui-focused': { boxShadow: '0 0 0 3px rgba(var(--mui-palette-primary-mainChannel) / 0.16)' },
          '&.Mui-error.Mui-focused': { boxShadow: '0 0 0 3px rgba(var(--mui-palette-error-mainChannel) / 0.16)' },
        },
        notchedOutline: { borderColor: 'var(--mui-palette-divider)' },
      },
    },
    MuiInputLabel: { styleOverrides: { root: { fontSize: '0.8125rem', fontWeight: 600 } } },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiCard: { styleOverrides: { root: { border: '1px solid var(--mui-palette-divider)', boxShadow: 'none' } } },
    MuiDrawer: { styleOverrides: { paper: { boxShadow: 'var(--mui-shadows-12)' } } },
    MuiDialog: { styleOverrides: { paper: { boxShadow: 'var(--mui-shadows-16)' } } },
    MuiTooltip: { styleOverrides: { tooltip: { fontSize: '0.75rem', fontWeight: 600 } } },
  },
});
