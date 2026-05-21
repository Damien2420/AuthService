import './instruments'
import './index.css'

import * as Sentry from '@sentry/react'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ErrorBoundary } from "react-error-boundary";

import { Toaster } from "@/components/ui/sonner";
import { GlobalErrorFallback } from "@/components/user-defined/GlobalErrorFallback";
import { ThemeProvider } from "@/components/user-defined/theme-provider";

import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider defaultTheme="system" attribute="class">
      <ErrorBoundary
          FallbackComponent={GlobalErrorFallback}
          onError={(error, info) => Sentry.captureReactException(error, info)}
          onReset={() => window.location.reload()}
      >
          <App />
          <Toaster richColors />
      </ErrorBoundary>
    </ThemeProvider>
  </StrictMode>,
)
