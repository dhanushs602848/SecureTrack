import React, { useEffect, useState, useMemo } from 'react';
import { LocationMap } from '../components/LocationMap';
import { locationService, deviceService } from '../services/api';
import { LocationRecord, Device } from '../types';
import { Clock, MapPin, Navigation, Filter, Calendar } from 'lucide-react';
import { EmptyState } from '../components/ui/EmptyState';
import { Skeleton } from '../components/ui/Skeleton';

type TimeFilter = 'ALL' | 'TODAY' | '24H' | '7D';

export const LocationHistory: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [history, setHistory] = useState<LocationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedRecordIndex, setSelectedRecordIndex] = useState<number | null>(null);
  const [timeFilter, setTimeFilter] = useState<TimeFilter>('ALL');

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
      setSelectedRecordIndex(null);
      const unsubLoc = locationService.subscribeToLocationHistory(selectedDeviceId, (data) => {
        setHistory(data);
        setLoading(false);
      });
      return () => unsubLoc();
    }
  }, [selectedDeviceId]);

  // Sensible filters based on existing timestamps
  const filteredHistory = useMemo(() => {
    const now = Date.now();
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);

    return history.filter((loc) => {
      if (timeFilter === 'ALL') return true;
      const ts = new Date(loc.timestamp).getTime();
      if (isNaN(ts)) return true;

      if (timeFilter === 'TODAY') {
        return ts >= startOfToday.getTime();
      }
      if (timeFilter === '24H') {
        return now - ts <= 24 * 60 * 60 * 1000;
      }
      if (timeFilter === '7D') {
        return now - ts <= 7 * 24 * 60 * 60 * 1000;
      }
      return true;
    });
  }, [history, timeFilter]);

  // Focus coordinates on selected timeline point or first point
  const mapCenter = useMemo((): [number, number] | undefined => {
    if (selectedRecordIndex !== null && filteredHistory[selectedRecordIndex]) {
      const rec = filteredHistory[selectedRecordIndex];
      return [rec.latitude, rec.longitude];
    }
    if (filteredHistory.length > 0) {
      return [filteredHistory[0].latitude, filteredHistory[0].longitude];
    }
    return undefined;
  }, [selectedRecordIndex, filteredHistory]);

  const selectedDevice = useMemo(
    () => devices.find((d) => d.id === selectedDeviceId),
    [devices, selectedDeviceId]
  );

  return (
    <div className="space-y-4 flex flex-col h-[calc(100vh-6.5rem)] max-w-7xl mx-auto transition-colors duration-200">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 shrink-0">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
            Location History &amp; Breadcrumb Trail
          </h1>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            Review historical endpoint coordinates, movement trails, and accuracy fixes
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label htmlFor="device-history-select" className="sr-only">
            Select Device
          </label>
          <select
            id="device-history-select"
            value={selectedDeviceId}
            onChange={(e) => setSelectedDeviceId(e.target.value)}
            className="w-full sm:w-64 bg-white dark:bg-[#121927] border border-slate-300 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-2 focus:ring-cyan-500 p-2.5 shadow-xs"
          >
            <option value="" disabled>
              Select a device
            </option>
            {devices.map((d) => (
              <option key={d.id} value={d.id}>
                {d.deviceName} ({d.model || 'Android'})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Filter Chips Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 shrink-0">
        <div className="flex items-center gap-1.5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] p-1 rounded-xl text-xs">
          <span className="px-2 text-slate-400 flex items-center gap-1 text-[11px]">
            <Filter className="w-3 h-3" /> Filter:
          </span>
          <button
            type="button"
            onClick={() => {
              setTimeFilter('ALL');
              setSelectedRecordIndex(null);
            }}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
              timeFilter === 'ALL'
                ? 'bg-indigo-600 text-white font-semibold'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            All ({history.length})
          </button>
          <button
            type="button"
            onClick={() => {
              setTimeFilter('TODAY');
              setSelectedRecordIndex(null);
            }}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
              timeFilter === 'TODAY'
                ? 'bg-indigo-600 text-white font-semibold'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            Today
          </button>
          <button
            type="button"
            onClick={() => {
              setTimeFilter('24H');
              setSelectedRecordIndex(null);
            }}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
              timeFilter === '24H'
                ? 'bg-indigo-600 text-white font-semibold'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            Last 24 Hours
          </button>
          <button
            type="button"
            onClick={() => {
              setTimeFilter('7D');
              setSelectedRecordIndex(null);
            }}
            className={`px-3 py-1.5 rounded-lg font-medium transition-colors ${
              timeFilter === '7D'
                ? 'bg-indigo-600 text-white font-semibold'
                : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            Last 7 Days
          </button>
        </div>

        {selectedRecordIndex !== null && (
          <button
            type="button"
            onClick={() => setSelectedRecordIndex(null)}
            className="text-xs text-indigo-600 dark:text-cyan-400 hover:underline font-medium"
          >
            Reset Selection (Show Full Path)
          </button>
        )}
      </div>

      {/* Main Split Layout: Timeline List & Map */}
      <div className="flex flex-col lg:flex-row gap-4 flex-1 min-h-0">
        {/* Timeline Panel */}
        <div className="lg:w-1/3 bg-white dark:bg-[#121927] rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] overflow-y-auto flex flex-col">
          <div className="p-4 border-b border-slate-200 dark:border-[#223049] bg-slate-50 dark:bg-[#162032] sticky top-0 z-10 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
              <h2 className="font-bold text-slate-900 dark:text-white text-xs font-mono">
                Timeline ({filteredHistory.length})
              </h2>
            </div>
            <span className="text-[11px] text-slate-500">Tap record to center on map</span>
          </div>

          <div className="divide-y divide-slate-100 dark:divide-slate-800/80 flex-1 overflow-y-auto">
            {loading ? (
              <div className="p-6 space-y-4">
                <Skeleton className="w-full h-12" />
                <Skeleton className="w-full h-12" />
                <Skeleton className="w-full h-12" />
              </div>
            ) : filteredHistory.length === 0 ? (
              <div className="p-8 text-center text-slate-400">
                <MapPin className="w-8 h-8 mx-auto mb-2 opacity-50" />
                <p className="text-xs font-medium text-slate-700 dark:text-slate-300">
                  No location records found
                </p>
                <p className="text-[11px] text-slate-500 mt-1">
                  {timeFilter !== 'ALL'
                    ? 'No records match this time filter. Try selecting "All".'
                    : 'This device has not uploaded location coordinates yet.'}
                </p>
              </div>
            ) : (
              filteredHistory.map((loc, idx) => {
                const isSelected = selectedRecordIndex === idx;
                return (
                  <div
                    key={loc.id || idx}
                    onClick={() => setSelectedRecordIndex(idx)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        setSelectedRecordIndex(idx);
                      }
                    }}
                    className={`p-4 transition-all group cursor-pointer text-left ${
                      isSelected
                        ? 'bg-indigo-50 dark:bg-indigo-950/40 border-l-4 border-indigo-600 dark:border-cyan-400'
                        : 'hover:bg-slate-50 dark:hover:bg-[#162032]'
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`mt-1.5 w-2.5 h-2.5 rounded-full shrink-0 transition-transform ${
                          isSelected
                            ? 'bg-indigo-600 dark:bg-cyan-400 ring-4 ring-indigo-500/20 dark:ring-cyan-400/20 scale-125'
                            : 'bg-slate-400 group-hover:scale-125'
                        }`}
                      />
                      <div className="space-y-1 text-xs flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <p className="font-bold text-slate-900 dark:text-white font-mono text-[11px]">
                            {new Date(loc.timestamp).toLocaleTimeString([], {
                              hour: '2-digit',
                              minute: '2-digit',
                              second: '2-digit',
                            })}
                          </p>
                          <span className="text-[10px] text-slate-400 font-mono">
                            {new Date(loc.timestamp).toLocaleDateString([], {
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                        </div>

                        <p className="text-slate-600 dark:text-slate-400 font-mono text-[11px] truncate">
                          {loc.latitude.toFixed(5)}, {loc.longitude.toFixed(5)}
                        </p>

                        <div className="flex items-center gap-3 text-[11px] text-slate-500 dark:text-slate-400 pt-0.5">
                          <span className="flex items-center gap-1 font-mono">
                            <Navigation className="w-3 h-3 text-cyan-500" />
                            {(loc.speed ?? 0).toFixed(1)} km/h
                          </span>
                          <span className="font-mono">±{(loc.accuracy ?? 0).toFixed(0)}m</span>
                          <span className="font-mono text-[9px] uppercase px-1.5 py-0.2 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
                            {loc.source || 'GPS'}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Map Panel */}
        <div className="lg:w-2/3 bg-white dark:bg-[#121927] rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] overflow-hidden min-h-[350px]">
          <LocationMap
            locations={filteredHistory.map((h, i) => ({
              ...h,
              label: `${selectedDevice?.deviceName || 'Handset'} Fix #${i + 1}`,
            }))}
            center={mapCenter}
            zoom={selectedRecordIndex !== null ? 17 : 14}
            showPath={true}
          />
        </div>
      </div>
    </div>
  );
};
