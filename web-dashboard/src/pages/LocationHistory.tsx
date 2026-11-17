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
    <div className="space-y-6 flex flex-col h-[calc(100vh-6rem)]">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Location History</h2>
          <p className="text-sm text-[#62666D]">Review endpoint movement timeline</p>
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

      <div className="flex flex-col lg:flex-row gap-6 flex-1 min-h-0">
        <div className="lg:w-1/3 bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-y-auto">
          <div className="p-4 border-b border-[#DFE2E7] bg-gray-50 sticky top-0 z-10 flex items-center gap-2">
            <Clock className="w-5 h-5 text-[#255DA8]" />
            <h3 className="font-bold text-[#152C4F]">Timeline</h3>
          </div>
          <div className="divide-y divide-[#DFE2E7]">
            {history.length === 0 ? (
              <div className="p-8 text-center text-[#62666D]">
                <MapPin className="w-8 h-8 mx-auto mb-2 opacity-50" />
                <p>No location history available</p>
              </div>
            ) : (
              history.map((loc, idx) => (
                <div key={idx} className="p-4 hover:bg-blue-50/50 transition-colors group cursor-pointer">
                  <div className="flex items-start gap-3">
                    <div className="mt-1 w-2 h-2 rounded-full bg-[#255DA8] group-hover:scale-150 transition-transform shadow-[0_0_0_4px_rgba(37,93,168,0.1)]"></div>
                    <div className="space-y-1 text-sm">
                      <p className="font-semibold text-[#152C4F]">{new Date(loc.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit', second:'2-digit'})}</p>
                      <p className="text-[#62666D] font-mono text-xs">{loc.latitude.toFixed(5)}, {loc.longitude.toFixed(5)}</p>
                      <div className="flex items-center gap-3 text-xs text-[#62666D]">
                        <span className="flex items-center gap-1"><Navigation className="w-3 h-3" /> {loc.speed.toFixed(1)} km/h</span>
                      </div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="lg:w-2/3 bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-hidden">
          <LocationMap locations={history} showPath={true} />
        </div>
      </div>
    </div>
  );
};
