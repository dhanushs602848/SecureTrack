import React, { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  Smartphone,
  ShieldAlert,
  ShieldCheck,
  Wifi,
  MapPin,
  ExternalLink,
  Plus,
  Terminal,
  Clock,
  ArrowRight,
  Shield,
  AlertTriangle,
  Info
} from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { deviceService, securityService, locationService } from '../services/api';
import { Device, SecurityEvent, LocationRecord } from '../types';
import { useTheme } from '../context/ThemeContext';
import { EmptyState } from '../components/ui/EmptyState';

export const Overview: React.FC = () => {
  const { resolvedTheme } = useTheme();
  const [devices, setDevices] = useState<Device[]>([]);
  const [alerts, setAlerts] = useState<SecurityEvent[]>([]);
  const [latestLocations, setLatestLocations] = useState<LocationRecord[]>([]);
  const [showReleaseModal, setShowReleaseModal] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubDevices = deviceService.subscribeToDevices((data) => {
      setDevices(data);
      setLoading(false);
    });

    const unsubAlerts = securityService.subscribeToSecurityEvents((data) => {
      setAlerts(data);
    });

    return () => {
      unsubDevices();
      unsubAlerts();
    };
  }, []);

  useEffect(() => {
    if (devices.length === 0) {
      setLatestLocations([]);
      return;
    }
    const unsubscribes = devices.map((d) =>
      locationService.subscribeToLocationHistory(d.id, (history) => {
        if (history.length > 0) {
          setLatestLocations((prev) => {
            const filtered = prev.filter((l) => l.deviceId !== d.id);
            return [...filtered, history[0]];
          });
        }
      })
    );
    return () => unsubscribes.forEach((unsub) => unsub());
  }, [devices]);

  // Accurate Metrics Computation
  const activeDevices = useMemo(() => devices.filter((d) => d.status !== 'REVOKED'), [devices]);
  const onlineDevices = useMemo(() => activeDevices.filter((d) => d.status === 'ONLINE').length, [activeDevices]);
  const activeAlerts = useMemo(() => alerts.filter((a) => (a.status || 'ACTIVE') === 'ACTIVE'), [alerts]);
  const criticalAlerts = useMemo(
    () => activeAlerts.filter((a) => a.severity === 'CRITICAL' || a.severity === 'HIGH').length,
    [activeAlerts]
  );

  // Protected devices count: requires protectionStatus === 'ACTIVE' and locationStatus !== 'DISABLED'
  const fullyProtectedCount = useMemo(() => {
    return activeDevices.filter(
      (d) => (d.protectionStatus === 'ACTIVE' || d.protectionEnabled) && d.locationStatus !== 'DISABLED'
    ).length;
  }, [activeDevices]);

  // Real 24-hour event aggregation (No fabricated statistics)
  const chartData = useMemo(() => {
    const now = Date.now();
    const oneDayAgo = now - 24 * 60 * 60 * 1000;
    const recentAlerts = alerts.filter((a) => {
      const ts = new Date(a.timestamp).getTime();
      return !isNaN(ts) && ts >= oneDayAgo;
    });

    if (recentAlerts.length === 0) {
      return null; // Signals empty state for actual data
    }

    // 6 intervals of 4 hours
    const intervals: { label: string; start: number; end: number; count: number }[] = [];
    for (let i = 5; i >= 0; i--) {
      const end = now - i * 4 * 60 * 60 * 1000;
      const start = end - 4 * 60 * 60 * 1000;
      const timeStr = new Date(end).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      intervals.push({ label: timeStr, start, end, count: 0 });
    }

    recentAlerts.forEach((a) => {
      const ts = new Date(a.timestamp).getTime();
      const match = intervals.find((int) => ts >= int.start && ts < int.end);
      if (match) {
        match.count += 1;
      }
    });

    return intervals.map((int) => ({ time: int.label, alerts: int.count }));
  }, [alerts]);

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800';
      case 'HIGH':
        return 'bg-orange-100 dark:bg-orange-950/60 text-orange-800 dark:text-orange-300 border-orange-300 dark:border-orange-800';
      case 'WARNING':
        return 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800';
      default:
        return 'bg-cyan-100 dark:bg-cyan-950/60 text-cyan-800 dark:text-cyan-300 border-cyan-300 dark:border-cyan-800';
    }
  };

  const isDark = resolvedTheme === 'dark';

  return (
    <div className="space-y-6 max-w-7xl mx-auto transition-colors duration-200 min-w-0">
      {/* Header and Quick Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4">
        <div className="min-w-0">
          <h1 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
            Security Overview
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Real-time status of protected Android devices and defense telemetry
          </p>
        </div>
        <div className="flex items-center gap-2 sm:gap-3 flex-wrap sm:flex-nowrap">
          <button
            type="button"
            onClick={() => setShowReleaseModal(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold bg-white dark:bg-[#162032] hover:bg-slate-50 dark:hover:bg-[#1d2a42] text-slate-700 dark:text-slate-200 border border-slate-300 dark:border-slate-700 shadow-xs transition-colors shrink-0"
          >
            <Info className="w-3.5 h-3.5 text-indigo-600 dark:text-cyan-400" />
            Release &amp; App Build
          </button>
          <Link
            to="/devices"
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-colors shrink-0"
          >
            <Plus className="w-3.5 h-3.5" />
            Pair Device
          </Link>
        </div>
      </div>

      {/* 4 Core Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {/* Total Devices */}
        <div className="bg-white dark:bg-[#121927] p-4 sm:p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm flex items-center gap-3.5 sm:gap-4 min-w-0">
          <div className="w-11 h-11 sm:w-12 sm:h-12 bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800/60 rounded-xl flex items-center justify-center shrink-0">
            <Smartphone className="w-5 h-5 sm:w-6 sm:h-6" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] sm:text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">TOTAL DEVICES</p>
            <h2 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white font-mono mt-0.5">
              {activeDevices.length}
            </h2>
            <span className="text-[11px] text-slate-500 block truncate">
              {devices.length - activeDevices.length > 0
                ? `${devices.length - activeDevices.length} revoked`
                : 'Enrolled under account'}
            </span>
          </div>
        </div>

        {/* Online Devices */}
        <div className="bg-white dark:bg-[#121927] p-4 sm:p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm flex items-center gap-3.5 sm:gap-4 min-w-0">
          <div className="w-11 h-11 sm:w-12 sm:h-12 bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/60 rounded-xl flex items-center justify-center shrink-0">
            <Wifi className="w-5 h-5 sm:w-6 sm:h-6" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] sm:text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">ONLINE NOW</p>
            <h2 className="text-xl sm:text-2xl font-bold text-emerald-600 dark:text-emerald-400 font-mono mt-0.5">
              {onlineDevices}
            </h2>
            <span className="text-[11px] text-slate-500 block truncate">
              {activeDevices.length > 0
                ? `${Math.round((onlineDevices / activeDevices.length) * 100)}% active connectivity`
                : 'No devices connected'}
            </span>
          </div>
        </div>

        {/* Verified Protection Status (Never assumes 0 alerts = protected) */}
        <div className="bg-white dark:bg-[#121927] p-4 sm:p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm flex items-center gap-3.5 sm:gap-4 min-w-0">
          <div
            className={`w-11 h-11 sm:w-12 sm:h-12 rounded-xl flex items-center justify-center border shrink-0 ${
              activeDevices.length > 0 && fullyProtectedCount === activeDevices.length
                ? 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800/60'
                : 'bg-amber-50 dark:bg-amber-950/50 text-amber-600 dark:text-amber-400 border-amber-200 dark:border-amber-800/60'
            }`}
          >
            <ShieldCheck className="w-5 h-5 sm:w-6 sm:h-6" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] sm:text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">PROTECTION HEALTH</p>
            <h2 className="text-xl sm:text-2xl font-bold font-mono mt-0.5 text-slate-900 dark:text-white">
              {fullyProtectedCount} / {activeDevices.length}
            </h2>
            <span className="text-[11px] text-slate-500 block truncate">
              {activeDevices.length === 0
                ? 'No active endpoints'
                : fullyProtectedCount === activeDevices.length
                ? 'All devices fully protected'
                : 'Attention needed on some devices'}
            </span>
          </div>
        </div>

        {/* Active Security Alerts */}
        <div className="bg-white dark:bg-[#121927] p-4 sm:p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm flex items-center gap-3.5 sm:gap-4 min-w-0">
          <div
            className={`w-11 h-11 sm:w-12 sm:h-12 rounded-xl flex items-center justify-center border shrink-0 ${
              activeAlerts.length === 0
                ? 'bg-emerald-50 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800/60'
                : 'bg-rose-50 dark:bg-rose-950/50 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-800/60'
            }`}
          >
            <ShieldAlert className="w-5 h-5 sm:w-6 sm:h-6" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] sm:text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">ACTIVE ALERTS</p>
            <h2
              className={`text-xl sm:text-2xl font-bold font-mono mt-0.5 ${
                activeAlerts.length === 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'
              }`}
            >
              {activeAlerts.length}
            </h2>
            <span className="text-[11px] text-slate-500 block truncate">
              {activeAlerts.length === 0 ? 'Zero unaddressed threats' : `${criticalAlerts} critical / high priority`}
            </span>
          </div>
        </div>
      </div>

      {/* Quick Navigation Shortcuts */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-4">
        <Link
          to="/tracking"
          className="p-3.5 sm:p-4 bg-white dark:bg-[#121927] hover:bg-slate-50 dark:hover:bg-[#162032] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-xs flex items-center justify-between group transition-all min-w-0"
        >
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-cyan-50 dark:bg-cyan-950/50 text-cyan-600 dark:text-cyan-400 flex items-center justify-center shrink-0">
              <MapPin className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-xs font-bold text-slate-900 dark:text-white truncate">Live Endpoint Tracking</h3>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate">View real-time map &amp; GPS fixes</p>
            </div>
          </div>
          <ArrowRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform shrink-0 ml-2" />
        </Link>

        <Link
          to="/commands"
          className="p-3.5 sm:p-4 bg-white dark:bg-[#121927] hover:bg-slate-50 dark:hover:bg-[#162032] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-xs flex items-center justify-between group transition-all min-w-0"
        >
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-indigo-50 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 flex items-center justify-center shrink-0">
              <Terminal className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-xs font-bold text-slate-900 dark:text-white truncate">Remote Commands</h3>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate">Dispatch lock, alarm &amp; locate</p>
            </div>
          </div>
          <ArrowRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform shrink-0 ml-2" />
        </Link>

        <Link
          to="/security"
          className="p-3.5 sm:p-4 bg-white dark:bg-[#121927] hover:bg-slate-50 dark:hover:bg-[#162032] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-xs flex items-center justify-between group transition-all min-w-0"
        >
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-xl bg-rose-50 dark:bg-rose-950/50 text-rose-600 dark:text-rose-400 flex items-center justify-center shrink-0">
              <ShieldAlert className="w-4 h-4" />
            </div>
            <div className="min-w-0">
              <h3 className="text-xs font-bold text-slate-900 dark:text-white truncate">Security Center</h3>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate">Audit logs &amp; security alarms</p>
            </div>
          </div>
          <ArrowRight className="w-4 h-4 text-slate-400 group-hover:translate-x-1 transition-transform shrink-0 ml-2" />
        </Link>
      </div>

      {/* Main Charts & Feed Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Threat Trend Chart (Actual data or honest empty state) */}
        <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white text-sm">
                24-Hour Threat &amp; Telemetry Signal
              </h3>
              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                Verified event activity over the last 24 hours
              </p>
            </div>
            {chartData && (
              <span className="text-[10px] font-mono font-bold bg-cyan-50 dark:bg-cyan-950/40 text-cyan-700 dark:text-cyan-400 border border-cyan-200 dark:border-cyan-800 px-2 py-0.5 rounded-full">
                Active Telemetry
              </span>
            )}
          </div>

          <div className="h-[260px] flex items-center justify-center">
            {chartData ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="colorAlerts" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={isDark ? '#00D2FF' : '#0284C7'} stopOpacity={0.35} />
                      <stop offset="95%" stopColor={isDark ? '#00D2FF' : '#0284C7'} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={isDark ? '#1E293B' : '#E2E8F0'} />
                  <XAxis
                    dataKey="time"
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: isDark ? '#64748B' : '#94A3B8', fontSize: 11 }}
                    dy={8}
                  />
                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: isDark ? '#64748B' : '#94A3B8', fontSize: 11 }}
                    allowDecimals={false}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: isDark ? '#0F172A' : '#FFFFFF',
                      borderRadius: '12px',
                      border: isDark ? '1px solid #223049' : '1px solid #E2E8F0',
                      color: isDark ? '#F8FAFC' : '#0F172A',
                      boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
                      fontSize: '12px',
                    }}
                  />
                  <Area
                    type="monotone"
                    dataKey="alerts"
                    name="Security Events"
                    stroke={isDark ? '#00D2FF' : '#0284C7'}
                    strokeWidth={2}
                    fillOpacity={1}
                    fill="url(#colorAlerts)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="text-center p-6 space-y-2">
                <ShieldCheck className="w-10 h-10 text-emerald-500 mx-auto opacity-75" />
                <h4 className="text-xs font-bold text-slate-800 dark:text-slate-200">
                  No Security Events in the Last 24 Hours
                </h4>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 max-w-xs mx-auto">
                  Your devices have not triggered any perimeter or security alerts today. When an event occurs, it will be automatically graphed here.
                </p>
              </div>
            )}
          </div>
        </div>

        {/* Recent Telemetry & Alerts Feed */}
        <div className="bg-white dark:bg-[#121927] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm overflow-hidden flex flex-col">
          <div className="p-5 border-b border-slate-200 dark:border-[#223049] flex items-center justify-between">
            <div>
              <h3 className="font-bold text-slate-900 dark:text-white text-sm">
                Recent Security Telemetry
              </h3>
              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                Latest alarms, sim-state events and perimeter updates
              </p>
            </div>
            <Link
              to="/security"
              className="text-xs font-semibold text-indigo-600 dark:text-cyan-400 hover:underline flex items-center gap-1"
            >
              View All ({alerts.length})
            </Link>
          </div>

          <div className="flex-1 overflow-y-auto p-4 space-y-2.5 max-h-[300px]">
            {activeAlerts.length === 0 ? (
              <div className="py-12 text-center flex flex-col items-center justify-center gap-2">
                <Shield className="w-8 h-8 text-emerald-500 opacity-70" />
                <p className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                  All alerts clear
                </p>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 max-w-xs">
                  There are no unaddressed security alerts. Check Security Center to review past dismissed records.
                </p>
              </div>
            ) : (
              activeAlerts.slice(0, 5).map((alert) => (
                <div
                  key={alert.id}
                  className="p-3 bg-slate-50 dark:bg-[#162032] hover:bg-slate-100 dark:hover:bg-[#1a263c] border border-slate-200 dark:border-slate-800 rounded-xl transition-all flex items-start gap-3"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-slate-900 dark:text-white text-xs truncate">
                        {alert.eventType}
                      </span>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded border ${getSeverityBadge(
                          alert.severity
                        )}`}
                      >
                        {alert.severity}
                      </span>
                    </div>
                    <p className="text-slate-600 dark:text-slate-300 text-xs mt-1 line-clamp-1">
                      {alert.description}
                    </p>
                    <div className="flex items-center gap-2 mt-1.5 text-[10px] text-slate-400 dark:text-slate-500 font-mono">
                      <span>{new Date(alert.timestamp).toLocaleString()}</span>
                      {alert.deviceName && <span>• Device: {alert.deviceName}</span>}
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Release & App Info Modal */}
      {showReleaseModal && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-fade-in"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowReleaseModal(false);
          }}
        >
          <div className="bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] rounded-3xl p-5 sm:p-6 max-w-md w-full max-h-[90vh] overflow-y-auto shadow-2xl space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3 min-w-0">
                <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-cyan-400 flex items-center justify-center border border-indigo-200 dark:border-indigo-800 shrink-0">
                  <Smartphone className="w-5 h-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="text-base font-bold text-slate-900 dark:text-white font-mono truncate">
                    SecureTrack Android Client
                  </h3>
                  <p className="text-xs text-slate-500">Version 1.8.0 • Fleet Protection</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowReleaseModal(false)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 p-1 shrink-0"
                aria-label="Close modal"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
              Install SecureTrack on your Android handset to enable continuous location telemetry, anti-shutdown defense, and remote recovery.
            </p>

            <div className="bg-slate-50 dark:bg-[#162032] p-4 rounded-xl border border-slate-200 dark:border-slate-800 space-y-2.5 text-xs">
              <div className="flex flex-col xs:flex-row xs:justify-between xs:items-center gap-1">
                <span className="text-slate-500">Source Repository</span>
                <a
                  href="https://github.com/dhanushs602848/SecureTrack"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-indigo-600 dark:text-cyan-400 hover:underline font-semibold inline-flex items-center gap-1 font-mono break-all"
                >
                  GitHub Repository <ExternalLink className="w-3 h-3 shrink-0" />
                </a>
              </div>
              <div className="flex flex-col xs:flex-row xs:justify-between xs:items-center gap-1">
                <span className="text-slate-500">APK Releases</span>
                <a
                  href="https://github.com/dhanushs602848/SecureTrack/releases"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-indigo-600 dark:text-cyan-400 hover:underline font-semibold inline-flex items-center gap-1 font-mono break-all"
                >
                  Official Releases <ExternalLink className="w-3 h-3 shrink-0" />
                </a>
              </div>
              <div className="flex flex-col xs:flex-row xs:justify-between xs:items-center gap-1">
                <span className="text-slate-500">Build Command</span>
                <code className="font-mono text-[11px] text-slate-800 dark:text-slate-200 bg-slate-200 dark:bg-slate-800 px-1.5 py-0.5 rounded break-all">
                  ./gradlew assembleDebug
                </code>
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="button"
                onClick={() => setShowReleaseModal(false)}
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
