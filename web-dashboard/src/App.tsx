import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { Sidebar } from './components/Sidebar';

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
      <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
      <span className="text-xs font-semibold text-slate-400 tracking-wider">LOADING SECURETRACK...</span>
    </div>
  </div>
);

const ProtectedLayout: React.FC = () => {
  const { token, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0A0E17] flex items-center justify-center text-cyan-400 font-bold">
        <div className="flex flex-col items-center gap-3">
          <div className="w-10 h-10 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
          <span className="text-xs font-semibold tracking-wider text-slate-400">INITIALIZING SECURITY SESSION...</span>
        </div>
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-[#0A0E17] text-slate-100 flex flex-col">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="flex-1 p-6 md:p-8 overflow-y-auto">
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
  );
};

export default App;
