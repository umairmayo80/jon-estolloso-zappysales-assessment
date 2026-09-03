import { type ReactNode, lazy, Suspense } from 'react';
import { createBrowserRouter, createRoutesFromElements, Link as RouterLink, Navigate, Outlet, Route, RouterProvider, useLocation } from 'react-router-dom';
import { Box, Button, Typography } from '@mui/material';
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded';
import { useAuth } from './auth/AuthProvider';
import { AppShell } from './components/AppShell';
import { PageLoading } from './components/AsyncState';
import { LoginPage } from './pages/LoginPage';

const UsersPage = lazy(() => import('./pages/UsersPage').then((module) => ({ default: module.UsersPage })));
const UserDetailPage = lazy(() => import('./pages/UserDetailPage').then((module) => ({ default: module.UserDetailPage })));
const UserEditorPage = lazy(() => import('./pages/UserEditorPage').then((module) => ({ default: module.UserEditorPage })));
const AddressEditorPage = lazy(() => import('./pages/AddressEditorPage').then((module) => ({ default: module.AddressEditorPage })));

function DeferredPage({ children }: { children: ReactNode }) {
  return <Suspense fallback={<PageLoading rows={5} />}>{children}</Suspense>;
}

function ProtectedRoute() {
  const { admin, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <Box sx={{ maxWidth: 900, mx: 'auto', p: 4 }}><PageLoading rows={5} /></Box>;
  if (!admin) return <Navigate to="/login" replace state={{ from: { pathname: location.pathname, search: location.search } }} />;
  return <Outlet />;
}

function NotFoundPage() {
  return <Box sx={{ maxWidth: 520, mx: 'auto', pt: { xs: 7, md: 12 }, textAlign: 'center' }}><Typography component="h1" variant="h1" data-route-heading="true" tabIndex={-1}>This page is not available</Typography><Typography color="text.secondary" sx={{ mt: 1 }}>The address may be incorrect, or the page may have moved.</Typography><Button component={RouterLink} to="/users" startIcon={<ArrowBackRoundedIcon />} sx={{ mt: 3 }}>Return to directory</Button></Box>;
}

const routes = createRoutesFromElements(
  <Route>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedRoute />}>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/users" replace />} />
        <Route path="users" element={<DeferredPage><UsersPage /></DeferredPage>} />
        <Route path="users/new" element={<DeferredPage><UserEditorPage /></DeferredPage>} />
        <Route path="users/:userId" element={<DeferredPage><UserDetailPage /></DeferredPage>}>
          <Route path="edit" element={<DeferredPage><UserEditorPage /></DeferredPage>} />
          <Route path="addresses/new" element={<DeferredPage><AddressEditorPage /></DeferredPage>} />
          <Route path="addresses/:addressId/edit" element={<DeferredPage><AddressEditorPage /></DeferredPage>} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/users" replace />} />
  </Route>,
);

// Data-router mode is required for useBlocker to intercept browser Back/Forward
// navigation from dirty route-backed editor overlays.
export const appRouter = createBrowserRouter(routes);

export default function App() {
  return <RouterProvider router={appRouter} />;
}
