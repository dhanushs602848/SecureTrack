import React, { useEffect, useState } from 'react';
import { LocationMap } from '../components/LocationMap';
import { locationService, deviceService } from '../services/api';
import { LocationRecord, Device } from '../types';
import { Activity, Clock, AlertTriangle, MapPin } from 'lucide-react';

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
          // If no location history collection records, check device document last coordinates
          const dev = devices.find(d => d.id === selectedDeviceId);
          if (dev && dev.lastLatitude !== undefined && dev.lastLongitude !== undefined) {
            setLatestLocation({
              id: 'last_known',
              deviceId: selectedDeviceId,
              latitude: dev.lastLatitude,
              longitude: dev.lastLongitude,
              accuracy: dev.locationAccuracy || 0,
              altitude: 0,
              speed: 0,
              timestamp: dev.locationTimestamp || dev.lastSeen || new Date().toISOString(),
              source: (dev.locationSource as any) || 'LAST_KNOWN'
            });
          } else {
            setLatestLocation(null);
          }
        }
      });
      return () => unsubLoc();
    }
  }, [selectedDeviceId, devices]);

  const selectedDevice = devices.find(d => d.id === selectedDeviceId);
  const isLocationDisabled = selectedDevice?.locationStatus === 'DISABLED';

  return (
    <div className="space-y-6 flex flex-col h-[calc(100vh-6rem)]">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Live Tracking</h2>
          <p className="text-sm text-[#64748B]">Real-time GPS endpoint positioning & status</p>
        </div>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          className="w-full sm:w-72 bg-white border border-[#CBD5E1] text-[#152C4F] text-sm rounded-xl focus:ring-[#315C97] focus:border-[#315C97] p-2.5 shadow-sm"
        >
          <option value="" disabled>Select a device</option>
          {devices.map(d => (
            <option key={d.id} value={d.id}>
              {d.deviceName} ({d.model}) - {d.status}
            </option>
          ))}
        </select>
      </div>

      {isLocationDisabled && (
        <div className="p-3.5 bg-amber-50 border border-amber-300 rounded-xl flex items-center gap-3 text-amber-900 text-sm shadow-sm shrink-0">
          <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0" />
          <div>
            <span className="font-bold">Location Disabled on Device:</span> Current GPS coordinates are unavailable because the user or system turned off Location. Displaying the last known verified coordinates recorded before it was disabled.
          </div>
        </div>
      )}

      <div className="flex-1 bg-white rounded-2xl shadow-sm border border-[#CBD5E1] overflow-hidden relative">
        <LocationMap locations={latestLocation ? [latestLocation] : []} />

        {latestLocation ? (
          <div className="absolute top-4 right-4 bg-white/95 backdrop-blur p-4 rounded-xl shadow-lg border border-[#CBD5E1] max-w-xs z-[400]">
            <h3 className="font-bold text-[#152C4F] mb-3 flex items-center gap-2">
              <Activity className="w-4 h-4 text-emerald-600" />
              Telemetry Data
            </h3>
            <div className="space-y-2 text-sm text-[#1A1C1E]">
              <div className="flex justify-between">
                <span className="text-[#64748B]">Coordinates</span>
                <span className="font-mono text-xs font-semibold text-[#152C4F]">
                  {latestLocation.latitude.toFixed(5)}, {latestLocation.longitude.toFixed(5)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#64748B]">Source</span>
                <span className={`text-xs font-bold px-2 py-0.5 rounded ${
                  isLocationDisabled || latestLocation.source === 'LAST_KNOWN'
                    ? 'bg-amber-100 text-amber-800'
                    : 'bg-emerald-100 text-emerald-800'
                }`}>
                  {isLocationDisabled ? 'LAST_KNOWN' : (latestLocation.source || 'GPS')}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#64748B]">Speed</span>
                <span className="font-mono">{latestLocation.speed.toFixed(1)} km/h</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#64748B]">Accuracy</span>
                <span className="font-mono">±{latestLocation.accuracy.toFixed(0)}m</span>
              </div>
              <div className="pt-2 border-t border-[#CBD5E1] flex items-center gap-1.5 text-xs text-[#64748B]">
                <Clock className="w-3.5 h-3.5" />
                {latestLocation.timestamp ? new Date(latestLocation.timestamp).toLocaleString() : 'N/A'}
              </div>
            </div>
          </div>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-50/80 z-[300]">
            <div className="text-center p-6 bg-white rounded-2xl border border-[#CBD5E1] shadow-sm max-w-sm">
              <MapPin className="w-10 h-10 text-[#64748B] mx-auto mb-2 opacity-50" />
              <p className="font-bold text-[#152C4F]">No Location Coordinates</p>
              <p className="text-xs text-[#64748B] mt-1">
                This device has not yet transmitted GPS location records. Ensure SecureTrack background tracking is active on the phone.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
