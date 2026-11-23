import React, { useEffect, useState } from 'react';
import { LocationMap } from '../components/LocationMap';
import { locationService, deviceService } from '../services/api';
import { LocationRecord, Device } from '../types';
import { Clock, MapPin, Navigation } from 'lucide-react';

export const LocationHistory: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [history, setHistory] = useState<LocationRecord[]>([]);

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
      const unsubLoc = locationService.subscribeToLocationHistory(selectedDeviceId, (data) => {
        setHistory(data);
      });
      return () => unsubLoc();
    }
  }, [selectedDeviceId]);

  return (
    <div className="space-y-6 flex flex-col h-[calc(100vh-6rem)] max-w-7xl mx-auto transition-colors duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white font-mono">Location History</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Review endpoint movement timeline and trail</p>
        </div>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          className="w-full sm:w-64 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-cyan-500 focus:border-cyan-500 p-2.5 shadow-sm"
        >
          <option value="" disabled>Select a device</option>
          {devices.map(d => (
            <option key={d.id} value={d.id}>{d.deviceName} ({d.model})</option>
          ))}
        </select>
      </div>

      <div className="flex flex-col lg:flex-row gap-6 flex-1 min-h-0">
        <div className="lg:w-1/3 bg-white dark:bg-[#121927] rounded-2xl shadow-sm dark:shadow-xl border border-slate-200 dark:border-[#223049] overflow-y-auto">
          <div className="p-4 border-b border-slate-200 dark:border-[#223049] bg-slate-50 dark:bg-[#162032] sticky top-0 z-10 flex items-center gap-2">
            <Clock className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
            <h3 className="font-bold text-slate-900 dark:text-white text-xs font-mono">Timeline ({history.length})</h3>
          </div>
          <div className="divide-y divide-slate-200 dark:divide-[#223049]">
            {history.length === 0 ? (
              <div className="p-8 text-center text-slate-400">
                <MapPin className="w-8 h-8 mx-auto mb-2 opacity-50" />
                <p className="text-xs font-mono">No location history available</p>
              </div>
            ) : (
              history.map((loc, idx) => (
                <div key={idx} className="p-4 hover:bg-slate-50 dark:hover:bg-[#162032] transition-colors group cursor-pointer">
                  <div className="flex items-start gap-3">
                    <div className="mt-1 w-2 h-2 rounded-full bg-cyan-500 group-hover:scale-150 transition-transform shadow-[0_0_0_4px_rgba(6,182,212,0.15)]"></div>
                    <div className="space-y-1 text-xs">
                      <p className="font-semibold text-slate-900 dark:text-white font-mono">
                        {new Date(loc.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit', second:'2-digit'})}
                      </p>
                      <p className="text-slate-500 dark:text-slate-400 font-mono text-[11px]">{loc.latitude.toFixed(5)}, {loc.longitude.toFixed(5)}</p>
                      <div className="flex items-center gap-3 text-[11px] text-slate-400 font-mono">
                        <span className="flex items-center gap-1"><Navigation className="w-3 h-3" /> {(loc.speed ?? 0).toFixed(1)} km/h</span>
                        <span>±{loc.accuracy?.toFixed(0) || '0'}m</span>
                      </div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="lg:w-2/3 bg-white dark:bg-[#121927] rounded-2xl shadow-sm dark:shadow-xl border border-slate-200 dark:border-[#223049] overflow-hidden">
          <LocationMap locations={history} showPath={true} />
        </div>
      </div>
    </div>
  );
};
