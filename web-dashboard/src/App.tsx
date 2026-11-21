import React, { Suspense, lazy, useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { Navbar } from './components/Navbar';
import { Sidebar } from './components/Sidebar';
import { RealtimeAlertBanner } from './components/RealtimeAlertBanner';

// Route-level code-splitting for optimal loading speed and lightweight initial bundle
const Login = lazy(() => import('./pages/Login').then(m => ({ default: m.Login })));
const Register = lazy(() => import('./pages/Register').then(m => ({ default: m.Register })));
const Overview = lazy(() => import('./pages/Overview').then(m => ({ default: m.Overview })));
const Devices = lazy(() => import('./pages/Devices').then(m => ({ default: m.Devices })));
const LiveTracking = lazy(() => import('./pages/LiveTracking').then(m => ({ default: m.LiveTracking })));
const LocationHistory = lazy(() => import('./pages/LocationHistory').then(m => ({ default: m.LocationHistory })));
const SecurityEvents = lazy(() => import('./pages/SecurityEvents').then(m => ({ default: m.SecurityEvents })));
const Commands = lazy(() => import('./pages/Commands').then(m => ({ default: m.Commands })));
const Settings = lazy(() => import('./pages/Settings').then(m => ({ default: m.Settings })));
const LostPhoneRecovery = lazy(() => import('./pages/LostPhoneRecovery').then(m => ({ default: m.LostPhoneRecovery })));

const LoadingFallback: React.FC = () => (
  <div className="flex-1 min-h-[400px] flex items-center justify-center">
    <div className="flex flex-col items-center gap-3">
      <div className="w-8 h-8 border-2 border-indigo-600 dark:border-cyan-400 border-t-transparent rounded-full animate-spin" />
      <span className="text-xs font-semibold text-slate-500 dark:text-slate-400 tracking-wider">LOADING SECURETRACK...</span>
    </div>
  </div>
);

const ProtectedLayout: React.FC = () => {
  const { token, loading } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 dark:bg-[#0A0E17] flex items-center justify-center text-indigo-600 dark:text-cyan-400 font-bold">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-2 border-indigo-600 dark:border-cyan-400 border-t-transparent rounded-full animate-spin" />
          <span className="text-xs font-semibold tracking-wider text-slate-500 dark:text-slate-400">INITIALIZING SECURITY SESSION...</span>
        </div>
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-[#0A0E17] text-slate-900 dark:text-slate-100 flex flex-col transition-colors duration-200">
      <Navbar
        mobileMenuOpen={mobileMenuOpen}
        onToggleMobileMenu={() => setMobileMenuOpen(prev => !prev)}
      />
      <RealtimeAlertBanner />
      <div className="flex flex-1 relative">
        <Sidebar
          isOpen={mobileMenuOpen}
          onClose={() => setMobileMenuOpen(false)}
        />
        <main className="flex-1 p-4 sm:p-6 md:p-8 overflow-y-auto w-full">
          <Suspense fallback={<LoadingFallback />}>
            <Routes>
              <Route path="/" element={<Overview />} />
              <Route path="/devices" element={<Devices />} />
              <Route path="/tracking" element={<LiveTracking />} />
              <Route path="/history" element={<LocationHistory />} />
              <Route path="/security" element={<SecurityEvents />} />
              <Route path="/commands" element={<Commands />} />
              <Route path="/recovery" element={<LostPhoneRecovery />} />
              <Route path="/settings" element={<Settings />} />
            </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Suspense fallback={<LoadingFallback />}>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/*" element={<ProtectedLayout />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
};

export default App;
