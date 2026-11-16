import React, { useEffect, useState } from 'react';
import { ShieldAlert, RefreshCw } from 'lucide-react';
import { securityService } from '../services/api';
import { SecurityEvent } from '../types';

export const SecurityEvents: React.FC = () => {
  const [events, setEvents] = useState<SecurityEvent[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchEvents = async () => {
    setLoading(true);
    try {
      const data = await securityService.getSecurityEvents();
      setEvents(data);
    } catch (e) {
      console.error('Failed to load security events:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Security Activity Log</h2>
          <p className="text-sm text-[#62666D]">Anti-theft events, SIM state logs and device admin changes</p>
        </div>
        <button
          onClick={fetchEvents}
          className="flex items-center gap-2 px-4 py-2 bg-white border border-[#DFE2E7] text-[#152C4F] rounded-xl text-sm font-semibold hover:bg-gray-50"
        >
          <RefreshCw className="w-4 h-4" />
          Refresh Log
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-[#DFE2E7] shadow-sm overflow-hidden">
        {loading ? (
          <p className="text-center text-sm py-12 text-[#62666D]">Loading events...</p>
        ) : events.length === 0 ? (
          <div className="p-12 text-center space-y-2">
            <ShieldAlert className="w-10 h-10 text-emerald-600 mx-auto" />
            <p className="font-bold text-[#152C4F]">All Clear — No Security Alerts</p>
            <p className="text-xs text-[#62666D]">Your endpoints report normal security status.</p>
          </div>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="bg-[#F8F9FB] border-b border-[#DFE2E7] text-xs font-bold text-[#62666D]">
              <tr>
                <th className="px-6 py-4">Event Type</th>
                <th className="px-6 py-4">Severity</th>
                <th className="px-6 py-4">Device ID</th>
                <th className="px-6 py-4">Description</th>
                <th className="px-6 py-4">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#DFE2E7]">
              {events.map((e) => (
                <tr key={e.id} className="hover:bg-[#F8F9FB]/50">
                  <td className="px-6 py-4 font-bold text-[#152C4F]">{e.eventType}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-bold ${
                      e.severity === 'CRITICAL' ? 'bg-red-100 text-red-800' :
                      e.severity === 'HIGH' ? 'bg-orange-100 text-orange-800' :
                      e.severity === 'WARNING' ? 'bg-amber-100 text-amber-800' :
                      'bg-blue-100 text-blue-800'
                    }`}>
                      {e.severity}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-[#62666D]">#{e.deviceId}</td>
                  <td className="px-6 py-4 text-[#1A1C1E]">{e.description}</td>
                  <td className="px-6 py-4 text-[#62666D] text-xs">{e.timestamp}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};
