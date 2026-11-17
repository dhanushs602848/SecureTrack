import React, { useEffect, useState } from 'react';
import { LocationMap } from '../components/LocationMap';
import { locationService, deviceService } from '../services/api';
import { LocationRecord, Device } from '../types';
import { Activity, Clock } from 'lucide-react';

export const LiveTracking: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [latestLocation, setLatestLocation] = useState<LocationRecord | null>(null);

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
      const unsubLoc = locationService.subscribeToLocationHistory(selectedDeviceId, (history) => {
        if (history.length > 0) {
          setLatestLocation(history[0]);
        } else {
          setLatestLocation(null);
        }
      });
      return () => unsubLoc();
    }
  }, [selectedDeviceId]);

  return (
    <div className="space-y-6 flex flex-col h-[calc(100vh-6rem)]">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Live Tracking</h2>
          <p className="text-sm text-[#62666D]">Monitor real-time endpoint location</p>
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

      <div className="flex-1 bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-hidden relative">
        <LocationMap locations={latestLocation ? [latestLocation] : []} />

        {latestLocation && (
          <div className="absolute top-4 right-4 bg-white/95 backdrop-blur p-4 rounded-xl shadow-lg border border-[#DFE2E7] max-w-xs z-[400]">
            <h3 className="font-bold text-[#152C4F] mb-3 flex items-center gap-2">
              <Activity className="w-4 h-4 text-emerald-600" />
              Telemetry Data
            </h3>
            <div className="space-y-2 text-sm text-[#1A1C1E]">
              <div className="flex justify-between">
                <span className="text-[#62666D]">Speed</span>
                <span className="font-mono">{latestLocation.speed.toFixed(1)} km/h</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#62666D]">Accuracy</span>
                <span className="font-mono">±{latestLocation.accuracy.toFixed(0)}m</span>
              </div>
              <div className="pt-2 border-t border-[#DFE2E7] flex items-center gap-1.5 text-xs text-[#62666D]">
                <Clock className="w-3.5 h-3.5" />
                {new Date(latestLocation.timestamp).toLocaleTimeString()}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
