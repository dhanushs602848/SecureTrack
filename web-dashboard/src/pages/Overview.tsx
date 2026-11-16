import React, { useEffect, useState } from 'react';
import { Smartphone, ShieldCheck, MapPin, AlertTriangle } from 'lucide-react';
import { deviceService, locationService, securityService } from '../services/api';
import { Device, LocationRecord, SecurityEvent } from '../types';
import { LocationMap } from '../components/LocationMap';

export const Overview: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [latestLocations, setLatestLocations] = useState<LocationRecord[]>([]);
  const [securityEvents, setSecurityEvents] = useState<SecurityEvent[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const deviceList = await deviceService.getDevices();
        setDevices(deviceList);

        const events = await securityService.getSecurityEvents();
        setSecurityEvents(events.slice(0, 5));

        if (deviceList.length > 0) {
          const locPromises = deviceList.map(d =>
            locationService.getLatestLocation(d.id).catch(() => null)
          );
          const locResults = await Promise.all(locPromises);
          setLatestLocations(locResults.filter((l): l is LocationRecord => l !== null));
        }
      } catch (e) {
        console.error('Failed to load overview data:', e);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  const totalDevices = devices.length;
  const onlineDevices = devices.filter(d => d.status === 'ONLINE').length;
  const alertsCount = securityEvents.length;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">System Protection Dashboard</h2>
          <p className="text-sm text-[#62666D]">Real-time mobile tracking and anti-theft monitoring</p>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4">
          <div className="p-3 bg-[#D8E6FF] text-[#152C4F] rounded-xl">
            <Smartphone className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-[#62666D]">Total Devices</p>
            <p className="text-2xl font-bold text-[#152C4F]">{totalDevices}</p>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4">
          <div className="p-3 bg-emerald-100 text-emerald-700 rounded-xl">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-[#62666D]">Online &amp; Active</p>
            <p className="text-2xl font-bold text-emerald-700">{onlineDevices}</p>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4">
          <div className="p-3 bg-blue-100 text-[#255DA8] rounded-xl">
            <MapPin className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-[#62666D]">Tracked Pins</p>
            <p className="text-2xl font-bold text-[#255DA8]">{latestLocations.length}</p>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-[#DFE2E7] shadow-sm flex items-center gap-4">
          <div className="p-3 bg-amber-100 text-amber-800 rounded-xl">
            <AlertTriangle className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-medium text-[#62666D]">Security Alerts</p>
            <p className="text-2xl font-bold text-amber-800">{alertsCount}</p>
          </div>
        </div>
      </div>

      {/* Main Grid: Map & Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-bold text-[#152C4F]">Live Device Map</h3>
            <span className="text-xs text-[#62666D]">Integrated OpenStreetMap Engine</span>
          </div>
          <div className="h-[380px]">
            <LocationMap
              locations={latestLocations.map(l => ({
                latitude: l.latitude,
                longitude: l.longitude,
                label: `Device #${l.deviceId}`,
                timestamp: l.timestamp
              }))}
            />
          </div>
        </div>

        {/* Security Alerts List */}
        <div className="bg-white p-6 rounded-2xl border border-[#DFE2E7] shadow-sm space-y-4">
          <h3 className="text-lg font-bold text-[#152C4F]">Recent Security Logs</h3>
          {securityEvents.length === 0 ? (
            <p className="text-sm text-[#62666D] py-8 text-center">No recent security alerts</p>
          ) : (
            <div className="space-y-3">
              {securityEvents.map((evt) => (
                <div key={evt.id} className="p-3 bg-[#F0F2F5] rounded-xl border border-[#DFE2E7] space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-[#152C4F]">{evt.eventType}</span>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-200 text-amber-900">
                      {evt.severity}
                    </span>
                  </div>
                  <p className="text-xs text-[#1A1C1E]">{evt.description}</p>
                  <p className="text-[10px] text-[#62666D]">{evt.timestamp}</p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
