import React, { useEffect, useState } from 'react';
import { ShieldAlert, AlertTriangle, Info, Shield, Search } from 'lucide-react';
import { securityService, deviceService } from '../services/api';
import { SecurityEvent, Device } from '../types';

export const SecurityEvents: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [events, setEvents] = useState<SecurityEvent[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubDevices = deviceService.subscribeToDevices((data) => {
      setDevices(data);
      if (data.length > 0 && !selectedDeviceId) {
        setSelectedDeviceId(data[0].id);
      }
    });
    return () => unsubDevices();
  }, []);

  useEffect(() => {
    if (selectedDeviceId) {
      setLoading(true);
      const unsubAlerts = securityService.subscribeToSecurityEvents((data) => {
        setEvents(data);
        setLoading(false);
      }, selectedDeviceId);
      return () => unsubAlerts();
    }
  }, [selectedDeviceId]);

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return <ShieldAlert className="w-5 h-5 text-red-600" />;
      case 'HIGH': return <AlertTriangle className="w-5 h-5 text-orange-600" />;
      case 'WARNING': return <AlertTriangle className="w-5 h-5 text-yellow-600" />;
      default: return <Info className="w-5 h-5 text-blue-600" />;
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return 'bg-red-100 text-red-800 border-red-200';
      case 'HIGH': return 'bg-orange-100 text-orange-800 border-orange-200';
      case 'WARNING': return 'bg-yellow-100 text-yellow-800 border-yellow-200';
      default: return 'bg-blue-100 text-blue-800 border-blue-200';
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Security Events</h2>
          <p className="text-sm text-[#62666D]">Audit log of all security and administrative alerts</p>
        </div>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          className="w-full sm:w-64 bg-white border border-[#DFE2E7] text-[#152C4F] text-sm rounded-xl focus:ring-[#255DA8] focus:border-[#255DA8] p-2.5 shadow-sm"
        >
          <option value="" disabled>Select a device</option>
          {devices.map(d => (
            <option key={d.id} value={d.id}>{d.deviceName} ({d.model})</option>
          ))}
        </select>
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-hidden">
        <div className="p-4 border-b border-[#DFE2E7] bg-gray-50 flex items-center gap-3">
          <Shield className="w-5 h-5 text-[#255DA8]" />
          <h3 className="font-bold text-[#152C4F]">Event Log</h3>
        </div>

        <div className="divide-y divide-[#DFE2E7]">
          {loading ? (
            <div className="p-12 text-center text-[#62666D]">Loading security events...</div>
          ) : events.length === 0 ? (
            <div className="p-16 text-center text-[#62666D] space-y-3">
              <Shield className="w-12 h-12 mx-auto opacity-20" />
              <p className="font-semibold text-[#152C4F]">No Security Events</p>
              <p className="text-sm">Your endpoint has not generated any alerts.</p>
            </div>
          ) : (
            events.map((event) => (
              <div key={event.id} className="p-5 hover:bg-gray-50 transition-colors flex flex-col sm:flex-row sm:items-center gap-4">
                <div className="flex-shrink-0">
                  <div className="w-10 h-10 rounded-full bg-white border border-gray-100 shadow-sm flex items-center justify-center">
                    {getSeverityIcon(event.severity)}
                  </div>
                </div>
                <div className="flex-1 space-y-1">
                  <div className="flex items-center gap-3">
                    <h4 className="font-bold text-[#152C4F]">{event.eventType}</h4>
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider ${getSeverityColor(event.severity)}`}>
                      {event.severity}
                    </span>
                  </div>
                  <p className="text-sm text-[#62666D]">{event.description}</p>
                </div>
                <div className="flex-shrink-0 text-xs font-mono text-gray-500 sm:text-right">
                  {new Date(event.timestamp).toLocaleString()}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
