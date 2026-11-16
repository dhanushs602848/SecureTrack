import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { Sidebar } from './components/Sidebar';
import { Login } from './pages/Login';
import { Register } from './pages/Register';
import { Overview } from './pages/Overview';
import { Devices } from './pages/Devices';
import { LiveTracking } from './pages/LiveTracking';
import { LocationHistory } from './pages/LocationHistory';
import { SecurityEvents } from './pages/SecurityEvents';
import { Commands } from './pages/Commands';
import { Settings } from './pages/Settings';

const ProtectedLayout: React.FC = () => {
  const { token, loading } = useAuth();

  if (loading) {
    return <div className="min-h-screen bg-[#F8F9FB] flex items-center justify-center text-[#152C4F] font-bold">Loading SecureTrack...</div>;
  }

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="min-h-screen bg-[#F8F9FB] flex flex-col">
      <Navbar />
      <div className="flex flex-1">
        <Sidebar />
        <main className="flex-1 p-8 overflow-y-auto">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/devices" element={<Devices />} />
            <Route path="/tracking" element={<LiveTracking />} />
            <Route path="/history" element={<LocationHistory />} />
            <Route path="/security" element={<SecurityEvents />} />
            <Route path="/commands" element={<Commands />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/*" element={<ProtectedLayout />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
