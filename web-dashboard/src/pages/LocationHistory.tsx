import React, { useEffect, useState } from 'react';
import { LocationMap } from '../components/LocationMap';
import { deviceService, locationService } from '../services/api';
import { Device, LocationRecord } from '../types';

export const LocationHistory: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | null>(null);
  const [history, setHistory] = useState<LocationRecord[]>([]);

  useEffect(() => {
    deviceService.getDevices().then((data) => {
      setDevices(data);
      if (data.length > 0) setSelectedDeviceId(data[0].id);
    });
  }, []);

  useEffect(() => {
    if (selectedDeviceId) {
      locationService.getLocationHistory(selectedDeviceId)
        .then(setHistory)
        .catch(() => setHistory([]));
    }
  }, [selectedDeviceId]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Historical Location Log</h2>
          <p className="text-sm text-[#62666D]">Route playback and chronological position history</p>
        </div>

        <select
          value={selectedDeviceId || ''}
          onChange={(e) => setSelectedDeviceId(Number(e.target.value))}
          className="px-4 py-2 bg-white border border-[#DFE2E7] rounded-xl text-sm font-semibold text-[#152C4F]"
        >
          {devices.map((d) => (
            <option key={d.id} value={d.id}>
              {d.deviceName} ({d.model})
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm">
          <div className="h-[420px]">
            <LocationMap
              locations={history.map(h => ({
                latitude: h.latitude,
                longitude: h.longitude,
                label: `Point #${h.id}`,
                timestamp: h.timestamp
              }))}
            />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm space-y-4 max-h-[480px] overflow-y-auto">
          <h3 className="text-lg font-bold text-[#152C4F]">Positions Timeline ({history.length})</h3>
          {history.length === 0 ? (
            <p className="text-sm text-[#62666D] py-8 text-center">No location history recorded yet.</p>
          ) : (
            <div className="space-y-2">
              {history.map((item) => (
                <div key={item.id} className="p-3 bg-[#F8F9FB] rounded-xl border border-[#DFE2E7] text-xs space-y-1">
                  <p className="font-bold text-[#152C4F]">Lat: {item.latitude.toFixed(5)}, Lng: {item.longitude.toFixed(5)}</p>
                  <p className="text-[#62666D]">Accuracy: {item.accuracy}m | {item.timestamp}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
