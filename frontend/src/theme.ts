import { createTheme } from '@mui/material/styles';

declare module '@mui/material/styles' {
  interface Palette {
    canvas: string;
  }
  interface PaletteOptions {
    canvas?: string;
  }
}

export const appTheme = createTheme({
  cssVariables: true,
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
        ':root': { colorScheme: 'light' },
        '*': { boxSizing: 'border-box' },
        html: { minHeight: '100%', backgroundColor: '#F8FAFC' },
        body: { minHeight: '100%', margin: 0, backgroundColor: '#F8FAFC' },
        '#root': { minHeight: '100vh' },
        '::selection': { backgroundColor: '#BFDBFE', color: '#0F172A' },
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
        root: { minHeight: 44, backgroundColor: '#FFFFFF', transition: 'border-color 180ms ease, box-shadow 180ms ease' },
        notchedOutline: { borderColor: '#CBD5E1' },
      },
    },
    MuiInputLabel: { styleOverrides: { root: { fontSize: '0.8125rem', fontWeight: 600 } } },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiCard: { styleOverrides: { root: { border: '1px solid #E4ECFC', boxShadow: 'none' } } },
    MuiDrawer: { styleOverrides: { paper: { boxShadow: '-12px 0 30px rgba(15, 23, 42, 0.10)' } } },
    MuiDialog: { styleOverrides: { paper: { boxShadow: '0 16px 35px rgba(15, 23, 42, 0.14)' } } },
    MuiTooltip: { styleOverrides: { tooltip: { fontSize: '0.75rem', fontWeight: 600 } } },
  },
});
