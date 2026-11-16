import React, { useEffect, useState } from 'react';
import { LocationMap } from '../components/LocationMap';
import { deviceService, locationService } from '../services/api';
import { Device, LocationRecord } from '../types';

export const LiveTracking: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<number | null>(null);
  const [location, setLocation] = useState<LocationRecord | null>(null);

  useEffect(() => {
    deviceService.getDevices().then((data) => {
      setDevices(data);
      if (data.length > 0) setSelectedDeviceId(data[0].id);
    });
  }, []);

  useEffect(() => {
    if (selectedDeviceId) {
      locationService.getLatestLocation(selectedDeviceId)
        .then(setLocation)
        .catch(() => setLocation(null));
    }
  }, [selectedDeviceId]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Live Tracking Center</h2>
          <p className="text-sm text-[#62666D]">Interactive OpenStreetMap real-time position monitor</p>
        </div>

        <select
          value={selectedDeviceId || ''}
          onChange={(e) => setSelectedDeviceId(Number(e.target.value))}
          className="px-4 py-2 bg-white border border-[#DFE2E7] rounded-xl text-sm font-semibold text-[#152C4F] focus:outline-none"
        >
          {devices.map((d) => (
            <option key={d.id} value={d.id}>
              {d.deviceName} ({d.model})
            </option>
          ))}
        </select>
      </div>

      <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm space-y-4">
        <div className="h-[450px]">
          <LocationMap
            locations={location ? [{
              latitude: location.latitude,
              longitude: location.longitude,
              label: `Device #${location.deviceId}`,
              timestamp: location.timestamp
            }] : []}
          />
        </div>

        {location ? (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 bg-[#F8F9FB] rounded-xl text-xs font-medium border border-[#DFE2E7]">
            <div><span className="text-[#62666D]">Latitude:</span> <p className="text-sm font-bold text-[#152C4F]">{location.latitude}</p></div>
            <div><span className="text-[#62666D]">Longitude:</span> <p className="text-sm font-bold text-[#152C4F]">{location.longitude}</p></div>
            <div><span className="text-[#62666D]">Accuracy:</span> <p className="text-sm font-bold text-[#152C4F]">{location.accuracy}m</p></div>
            <div><span className="text-[#62666D]">Timestamp:</span> <p className="text-sm font-bold text-[#152C4F]">{location.timestamp}</p></div>
          </div>
        ) : (
          <p className="text-center text-sm py-4 text-[#62666D]">No location report available for selected device.</p>
        )}
      </div>
    </div>
  );
};
