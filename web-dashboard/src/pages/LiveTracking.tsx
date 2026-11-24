import React, { useEffect, useState, useMemo } from 'react';
import { LocationMap } from '../components/LocationMap';
import { locationService, deviceService, commandService } from '../services/api';
import { LocationRecord, Device } from '../types';
import { Activity, Clock, AlertTriangle, MapPin, RefreshCw, Smartphone, Battery, Shield, Navigation } from 'lucide-react';
import { useToast } from '../context/ToastContext';

export const LiveTracking: React.FC = () => {
  const toast = useToast();
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [latestLocation, setLatestLocation] = useState<LocationRecord | null>(null);
  const [isLocating, setIsLocating] = useState<boolean>(false);
  const [locateStatusMsg, setLocateStatusMsg] = useState<string | null>(null);

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
    if (!selectedDeviceId) return;

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
  }, [selectedDeviceId, devices]);

  const selectedDevice = useMemo(
    () => devices.find((d) => d.id === selectedDeviceId),
    [devices, selectedDeviceId]
  );

  const isLocationDisabled = selectedDevice?.locationStatus === 'DISABLED';
  const locationAgeMs = latestLocation?.timestamp
    ? Date.now() - new Date(latestLocation.timestamp).getTime()
    : null;
  const isStale = locationAgeMs !== null ? locationAgeMs > 15 * 60 * 1000 : true;

  // Determine unambiguous location status
  const locationState = useMemo(() => {
    if (!latestLocation) return { label: 'UNAVAILABLE', color: 'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700' };
    if (isLocationDisabled) return { label: 'LAST KNOWN (LOCATION OFF)', color: 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-950/60 dark:text-amber-300 dark:border-amber-800' };
    if (isStale) return { label: 'STALE (CACHED)', color: 'bg-amber-100 text-amber-800 border-amber-300 dark:bg-amber-950/60 dark:text-amber-300 dark:border-amber-800' };
    return { label: 'LIVE FIX', color: 'bg-emerald-100 text-emerald-800 border-emerald-300 dark:bg-emerald-950/60 dark:text-emerald-300 dark:border-emerald-800' };
  }, [latestLocation, isLocationDisabled, isStale]);

  const handleLocateNow = async () => {
    if (!selectedDeviceId) return;
    setIsLocating(true);
    setLocateStatusMsg('Dispatching live GPS location request to device...');

    try {
      // Use exact command 'LOCATE_NOW' (preserves anti-misuse rules and device execution)
      await commandService.sendCommand(selectedDeviceId, 'LOCATE_NOW', 'Dashboard Locate Now');
      setLocateStatusMsg('Locate command dispatched. Awaiting device GPS response...');
      toast.info('Command queued. When handset reports new coordinates, map will automatically update.', 'Locate Dispatched');
      setTimeout(() => setLocateStatusMsg(null), 8000);
    } catch (e: any) {
      setLocateStatusMsg(null);
      toast.error(e.message || 'Failed to dispatch locate command', 'Locate Error');
    } finally {
      setIsLocating(false);
    }
  };

  const formatRelativeTime = (isoString?: string) => {
    if (!isoString) return 'Unknown';
    const diffSec = Math.floor((Date.now() - new Date(isoString).getTime()) / 1000);
    if (diffSec < 60) return `${diffSec}s ago`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m ago`;
    const diffHour = Math.floor(diffMin / 60);
    if (diffHour < 24) return `${diffHour}h ago`;
    return new Date(isoString).toLocaleDateString();
  };

  return (
    <div className="space-y-4 flex flex-col h-[calc(100vh-6.5rem)] max-w-7xl mx-auto transition-colors duration-200">
      {/* Top Header & Device Selection */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
            Live Endpoint Tracking
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Real-time GPS positioning, telemetry freshness, and remote location dispatch
          </p>
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <label htmlFor="device-select" className="sr-only">
            Select Device to Track
          </label>
          <select
            id="device-select"
            value={selectedDeviceId}
            onChange={(e) => setSelectedDeviceId(e.target.value)}
            className="w-full sm:w-72 bg-white dark:bg-[#121927] border border-slate-300 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-2 focus:ring-cyan-500 p-2.5 shadow-xs"
          >
            <option value="" disabled>
              Select a device to track
            </option>
            {devices.map((d) => (
              <option key={d.id} value={d.id}>
                {d.deviceName} ({d.model || 'Android'}) • {d.status}
              </option>
            ))}
          </select>

          <button
            type="button"
            onClick={handleLocateNow}
            disabled={!selectedDeviceId || isLocating}
            className="flex items-center gap-1.5 px-3.5 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-xs transition-colors shrink-0"
            title="Dispatch immediate real-time GPS location refresh command"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLocating ? 'animate-spin' : ''}`} />
            <span>{isLocating ? 'Locating...' : 'Locate Now'}</span>
          </button>
        </div>
      </div>

      {/* Selected Device Context Bar */}
      {selectedDevice && (
        <div className="flex flex-wrap items-center justify-between gap-3 p-3 bg-white dark:bg-[#121927] rounded-2xl border border-slate-200 dark:border-[#223049] text-xs shadow-xs shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-xl bg-indigo-50 dark:bg-indigo-950/60 text-indigo-600 dark:text-cyan-400 flex items-center justify-center border border-indigo-200 dark:border-indigo-800">
              <Smartphone className="w-4 h-4" />
            </div>
            <div>
              <span className="font-bold text-slate-900 dark:text-white">
                {selectedDevice.deviceName}
              </span>
              <span className="text-slate-500 dark:text-slate-400 ml-2">
                {selectedDevice.model || 'Android'} • Android {selectedDevice.androidVersion || '14'}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <span className="flex items-center gap-1 text-slate-600 dark:text-slate-300">
              <Battery className="w-3.5 h-3.5 text-emerald-500" />
              {selectedDevice.batteryLevel ?? 'N/A'}%
            </span>
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-mono font-bold border ${locationState.color}`}
            >
              {locationState.label}
            </span>
          </div>
        </div>
      )}

      {/* Dispatch status feedback */}
      {locateStatusMsg && (
        <div className="p-3 bg-indigo-50 dark:bg-indigo-950/50 border border-indigo-200 dark:border-indigo-800 text-indigo-900 dark:text-indigo-200 rounded-xl text-xs flex items-center gap-2 shrink-0 animate-fade-in">
          <Activity className="w-4 h-4 text-indigo-600 dark:text-cyan-400 animate-spin shrink-0" />
          <span>{locateStatusMsg}</span>
        </div>
      )}

      {/* Location Disabled Warning Banner */}
      {isLocationDisabled && (
        <div className="p-3.5 bg-amber-50 dark:bg-[#201910] border border-amber-300 dark:border-amber-700/80 rounded-2xl flex items-center gap-3 text-amber-900 dark:text-amber-200 text-xs shadow-xs shrink-0">
          <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0" />
          <div>
            <strong className="font-bold">Location Disabled on Handset:</strong> The device user or system has turned off Location Services. Displaying last verified coordinates recorded before it was turned off.
          </div>
        </div>
      )}

      {/* Map Container & Telemetry Floating Card */}
      <div className="flex-1 bg-white dark:bg-[#121927] rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] overflow-hidden relative min-h-[350px]">
        <LocationMap
          locations={
            latestLocation
              ? [
                  {
                    ...latestLocation,
                    label: selectedDevice?.deviceName || 'Handset',
                  },
                ]
              : []
          }
        />

        {/* Telemetry Overlay Card */}
        {latestLocation && (
          <div className="absolute top-4 right-4 bg-white/95 dark:bg-[#121927]/95 backdrop-blur-md p-4 rounded-2xl shadow-xl border border-slate-200 dark:border-[#223049] max-w-xs w-full z-[400] space-y-2.5 text-xs">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-2">
              <span className="font-bold text-slate-900 dark:text-white flex items-center gap-1.5 font-mono">
                <Activity className="w-3.5 h-3.5 text-emerald-500" />
                Verified Telemetry
              </span>
              <span className={`text-[9px] font-mono font-bold px-1.5 py-0.5 rounded border ${locationState.color}`}>
                {locationState.label}
              </span>
            </div>

            <div className="space-y-1.5 text-slate-600 dark:text-slate-300">
              <div className="flex justify-between">
                <span className="text-slate-400">Coordinates</span>
                <span className="font-mono text-[11px] font-semibold text-slate-900 dark:text-cyan-400 select-all">
                  {latestLocation.latitude.toFixed(5)}, {latestLocation.longitude.toFixed(5)}
                </span>
              </div>

              <div className="flex justify-between">
                <span className="text-slate-400">Provider</span>
                <span className="font-mono text-[10px] uppercase font-bold text-slate-800 dark:text-slate-200">
                  {isLocationDisabled ? 'LAST_KNOWN' : latestLocation.source || 'GPS'}
                </span>
              </div>

              <div className="flex justify-between">
                <span className="text-slate-400">Accuracy</span>
                <span className="font-mono">
                  {latestLocation.accuracy ? `±${latestLocation.accuracy.toFixed(0)}m` : '±15m'}
                </span>
              </div>

              <div className="flex justify-between">
                <span className="text-slate-400">Speed</span>
                <span className="font-mono">
                  {(latestLocation.speed ?? 0).toFixed(1)} km/h
                </span>
              </div>

              <div className="pt-2 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between text-[10px] text-slate-400 font-mono">
                <span className="flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  {formatRelativeTime(latestLocation.timestamp)}
                </span>
                <span>
                  {latestLocation.timestamp
                    ? new Date(latestLocation.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                    : ''}
                </span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
