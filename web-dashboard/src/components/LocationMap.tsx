import React from 'react';
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet';
import L from 'leaflet';

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
  locations: { latitude: number; longitude: number; label?: string; timestamp?: string }[];
  center?: [number, number];
  zoom?: number;
}

export const LocationMap: React.FC<LocationMapProps> = ({
  locations,
  center = [37.7749, -122.4194],
  zoom = 13,
}) => {
  const mapCenter: [number, number] = locations.length > 0
    ? [locations[0].latitude, locations[0].longitude]
    : center;

  const polylinePositions: [number, number][] = locations.map(l => [l.latitude, l.longitude]);

  return (
    <div className="w-full h-full min-h-[350px] rounded-2xl overflow-hidden border border-[#DFE2E7] shadow-sm relative">
      <MapContainer
        center={mapCenter}
        zoom={zoom}
        style={{ width: '100%', height: '100%', minHeight: '350px' }}
      >
        {/* Mandatory OpenStreetMap attribution */}
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        {locations.map((loc, idx) => (
          <Marker key={idx} position={[loc.latitude, loc.longitude]}>
            <Popup>
              <div className="text-xs space-y-1">
                <p className="font-bold text-[#152C4F]">{loc.label || `Point #${idx + 1}`}</p>
                <p>Lat: {loc.latitude.toFixed(6)}</p>
                <p>Lng: {loc.longitude.toFixed(6)}</p>
                {loc.timestamp && <p className="text-gray-500">{loc.timestamp}</p>}
              </div>
            </Popup>
          </Marker>
        ))}

        {polylinePositions.length > 1 && (
          <Polyline positions={polylinePositions} color="#255DA8" weight={4} opacity={0.7} />
        )}
      </MapContainer>
    </div>
  );
};
