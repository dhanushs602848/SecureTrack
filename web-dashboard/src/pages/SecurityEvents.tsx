import React, { useEffect, useState } from 'react';
import { ShieldAlert, AlertTriangle, Info, Shield, CheckCircle, RotateCcw, Check } from 'lucide-react';
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
      case 'CRITICAL': return <ShieldAlert className="w-5 h-5 text-rose-500" />;
      case 'HIGH': return <AlertTriangle className="w-5 h-5 text-orange-500" />;
      case 'WARNING': return <AlertTriangle className="w-5 h-5 text-amber-500" />;
      default: return <Info className="w-5 h-5 text-cyan-500 dark:text-cyan-400" />;
    }
  };

  const getSeverityBadge = (severity: string) => {
    switch (severity) {
      case 'CRITICAL': return 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800';
      case 'HIGH': return 'bg-orange-100 dark:bg-orange-950/40 text-orange-700 dark:text-orange-400 border-orange-300 dark:border-orange-800';
      case 'WARNING': return 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800';
      default: return 'bg-cyan-100 dark:bg-cyan-950/40 text-cyan-700 dark:text-cyan-400 border-cyan-300 dark:border-cyan-800';
    }
  };

  const activeEvents = events.filter(e => (e.status || 'ACTIVE') === 'ACTIVE');
  const dismissedEvents = events.filter(e => e.status === 'DISMISSED');
  const resolvedEvents = events.filter(e => e.status === 'RESOLVED');

  const filteredEvents = events.filter((ev) => {
    const status = ev.status || 'ACTIVE';
    if (filter === 'ALL') {
      return status !== 'DISMISSED';
    }
    return status === filter;
  });

  return (
    <div className="space-y-6 max-w-5xl mx-auto transition-colors duration-200">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h2 className="text-2xl font-bold text-slate-900 dark:text-white font-mono">Security Center &amp; Alerts</h2>
            <span className={`text-xs font-bold px-2.5 py-0.5 rounded-full border ${
              activeEvents.length === 0
                ? 'bg-emerald-100 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
                : 'bg-rose-100 dark:bg-rose-950/50 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800'
            }`}>
              {activeEvents.length} Active
            </span>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">Telemetry alarms, perimeter alerts, and safe audit dismissals</p>
        </div>
        <select
          value={selectedDeviceId}
          onChange={(e) => setSelectedDeviceId(e.target.value)}
          className="w-full sm:w-64 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] text-slate-900 dark:text-slate-200 text-xs rounded-xl focus:ring-cyan-500 focus:border-cyan-500 p-2.5 shadow-sm"
        >
          <option value="" disabled>Select a device</option>
          {devices.map(d => (
            <option key={d.id} value={d.id}>{d.deviceName} ({d.model})</option>
          ))}
        </select>
      </div>

      {actionMessage && (
        <div className="p-3 bg-emerald-100 dark:bg-emerald-950/60 border border-emerald-300 dark:border-emerald-700/60 text-emerald-800 dark:text-emerald-300 rounded-xl text-xs flex items-center justify-between shadow-lg">
          <span className="font-medium">{actionMessage}</span>
          <Check className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
        </div>
      )}

      {/* Filter Tabs & Bulk Actions */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1.5 bg-slate-100 dark:bg-[#121927] border border-slate-200 dark:border-[#223049] p-1 rounded-xl">
          <button
            onClick={() => setFilter('ACTIVE')}
            className={`px-3 py-1.5 text-xs font-bold rounded-lg transition-all ${
              filter === 'ACTIVE'
                ? 'bg-cyan-500 text-black shadow-md'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            ACTIVE ({activeEvents.length})
          </button>
          <button
            onClick={() => setFilter('DISMISSED')}
            className={`px-3 py-1.5 text-xs font-bold rounded-lg transition-all ${
              filter === 'DISMISSED'
                ? 'bg-cyan-500 text-black shadow-md'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            DISMISSED ({dismissedEvents.length})
          </button>
          <button
            onClick={() => setFilter('RESOLVED')}
            className={`px-3 py-1.5 text-xs font-bold rounded-lg transition-all ${
              filter === 'RESOLVED'
                ? 'bg-cyan-500 text-black shadow-md'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            RESOLVED ({resolvedEvents.length})
          </button>
          <button
            onClick={() => setFilter('ALL')}
            className={`px-3 py-1.5 text-xs font-bold rounded-lg transition-all ${
              filter === 'ALL'
                ? 'bg-cyan-500 text-black shadow-md'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            ALL ({activeEvents.length + resolvedEvents.length})
          </button>
        </div>

        <button
          onClick={handleBulkDismissResolved}
          className="px-3 py-1.5 text-xs font-semibold text-cyan-600 dark:text-cyan-400 hover:bg-cyan-50 dark:hover:bg-cyan-950/40 border border-cyan-300 dark:border-cyan-800/60 rounded-xl transition-colors flex items-center gap-1.5"
        >
          <CheckCircle className="w-3.5 h-3.5" />
          Dismiss Resolved Alerts
        </button>
      </div>

      {/* Events List */}
      <div className="bg-white dark:bg-[#121927] rounded-2xl shadow-sm dark:shadow-xl border border-slate-200 dark:border-[#223049] overflow-hidden">
        <div className="p-4 border-b border-slate-200 dark:border-[#223049] bg-slate-50 dark:bg-[#162032] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Shield className="w-4 h-4 text-cyan-600 dark:text-cyan-400" />
            <h3 className="font-bold text-slate-900 dark:text-white text-xs uppercase tracking-wider font-mono">
              Audit Trail ({filteredEvents.length})
            </h3>
          </div>
          <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">Safe dismissal preserves historical audit records</span>
        </div>

        <div className="divide-y divide-slate-200 dark:divide-[#223049]">
          {loading ? (
            <div className="p-12 text-center text-slate-400 font-mono text-xs">Loading security telemetry...</div>
          ) : filteredEvents.length === 0 ? (
            <div className="p-16 text-center text-slate-400 space-y-3">
              <Shield className="w-12 h-12 mx-auto text-emerald-500 dark:text-emerald-400/40" />
              <p className="font-semibold text-slate-900 dark:text-white text-sm font-mono">
                {filter === 'ACTIVE' || activeEvents.length === 0 ? 'No active alerts' : 'No Security Alerts Found'}
              </p>
              <p className="text-xs text-slate-500">
                {filter === 'ACTIVE' || activeEvents.length === 0
                  ? "You're all clear. Your dismissed alerts remain available in history."
                  : `There are no ${filter.toLowerCase()} security events in this view.`}
              </p>
            </div>
          ) : (
            filteredEvents.map((event) => {
              const isDismissed = event.status === 'DISMISSED';
              return (
                <div
                  key={event.id}
                  className={`p-5 transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                    isDismissed
                      ? 'bg-slate-50/70 dark:bg-[#0E1524]/60 opacity-60'
                      : 'hover:bg-slate-50/80 dark:hover:bg-[#162032]'
                  }`}
                >
                  <div className="flex items-start gap-4 flex-1 min-w-0">
                    <div className="flex-shrink-0 mt-0.5">
                      <div className="w-10 h-10 rounded-xl bg-slate-100 dark:bg-[#1A263D] border border-slate-200 dark:border-slate-700 flex items-center justify-center shadow-inner">
                        {getSeverityIcon(event.severity)}
                      </div>
                    </div>
                    <div className="space-y-1 min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h4 className="font-bold text-slate-900 dark:text-white text-xs truncate font-mono">{event.eventType}</h4>
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider ${getSeverityBadge(event.severity)}`}>
                          {event.severity}
                        </span>
                        {event.executionStatus && (
                          <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded border uppercase ${
                            event.executionStatus === 'COMPLETED'
                              ? 'bg-cyan-100 dark:bg-cyan-950/40 text-cyan-700 dark:text-cyan-400 border-cyan-300 dark:border-cyan-800'
                              : event.executionStatus === 'STARTED'
                              ? 'bg-amber-100 dark:bg-amber-950/40 text-amber-700 dark:text-amber-400 border-amber-300 dark:border-amber-800'
                              : 'bg-rose-100 dark:bg-rose-950/40 text-rose-700 dark:text-rose-400 border-rose-300 dark:border-rose-800'
                          }`}>
                            EXEC: {event.executionStatus}
                          </span>
                        )}
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase ${
                          isDismissed
                            ? 'bg-slate-200 dark:bg-slate-800/80 text-slate-600 dark:text-slate-400 border-slate-300 dark:border-slate-700'
                            : 'bg-emerald-100 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400 border-emerald-300 dark:border-emerald-800'
                        }`}>
                          {event.status || 'ACTIVE'}
                        </span>
                      </div>
                      <p className="text-xs text-slate-700 dark:text-slate-300">{event.description}</p>
                      <p className="text-[10px] font-mono text-slate-400 dark:text-slate-500">
                        {new Date(event.timestamp).toLocaleString()}
                        {event.deviceName ? ` • Device: ${event.deviceName}` : ''}
                        {event.dismissedAt ? ` • Dismissed: ${new Date(event.dismissedAt).toLocaleString()}` : ''}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                    {isDismissed ? (
                      <button
                        onClick={() => handleRestore(event.id)}
                        className="px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:text-cyan-600 dark:hover:text-cyan-400 bg-slate-100 dark:bg-slate-800/80 hover:bg-slate-200 dark:hover:bg-slate-700/80 border border-slate-300 dark:border-slate-700 rounded-xl transition-all flex items-center gap-1.5"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        Restore
                      </button>
                    ) : (
                      <button
                        onClick={() => handleDismiss(event.id)}
                        className="px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:text-rose-600 dark:hover:text-rose-400 bg-slate-100 dark:bg-slate-800/80 hover:bg-rose-50 dark:hover:bg-rose-950/30 border border-slate-300 dark:border-slate-700 rounded-xl transition-all flex items-center gap-1.5"
                      >
                        Dismiss
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
