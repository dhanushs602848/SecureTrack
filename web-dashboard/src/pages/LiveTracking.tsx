import React, { useEffect, useState } from 'react';
import { LocationMap } from '../components/LocationMap';
import { locationService, deviceService, commandService } from '../services/api';
import { LocationRecord, Device } from '../types';
import { Activity, Clock, AlertTriangle, MapPin, RefreshCw } from 'lucide-react';

export const LiveTracking: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [latestLocation, setLatestLocation] = useState<LocationRecord | null>(null);
  const [isLocating, setIsLocating] = useState<boolean>(false);
  const [locateFeedback, setLocateFeedback] = useState<string | null>(null);

  useEffect(() => {
    const unsubDevices = deviceService.subscribeToDevices((data) => {
      setDevices(data);

      setSelectedDeviceId((currentId) => {
        if (currentId && data.some((device) => device.id === currentId)) {
          return currentId;
        }

        return data[0]?.id ?? '';
      });
    });

    return () => unsubDevices();
  }, []);

  useEffect(() => {
    if (selectedDeviceId) {
      const dev = devices.find((d) => d.id === selectedDeviceId);
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
          source: (dev.locationSource as any) || 'LAST_KNOWN',
        });
      } else {
        setLatestLocation(null);
      }

      const unsubLoc = locationService.subscribeToLocationHistory(selectedDeviceId, (history) => {
        if (history.length > 0) {
          setLatestLocation(history[0]);
        }
      });
      return () => unsubLoc();
    }
  }, [selectedDeviceId, devices]);

  const selectedDevice = devices.find((d) => d.id === selectedDeviceId);
  const isLocationDisabled = selectedDevice?.locationStatus === 'DISABLED';
  const isStale = latestLocation?.timestamp
    ? Date.now() - new Date(latestLocation.timestamp).getTime() > 15 * 60 * 1000
    : false;

  const handleLocateNow = async () => {
    if (!selectedDeviceId) return;
    setIsLocating(true);
    setLocateFeedback(null);
    try {
      await commandService.sendCommand(selectedDeviceId, 'REFRESH_LOCATION');
      setLocateFeedback('Location refresh command dispatched via Cloud & FCM.');
      setTimeout(() => setLocateFeedback(null), 5000);
    } catch (e: any) {
      setLocateFeedback(`Failed to dispatch command: ${e.message || 'Unknown error'}`);
      setTimeout(() => setLocateFeedback(null), 5000);
    } finally {
      setIsLocating(false);
    }
  };

  return (
    <div className="space-y-6 flex flex-col h-[calc(100vh-6rem)] max-w-7xl mx-auto transition-colors duration-200">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 dark:text-white font-mono">Live Tracking</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Real-time GPS endpoint positioning &amp; status</p>
        </div>
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <select
            value={selectedDeviceId}
            onChange={(e) => setSelectedDeviceId(e.target.value)}
            className="w-full sm:w-72 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-cyan-500 focus:border-cyan-500 p-2.5 shadow-sm"
          >
            <option value="" disabled>Select a device</option>
            {devices.map(d => (
              <option key={d.id} value={d.id}>
                {d.deviceName} ({d.model}) - {d.status}
              </option>
            ))}
          </select>
          <button
            onClick={handleLocateNow}
            disabled={!selectedDeviceId || isLocating}
            className="flex items-center gap-1.5 px-3.5 py-2.5 bg-cyan-600 hover:bg-cyan-500 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-sm transition shrink-0"
            title="Dispatch immediate real-time GPS location refresh command"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLocating ? 'animate-spin' : ''}`} />
            <span>Locate Now</span>
          </button>
        </div>
      </div>

      {locateFeedback && (
        <div className={`p-3 rounded-xl text-xs flex items-center gap-2 shrink-0 ${
          locateFeedback.startsWith('Failed')
            ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-800 dark:text-rose-300 border border-rose-300 dark:border-rose-800'
            : 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-800 dark:text-emerald-300 border border-emerald-300 dark:border-emerald-800'
        }`}>
          <Activity className="w-4 h-4 shrink-0" />
          <span>{locateFeedback}</span>
        </div>
      )}

      {isLocationDisabled && (
        <div className="p-3.5 bg-amber-50 dark:bg-amber-950/40 border border-amber-300 dark:border-amber-800 rounded-xl flex items-center gap-3 text-amber-900 dark:text-amber-300 text-xs shadow-sm shrink-0">
          <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0" />
          <div>
            <span className="font-bold">Location Disabled on Device:</span> Current GPS coordinates are unavailable because the user or system turned off Location. Displaying the last known verified coordinates recorded before it was disabled.
          </div>
        </div>
      )}

      <div className="flex-1 bg-white dark:bg-[#121927] rounded-2xl shadow-sm dark:shadow-xl border border-slate-200 dark:border-[#223049] overflow-hidden relative">
        <LocationMap locations={latestLocation ? [latestLocation] : []} />

        {latestLocation ? (
          <div className="absolute top-4 right-4 bg-white/95 dark:bg-[#121927]/95 backdrop-blur p-4 rounded-xl shadow-lg border border-slate-200 dark:border-[#223049] max-w-xs z-[400]">
            <h3 className="font-bold text-slate-900 dark:text-white mb-3 flex items-center gap-2 text-xs font-mono">
              <Activity className="w-4 h-4 text-emerald-500" />
              Telemetry Data
            </h3>
            <div className="space-y-2 text-xs text-slate-700 dark:text-slate-300">
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">Coordinates</span>
                <span className="font-mono text-[11px] font-semibold text-slate-900 dark:text-cyan-400">
                  {latestLocation.latitude.toFixed(5)}, {latestLocation.longitude.toFixed(5)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">Source</span>
                <span className={`text-[10px] font-bold px-2 py-0.5 rounded border ${
                  isLocationDisabled || latestLocation.source === 'LAST_KNOWN'
                    ? 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800'
                    : 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
                }`}>
                  {isLocationDisabled ? 'LAST_KNOWN' : (latestLocation.source || 'GPS')}
                </span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-500 dark:text-slate-400">Freshness</span>
                <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded border ${
                  isLocationDisabled
                    ? 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800'
                    : isStale
                    ? 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800'
                    : 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
                }`}>
                  {isLocationDisabled ? 'CACHED (LOCATION OFF)' : isStale ? 'STALE (CACHED)' : 'LIVE'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">Speed</span>
                <span className="font-mono">{(latestLocation.speed ?? 0).toFixed(1)} km/h</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500 dark:text-slate-400">Accuracy</span>
                <span className="font-mono">±{latestLocation.accuracy.toFixed(0)}m</span>
              </div>
              <div className="pt-2 border-t border-slate-200 dark:border-slate-800 flex items-center gap-1.5 text-[11px] text-slate-400 font-mono">
                <Clock className="w-3.5 h-3.5" />
                {latestLocation.timestamp ? new Date(latestLocation.timestamp).toLocaleString() : 'N/A'}
              </div>
            </div>
          </div>
        ) : (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-50/80 dark:bg-[#0A0E17]/80 z-[300]">
            <div className="text-center p-6 bg-white dark:bg-[#121927] rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm max-w-sm">
              <MapPin className="w-10 h-10 text-slate-400 mx-auto mb-2 opacity-50" />
              <p className="font-bold text-slate-900 dark:text-white text-sm font-mono">No Location Coordinates</p>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                This device has not yet transmitted GPS location records. Ensure SecureTrack background tracking is active on the phone.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
