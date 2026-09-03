import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth/AuthProvider';
import { ToastProvider } from './components/ToastProvider';
import { appTheme } from './theme';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: (failureCount, error) => failureCount < 1 && !(error instanceof Error && 'status' in error && (error as { status?: number }).status === 401), refetchOnWindowFocus: false },
    mutations: { retry: false },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider
      theme={appTheme}
      defaultMode="system"
      modeStorageKey="profile-directory-mode"
      colorSchemeStorageKey="profile-directory-color-scheme"
      disableTransitionOnChange
      forceThemeRerender
      noSsr
    >
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <AuthProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </AuthProvider>
        </ToastProvider>
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
