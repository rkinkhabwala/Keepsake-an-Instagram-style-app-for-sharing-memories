import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { Spinner } from './components/ui';
import { LoginPage, SignupPage } from './pages/AuthPages';
import { CreateMemoryPage } from './pages/CreateMemoryPage';
import { FeedPage } from './pages/FeedPage';
import { MemoryDetailPage, NotFound } from './pages/MemoryDetailPage';
import { ProfilePage } from './pages/ProfilePage';
import { SettingsPage } from './pages/SettingsPage';
import { UserListPage } from './pages/UserListPage';

function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <Spinner label="Starting Keepsake" />;
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

function RedirectIfSignedIn() {
  const { status } = useAuth();
  if (status === 'loading') return <Spinner label="Starting Keepsake" />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <Outlet />;
}

export default function App() {
  return (
    <Routes>
      <Route element={<RedirectIfSignedIn />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<FeedPage />} />
          <Route path="new" element={<CreateMemoryPage />} />
          <Route path="m/:id" element={<MemoryDetailPage />} />
          <Route path="u/:username" element={<ProfilePage />} />
          <Route path="u/:username/followers" element={<UserListPage list="followers" />} />
          <Route path="u/:username/following" element={<UserListPage list="following" />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}
