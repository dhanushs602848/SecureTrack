import React, { useEffect, useState } from 'react';
import { Smartphone, ShieldAlert, Activity, Wifi, Battery, MapPin } from 'lucide-react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { deviceService, securityService, locationService } from '../services/api';
import { Device, SecurityEvent, LocationRecord } from '../types';

export const Overview: React.FC = () => {
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

  // Fetch locations for devices whenever devices change (simplified for overview)
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

  const onlineDevices = devices.filter(d => d.status === 'ONLINE').length;
  const criticalAlerts = alerts.filter(a => a.severity === 'CRITICAL' || a.severity === 'HIGH').length;

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return 'bg-red-100 text-red-800 border-red-200';
      case 'HIGH': return 'bg-orange-100 text-orange-800 border-orange-200';
      case 'WARNING': return 'bg-yellow-100 text-yellow-800 border-yellow-200';
      default: return 'bg-blue-100 text-blue-800 border-blue-200';
    }
  };

  const chartData = [
    { time: '00:00', alerts: 2 }, { time: '04:00', alerts: 1 },
    { time: '08:00', alerts: 4 }, { time: '12:00', alerts: 3 },
    { time: '16:00', alerts: 7 }, { time: '20:00', alerts: 2 },
    { time: '24:00', alerts: 5 },
  ];

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">System Overview</h2>
          <p className="text-sm text-[#62666D]">Welcome to SecureTrack Command Center</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4 hover:border-[#255DA8] transition-colors group cursor-default">
          <div className="w-12 h-12 bg-blue-50 text-[#255DA8] rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Smartphone className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm font-semibold text-[#62666D]">Total Devices</p>
            <h3 className="text-2xl font-bold text-[#152C4F]">{devices.length}</h3>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4 hover:border-emerald-500 transition-colors group cursor-default">
          <div className="w-12 h-12 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Wifi className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm font-semibold text-[#62666D]">Online</p>
            <h3 className="text-2xl font-bold text-[#152C4F]">{onlineDevices}</h3>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4 hover:border-red-500 transition-colors group cursor-default">
          <div className="w-12 h-12 bg-red-50 text-red-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm font-semibold text-[#62666D]">Critical Alerts</p>
            <h3 className="text-2xl font-bold text-[#152C4F]">{criticalAlerts}</h3>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4 hover:border-purple-500 transition-colors group cursor-default">
          <div className="w-12 h-12 bg-purple-50 text-purple-600 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Activity className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm font-semibold text-[#62666D]">System Status</p>
            <h3 className="text-lg font-bold text-emerald-600">Healthy</h3>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm">
          <h3 className="font-bold text-[#152C4F] mb-6">Security Events Trend (24h mockup)</h3>
          <div className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorAlerts" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#255DA8" stopOpacity={0.3}/>
                    <stop offset="95%" stopColor="#255DA8" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#DFE2E7" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: '#62666D', fontSize: 12}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#62666D', fontSize: 12}} />
                <Tooltip
                  contentStyle={{ borderRadius: '12px', border: '1px solid #DFE2E7', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                />
                <Area type="monotone" dataKey="alerts" stroke="#255DA8" strokeWidth={3} fillOpacity={1} fill="url(#colorAlerts)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-[#DFE2E7] shadow-sm overflow-hidden flex flex-col">
          <div className="p-6 border-b border-[#DFE2E7]">
            <h3 className="font-bold text-[#152C4F]">Recent Activity</h3>
          </div>
          <div className="flex-1 overflow-y-auto p-2">
            {alerts.length === 0 ? (
              <div className="p-8 text-center text-[#62666D]">No recent activity</div>
            ) : (
              alerts.slice(0, 5).map((alert) => (
                <div key={alert.id} className="p-4 hover:bg-gray-50 rounded-xl transition-colors flex items-start gap-4">
                  <div className={`w-2 h-2 mt-2 rounded-full ${getSeverityColor(alert.severity).split(' ')[0]}`}></div>
                  <div className="flex-1">
                    <p className="font-semibold text-[#152C4F] text-sm">{alert.eventType}</p>
                    <p className="text-[#62666D] text-xs mt-1">{alert.description}</p>
                    <p className="text-gray-400 text-xs mt-2">{new Date(alert.timestamp).toLocaleString()}</p>
                  </div>
                  <span className={`text-[10px] font-bold px-2 py-1 rounded border ${getSeverityColor(alert.severity)}`}>
                    {alert.severity}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
