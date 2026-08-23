import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { ProtectedRoute } from '@/features/auth/ProtectedRoute'
import { useAuth } from '@/features/auth/AuthContext'
import { AppLayout } from '@/layouts/AppLayout'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { AssetsPage } from '@/pages/AssetsPage'
import { AssetDetailPage } from '@/pages/AssetDetailPage'
import { ProjectsPage } from '@/pages/ProjectsPage'
import { ProjectDetailPage } from '@/pages/ProjectDetailPage'
import { ClientProjectsPage } from '@/pages/ClientProjectsPage'
import { CollectionsPage } from '@/pages/CollectionsPage'
import { CollectionDetailPage } from '@/pages/CollectionDetailPage'
import { ClientsPage } from '@/pages/ClientsPage'
import { ClientDetailPage } from '@/pages/ClientDetailPage'
import { AnalyticsPage } from '@/pages/AnalyticsPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

// A client-only account (see CurrentUser.isClientOnly) has a deliberately simplified UI: the
// nav already only offers Client Projects (see navItems.ts), and this gate backs that up by
// redirecting away from every other page too, in case one is reached directly by URL. Project
// and asset detail pages stay reachable -- that's where a client actually reads a project and
// writes client notes -- everything else (Dashboard, the full Assets/Projects/Collections/
// Clients lists, Analytics) is producer-facing and has nothing to show a client-only account.
const CLIENT_ALLOWED_PATH = /^\/(client-projects|projects\/[^/]+|assets\/[^/]+)$/

function ClientOnlyGate() {
  const { user } = useAuth()
  const location = useLocation()

  if (user?.isClientOnly && !CLIENT_ALLOWED_PATH.test(location.pathname)) {
    return <Navigate to="/client-projects" replace />
  }

  return <Outlet />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route element={<ClientOnlyGate />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/assets" element={<AssetsPage />} />
            <Route path="/assets/:id" element={<AssetDetailPage />} />
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/projects/:id" element={<ProjectDetailPage />} />
            <Route path="/client-projects" element={<ClientProjectsPage />} />
            <Route path="/collections" element={<CollectionsPage />} />
            <Route path="/collections/:id" element={<CollectionDetailPage />} />
            <Route path="/clients" element={<ClientsPage />} />
            <Route path="/clients/:id" element={<ClientDetailPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
