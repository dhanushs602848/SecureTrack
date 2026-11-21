import React, { useEffect, useState } from 'react';
import { Smartphone, ShieldAlert, Activity, Wifi, Battery, MapPin } from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { deviceService, securityService, locationService } from '../services/api';
import { Device, SecurityEvent, LocationRecord } from '../types';
import { useTheme } from '../context/ThemeContext';

export const Overview: React.FC = () => {
  const { resolvedTheme } = useTheme();
  const [devices, setDevices] = useState<Device[]>([]);
  const [alerts, setAlerts] = useState<SecurityEvent[]>([]);
  const [latestLocations, setLatestLocations] = useState<LocationRecord[]>([]);

  useEffect(() => {
    const unsubDevices = deviceService.subscribeToDevices((data) => {
      setDevices(data);
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
    if (devices.length === 0) return;
    const unsubscribes = devices.map(d =>
      locationService.subscribeToLocationHistory(d.id, (history) => {
        if (history.length > 0) {
          setLatestLocations(prev => {
            const filtered = prev.filter(l => l.deviceId !== d.id);
            return [...filtered, history[0]];
          });
        }
      })
    );
    return () => unsubscribes.forEach(unsub => unsub());
  }, [devices.length]);

  const activeAlerts = alerts.filter(a => (a.status || 'ACTIVE') === 'ACTIVE');
  const criticalAlerts = activeAlerts.filter(a => a.severity === 'CRITICAL' || a.severity === 'HIGH').length;
  const onlineDevices = devices.filter(d => d.status === 'ONLINE').length;

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800';
      case 'HIGH':
        return 'bg-orange-100 dark:bg-orange-950/40 text-orange-700 dark:text-orange-400 border-orange-300 dark:border-orange-800';
      case 'WARNING':
        return 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800';
      default:
        return 'bg-cyan-100 dark:bg-cyan-950/40 text-cyan-700 dark:text-cyan-400 border-cyan-300 dark:border-cyan-800';
    }
  };

  const chartData = [
    { time: '00:00', alerts: 2 }, { time: '04:00', alerts: 1 },
    { time: '08:00', alerts: 4 }, { time: '12:00', alerts: 3 },
    { time: '16:00', alerts: 7 }, { time: '20:00', alerts: 2 },
    { time: '24:00', alerts: activeAlerts.length },
  ];

  const isDark = resolvedTheme === 'dark';

  return (
    <div className="space-y-6 max-w-7xl mx-auto transition-colors duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight font-mono">Telemetry Overview</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Real-time status of protected Android fleet and security barriers</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-100 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-400 border border-emerald-300 dark:border-emerald-800/60 shadow-[0_0_10px_rgba(16,185,129,0.15)]">
            <span className="w-2 h-2 rounded-full bg-emerald-500 dark:bg-emerald-400 animate-pulse" />
            Zero-Trust Fleet Active
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white dark:bg-[#121927] p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg flex items-center gap-4 hover:border-cyan-500/50 transition-all group">
          <div className="w-12 h-12 bg-cyan-100 dark:bg-cyan-950/50 text-cyan-600 dark:text-cyan-400 border border-cyan-200 dark:border-cyan-800/50 rounded-xl flex items-center justify-center group-hover:scale-105 transition-transform shadow-[0_0_12px_rgba(0,210,255,0.15)]">
            <Smartphone className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 tracking-wider">TOTAL FLEET</p>
            <h3 className="text-2xl font-bold text-slate-900 dark:text-white font-mono mt-0.5">{devices.length}</h3>
          </div>
        </div>

        <div className="bg-white dark:bg-[#121927] p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg flex items-center gap-4 hover:border-emerald-500/50 transition-all group">
          <div className="w-12 h-12 bg-emerald-100 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800/50 rounded-xl flex items-center justify-center group-hover:scale-105 transition-transform shadow-[0_0_12px_rgba(16,185,129,0.15)]">
            <Wifi className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 tracking-wider">ONLINE PEERS</p>
            <h3 className="text-2xl font-bold text-emerald-600 dark:text-emerald-400 font-mono mt-0.5">{onlineDevices}</h3>
          </div>
        </div>

        <div className="bg-white dark:bg-[#121927] p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg flex items-center gap-4 hover:border-rose-500/50 transition-all group">
          <div className={`w-12 h-12 rounded-xl flex items-center justify-center group-hover:scale-105 transition-transform border ${
            activeAlerts.length === 0
              ? 'bg-emerald-100 dark:bg-emerald-950/50 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800/50 shadow-[0_0_12px_rgba(16,185,129,0.15)]'
              : 'bg-rose-100 dark:bg-rose-950/50 text-rose-600 dark:text-rose-400 border-rose-200 dark:border-rose-800/50 shadow-[0_0_12px_rgba(239,68,68,0.15)]'
          }`}>
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 tracking-wider">ACTIVE ALERTS</p>
            <h3 className={`text-2xl font-bold font-mono mt-0.5 ${
              activeAlerts.length === 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'
            }`}>
              {activeAlerts.length}
            </h3>
            <span className="text-[10px] text-slate-500 font-medium">
              {activeAlerts.length === 0 ? 'All dismissed • Protected' : `${criticalAlerts} critical`}
            </span>
          </div>
        </div>

        <div className="bg-white dark:bg-[#121927] p-5 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg flex items-center gap-4 hover:border-indigo-500/50 transition-all group">
          <div className="w-12 h-12 bg-indigo-100 dark:bg-indigo-950/50 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800/50 rounded-xl flex items-center justify-center group-hover:scale-105 transition-transform shadow-[0_0_12px_rgba(99,102,241,0.15)]">
            <Activity className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 tracking-wider">SYSTEM STATUS</p>
            <h3 className="text-sm font-bold text-emerald-600 dark:text-emerald-400 mt-1 font-mono">● OPERATIONAL</h3>
            <span className="text-[10px] text-slate-500">Cloud Sync &amp; BLE Active</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg">
          <h3 className="font-bold text-slate-900 dark:text-white text-sm tracking-wide mb-6 font-mono flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-cyan-500 dark:bg-cyan-400" />
            24h Threat &amp; Telemetry Signal
          </h3>
          <div className="h-[280px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorAlerts" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={isDark ? "#00D2FF" : "#0284C7"} stopOpacity={0.4}/>
                    <stop offset="95%" stopColor={isDark ? "#00D2FF" : "#0284C7"} stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={isDark ? "#1E293B" : "#E2E8F0"} />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: isDark ? '#64748B' : '#94A3B8', fontSize: 11}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: isDark ? '#64748B' : '#94A3B8', fontSize: 11}} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: isDark ? '#0F172A' : '#FFFFFF',
                    borderRadius: '12px',
                    border: isDark ? '1px solid #223049' : '1px solid #E2E8F0',
                    color: isDark ? '#F8FAFC' : '#0F172A',
                    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
                  }}
                />
                <Area type="monotone" dataKey="alerts" stroke={isDark ? "#00D2FF" : "#0284C7"} strokeWidth={2.5} fillOpacity={1} fill="url(#colorAlerts)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white dark:bg-[#121927] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm dark:shadow-lg overflow-hidden flex flex-col">
          <div className="p-5 border-b border-slate-200 dark:border-[#223049] flex items-center justify-between">
            <h3 className="font-bold text-slate-900 dark:text-white text-sm tracking-wide font-mono flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-indigo-500 dark:bg-indigo-400" />
              Active Telemetry Feed
            </h3>
            <span className="text-xs text-slate-500 dark:text-slate-400 font-mono">
              {activeAlerts.length} active
            </span>
          </div>
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {activeAlerts.length === 0 ? (
              <div className="p-10 text-center flex flex-col items-center justify-center gap-2">
                <ShieldAlert className="w-8 h-8 text-emerald-500 dark:text-emerald-400 opacity-60" />
                <p className="text-sm font-semibold text-slate-800 dark:text-slate-200">No active alerts</p>
                <p className="text-xs text-slate-500">You're all clear. Your dismissed alerts remain available in history.</p>
              </div>
            ) : (
              activeAlerts.slice(0, 5).map((alert) => (
                <div key={alert.id} className="p-3.5 bg-slate-50 dark:bg-[#162032] hover:bg-slate-100 dark:hover:bg-[#1A263D] border border-slate-200 dark:border-slate-800/80 rounded-xl transition-all flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold text-slate-900 dark:text-white text-xs truncate">{alert.eventType}</p>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${getSeverityBadge(alert.severity)}`}>
                        {alert.severity}
                      </span>
                    </div>
                    <p className="text-slate-600 dark:text-slate-400 text-xs mt-1 line-clamp-1">{alert.description}</p>
                    <p className="text-slate-400 dark:text-slate-500 text-[10px] mt-1.5 font-mono">{new Date(alert.timestamp).toLocaleString()}</p>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
