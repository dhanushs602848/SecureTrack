import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline, useMap } from 'react-leaflet';
import L from 'leaflet';
import { MapPinOff } from 'lucide-react';

// Fix leaflet marker icon default path issue
import icon from 'leaflet/dist/images/marker-icon.png';
import iconShadow from 'leaflet/dist/images/marker-shadow.png';

const DefaultIcon = L.icon({
  iconUrl: icon,
  shadowUrl: iconShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});
L.Marker.prototype.options.icon = DefaultIcon;

interface LocationMapProps {
  locations: {
    latitude: number;
    longitude: number;
    label?: string;
    timestamp?: string;
    source?: string;
    accuracy?: number;
  }[];
  center?: [number, number];
  zoom?: number;
  showPath?: boolean;
}

const ChangeMapView: React.FC<{ center: [number, number]; zoom: number }> = ({ center, zoom }) => {
  const map = useMap();
  useEffect(() => {
    map.setView(center, zoom, { animate: true });
  }, [center[0], center[1], zoom, map]);
  return null;
};

export const LocationMap: React.FC<LocationMapProps> = ({
  locations,
  center,
  zoom = 15,
  showPath = false,
}) => {
  // If no locations and no explicit center, display a genuine empty state (do not use placeholder coordinates)
  if (locations.length === 0 && !center) {
    return (
      <div className="w-full h-full min-h-[350px] rounded-2xl flex flex-col items-center justify-center p-8 bg-slate-50 dark:bg-[#0D131F] border border-slate-200 dark:border-[#223049] text-center space-y-3">
        <div className="w-12 h-12 rounded-xl bg-slate-100 dark:bg-[#162032] border border-slate-300 dark:border-slate-700 flex items-center justify-center text-slate-400">
          <MapPinOff className="w-6 h-6" />
        </div>
        <div>
          <h4 className="font-bold text-sm text-slate-900 dark:text-white font-mono">No Location Telemetry Available</h4>
          <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm mt-1">
            This endpoint has not transmitted coordinates yet. Trigger "Locate Now" or ensure Location is enabled on the device.
          </p>
        </div>
      </div>
    );
  }

  // Derive center strictly from the device's actual verified location
  const mapCenter: [number, number] = locations.length > 0
    ? [locations[0].latitude, locations[0].longitude]
    : center!;

  const polylinePositions: [number, number][] = locations.map((l) => [l.latitude, l.longitude]);

  return (
    <div className="w-full h-full min-h-[350px] rounded-2xl overflow-hidden border border-slate-200 dark:border-[#223049] shadow-sm relative">
      <MapContainer
        center={mapCenter}
        zoom={zoom}
        style={{ width: '100%', height: '100%', minHeight: '350px' }}
      >
        <ChangeMapView center={mapCenter} zoom={zoom} />

        {/* Mandatory OpenStreetMap attribution */}
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {locations.map((loc, idx) => (
          <Marker key={idx} position={[loc.latitude, loc.longitude]}>
            <Popup>
              <div className="text-xs space-y-1.5 font-sans p-1">
                <div className="flex items-center justify-between gap-2 border-b border-slate-200 dark:border-slate-700 pb-1">
                  <p className="font-bold text-slate-900 dark:text-white">{loc.label || `Fix #${idx + 1}`}</p>
                  <span className="text-[9px] font-mono font-bold px-1.5 py-0.5 rounded bg-indigo-100 dark:bg-cyan-950 text-indigo-700 dark:text-cyan-300 border border-indigo-200 dark:border-cyan-800">
                    {loc.source || 'GPS'}
                  </span>
                </div>
                <p className="font-mono text-[11px] text-slate-700 dark:text-slate-300">
                  Lat: {loc.latitude.toFixed(6)}, Lng: {loc.longitude.toFixed(6)}
                </p>
                {loc.accuracy !== undefined && loc.accuracy > 0 && (
                  <p className="text-[10px] text-slate-500 font-mono">Accuracy: ±{loc.accuracy.toFixed(0)}m</p>
                )}
                {loc.timestamp && (
                  <p className="text-slate-400 font-mono text-[10px]">
                    {new Date(loc.timestamp).toLocaleString()}
                  </p>
                )}
              </div>
            </Popup>
          </Marker>
        ))}

        {showPath && polylinePositions.length > 1 && (
          <Polyline positions={polylinePositions} color="#06b6d4" weight={4} opacity={0.8} />
        )}
      </MapContainer>
    </div>
  );
};
