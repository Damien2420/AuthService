import './App.css'

import * as Sentry from '@sentry/react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'

import { AuthProvider } from './contexts/AuthContext'
import Login from './pages/Login'
import MainPage from './pages/MainPage'
import OAuthCallback from './pages/OAuthCallback'
import ResetPassword from './pages/ResetPassword'
import VerifyEmail from './pages/VerifyEmail'
import Welcome from './pages/Welcome'

const SentryRoutes = Sentry.withSentryReactRouterV7Routing(Routes)

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <SentryRoutes>
          <Route path="/" element={<MainPage />} />
          <Route path="/login" element={<Login />} />
          <Route path="/welcome" element={<Welcome />} />
          <Route path="/auth/callback" element={<OAuthCallback />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
        </SentryRoutes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App