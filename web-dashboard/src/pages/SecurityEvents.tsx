import React, { useEffect, useState } from 'react';
import { ShieldAlert, AlertTriangle, Info, Shield, CheckCircle, RotateCcw, Trash2, Check } from 'lucide-react';
import { securityService, deviceService } from '../services/api';
import { SecurityEvent, Device } from '../types';

export const SecurityEvents: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [events, setEvents] = useState<SecurityEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'ALL' | 'ACTIVE' | 'RESOLVED' | 'DISMISSED'>('ALL');
  const [actionMessage, setActionMessage] = useState<string | null>(null);

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
      const unsubAlerts = securityService.subscribeToSecurityEvents((data) => {
        setEvents(data);
        setLoading(false);
      }, selectedDeviceId);
      return () => unsubAlerts();
    }
  }, [selectedDeviceId]);

  const showNotification = (msg: string) => {
    setActionMessage(msg);
    setTimeout(() => setActionMessage(null), 4000);
  };

  const handleDismiss = async (alertId: string) => {
    try {
      await securityService.dismissAlert(alertId);
      showNotification('Alert dismissed (historical record preserved)');
    } catch (err: any) {
      alert('Failed to dismiss alert: ' + (err?.message || err));
    }
  };

  const handleRestore = async (alertId: string) => {
    try {
      await securityService.restoreAlert(alertId);
      showNotification('Alert restored to active status');
    } catch (err: any) {
      alert('Failed to restore alert: ' + (err?.message || err));
    }
  };

  const handleBulkDismissResolved = async () => {
    try {
      const count = await securityService.bulkDismissResolvedAlerts();
      showNotification(`${count} resolved alerts dismissed`);
    } catch (err: any) {
      alert('Bulk dismiss failed: ' + (err?.message || err));
    }
  };

  const getSeverityIcon = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return <ShieldAlert className="w-5 h-5 text-red-500" />;
      case 'HIGH': return <AlertTriangle className="w-5 h-5 text-orange-500" />;
      case 'WARNING': return <AlertTriangle className="w-5 h-5 text-amber-500" />;
      default: return <Info className="w-5 h-5 text-cyan-400" />;
    }
  };

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return 'bg-red-950/40 text-red-400 border-red-800';
      case 'HIGH': return 'bg-orange-950/40 text-orange-400 border-orange-800';
      case 'WARNING': return 'bg-amber-950/40 text-amber-400 border-amber-800';
      default: return 'bg-cyan-950/40 text-cyan-400 border-cyan-800';
    }
  };

  const filteredEvents = events.filter((ev) => {
    const status = ev.status || 'ACTIVE';
    if (filter === 'ALL') {
      return status !== 'DISMISSED';
    }
    return status === filter;
  });

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Security Center &amp; Alerts</h2>
          <p className="text-sm text-[#62666D]">Review telemetry alarms, device alerts, and safe audit dismissals</p>
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

      {actionMessage && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl text-sm flex items-center justify-between">
          <span>{actionMessage}</span>
          <Check className="w-4 h-4 text-emerald-600" />
        </div>
      )}

      {/* Filter Tabs & Bulk Actions */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 bg-gray-100 p-1 rounded-xl">
          {(['ALL', 'ACTIVE', 'RESOLVED', 'DISMISSED'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setFilter(tab)}
              className={`px-4 py-1.5 text-xs font-bold rounded-lg transition-all ${
                filter === tab
                  ? 'bg-white text-[#152C4F] shadow-sm'
                  : 'text-[#62666D] hover:text-[#152C4F]'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        <button
          onClick={handleBulkDismissResolved}
          className="px-3 py-1.5 text-xs font-semibold text-[#255DA8] hover:bg-blue-50 border border-[#D8E6FF] rounded-lg transition-colors flex items-center gap-1.5"
        >
          <CheckCircle className="w-3.5 h-3.5" />
          Dismiss Resolved Alerts
        </button>
      </div>

      {/* Events List */}
      <div className="bg-white rounded-2xl shadow-sm border border-[#DFE2E7] overflow-hidden">
        <div className="p-4 border-b border-[#DFE2E7] bg-gray-50 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-[#255DA8]" />
            <h3 className="font-bold text-[#152C4F]">Audit Trail ({filteredEvents.length})</h3>
          </div>
          <span className="text-xs text-[#62666D]">Safe dismissal keeps historical log intact</span>
        </div>

        <div className="divide-y divide-[#DFE2E7]">
          {loading ? (
            <div className="p-12 text-center text-[#62666D]">Loading security events...</div>
          ) : filteredEvents.length === 0 ? (
            <div className="p-16 text-center text-[#62666D] space-y-3">
              <Shield className="w-12 h-12 mx-auto opacity-20" />
              <p className="font-semibold text-[#152C4F]">No Security Alerts Found</p>
              <p className="text-sm">There are no {filter.toLowerCase()} security events matching this view.</p>
            </div>
          ) : (
            filteredEvents.map((event) => {
              const isDismissed = event.status === 'DISMISSED';
              return (
                <div
                  key={event.id}
                  className={`p-5 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                    isDismissed ? 'bg-gray-50/60 opacity-70' : 'hover:bg-gray-50'
                  }`}
                >
                  <div className="flex items-start gap-4 flex-1">
                    <div className="flex-shrink-0 mt-0.5">
                      <div className="w-10 h-10 rounded-full bg-white border border-gray-200 shadow-sm flex items-center justify-center">
                        {getSeverityIcon(event.severity)}
                      </div>
                    </div>
                    <div className="space-y-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h4 className="font-bold text-[#152C4F]">{event.eventType}</h4>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider ${getSeverityBadge(event.severity)}`}>
                          {event.severity}
                        </span>
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-gray-100 text-gray-700 uppercase">
                          {event.status || 'ACTIVE'}
                        </span>
                      </div>
                      <p className="text-sm text-[#62666D]">{event.description}</p>
                      <p className="text-xs font-mono text-gray-400">
                        {new Date(event.timestamp).toLocaleString()}
                        {event.deviceName ? ` • Device: ${event.deviceName}` : ''}
                      </p>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 flex-shrink-0 self-end sm:self-center">
                    {isDismissed ? (
                      <button
                        onClick={() => handleRestore(event.id)}
                        className="p-2 text-gray-600 hover:text-[#255DA8] hover:bg-blue-50 rounded-lg transition-colors flex items-center gap-1 text-xs"
                        title="Restore to Active"
                      >
                        <RotateCcw className="w-4 h-4" />
                        <span>Restore</span>
                      </button>
                    ) : (
                      <button
                        onClick={() => handleDismiss(event.id)}
                        className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors flex items-center gap-1 text-xs"
                        title="Dismiss Alert"
                      >
                        <Trash2 className="w-4 h-4" />
                        <span>Dismiss</span>
                      </button>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
