import React, { useEffect, useState, useMemo } from 'react';
import {
  ShieldAlert,
  AlertTriangle,
  Info,
  Shield,
  CheckCircle2,
  RotateCcw,
  Check,
  Smartphone,
  Clock,
  HelpCircle,
  Archive,
  ArrowRight,
  Download,
  Database,
  FileCode
} from 'lucide-react';
import { securityService, deviceService, auditService } from '../services/api';
import { SecurityEvent, Device } from '../types';
import { useToast } from '../context/ToastContext';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { EmptyState } from '../components/ui/EmptyState';
import { Skeleton, CardSkeleton } from '../components/ui/Skeleton';

export const SecurityEvents: React.FC = () => {
  const toast = useToast();
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string>('');
  const [events, setEvents] = useState<SecurityEvent[]>([]);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [viewMode, setViewMode] = useState<'ALERTS' | 'AUDIT_TRAIL'>('ALERTS');
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);
  const [filter, setFilter] = useState<'ALL' | 'ACTIVE' | 'RESOLVED' | 'DISMISSED'>('ACTIVE');

  // Confirmation dialogs
  const [dismissTarget, setDismissTarget] = useState<SecurityEvent | null>(null);
  const [resolveTarget, setResolveTarget] = useState<SecurityEvent | null>(null);

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

  useEffect(() => {
    const unsubAudit = auditService.subscribeToAuditLogs((data) => {
      setAuditLogs(data);
    });
    return () => unsubAudit();
  }, []);

  const exportAuditLogJson = () => {
    if (auditLogs.length === 0) return;
    const blob = new Blob([JSON.stringify(auditLogs, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const dateStr = new Date().toISOString().slice(0, 10);
    link.download = `SecureTrack_Immutable_Audit_Log_${dateStr}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleResolveAlert = async () => {
    if (!resolveTarget) return;
    setActionLoading(true);
    try {
      await securityService.resolveAlert(resolveTarget.id);
      toast.success(
        `Event "${resolveTarget.eventType}" marked as RESOLVED.`,
        'Threat Resolved'
      );
      setResolveTarget(null);
    } catch (err: any) {
      toast.error(err?.message || 'Failed to resolve alert', 'Resolution Error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDismissAlert = async () => {
    if (!dismissTarget) return;
    setActionLoading(true);
    try {
      await securityService.dismissAlert(dismissTarget.id);
      toast.info(
        `Alert "${dismissTarget.eventType}" dismissed. Historical audit record preserved.`,
        'Alert Dismissed'
      );
      setDismissTarget(null);
    } catch (err: any) {
      toast.error(err?.message || 'Failed to dismiss alert', 'Dismissal Error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleRestoreAlert = async (alertId: string) => {
    try {
      await securityService.restoreAlert(alertId);
      toast.success('Alert restored to ACTIVE queue.', 'Alert Restored');
    } catch (err: any) {
      toast.error(err?.message || 'Failed to restore alert', 'Restore Error');
    }
  };

  const handleBulkDismissResolved = async () => {
    try {
      const count = await securityService.bulkDismissResolvedAlerts();
      toast.success(`${count} resolved alerts archived to dismissed.`, 'Bulk Action Complete');
    } catch (err: any) {
      toast.error(err?.message || 'Bulk dismiss failed', 'Action Error');
    }
  };

  const getRemediationAdvice = (eventType: string): string => {
    const type = eventType.toUpperCase();
    if (type.includes('SIM')) {
      return 'Action: A SIM card change was detected. If unauthorized, verify handset ownership, contact telecom provider, and trigger Remote Lock.';
    }
    if (type.includes('UNLOCK') || type.includes('PASSWORD')) {
      return 'Action: Multiple failed unlock attempts occurred. Ensure device is in safe hands, or sound the 5s Siren Alarm to locate it.';
    }
    if (type.includes('GEOFENCE') || type.includes('PERIMETER')) {
      return 'Action: Handset exited configured safe zone. Check Live Tracking to verify current location.';
    }
    if (type.includes('SHUTDOWN') || type.includes('POWER')) {
      return 'Action: Attempted device shutdown was intercepted by Anti-Shutdown shield. Dispatch Locate Now to refresh coordinates.';
    }
    if (type.includes('AIRPLANE')) {
      return 'Action: Disconnection attempt intercepted. Verify network telemetry connectivity.';
    }
    return 'Action: Review handset location on Live Tracking map and dispatch security controls if unrecognized.';
  };

  const getSeverityIcon = (severity: string) => {
    switch (severity?.toUpperCase()) {
      case 'CRITICAL':
        return <ShieldAlert className="w-5 h-5 text-rose-600 dark:text-rose-400" />;
      case 'HIGH':
        return <AlertTriangle className="w-5 h-5 text-orange-600 dark:text-orange-400" />;
      case 'WARNING':
        return <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400" />;
      default:
        return <Info className="w-5 h-5 text-cyan-600 dark:text-cyan-400" />;
    }
  };

  const getSeverityBadge = (severity: string) => {
    switch (severity?.toUpperCase()) {
      case 'CRITICAL':
        return 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800';
      case 'HIGH':
        return 'bg-orange-100 dark:bg-orange-950/60 text-orange-800 dark:text-orange-300 border-orange-300 dark:border-orange-800';
      case 'WARNING':
        return 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800';
      default:
        return 'bg-cyan-100 dark:bg-cyan-950/60 text-cyan-800 dark:text-cyan-300 border-cyan-300 dark:border-cyan-800';
    }
  };

  const activeEvents = useMemo(
    () => events.filter((e) => (e.status || 'ACTIVE') === 'ACTIVE'),
    [events]
  );
  const resolvedEvents = useMemo(() => events.filter((e) => e.status === 'RESOLVED'), [events]);
  const dismissedEvents = useMemo(() => events.filter((e) => e.status === 'DISMISSED'), [events]);

  const filteredEvents = useMemo(() => {
    return events.filter((ev) => {
      const status = ev.status || 'ACTIVE';
      if (filter === 'ALL') return true;
      return status === filter;
    });
  }, [events, filter]);

  const selectedDevice = useMemo(
    () => devices.find((d) => d.id === selectedDeviceId),
    [devices, selectedDeviceId]
  );

  return (
    <div className="space-y-6 max-w-5xl mx-auto transition-colors duration-200 min-w-0">
      {/* Header and Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 sm:gap-4 min-w-0">
        <div className="min-w-0">
          <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
            <h1 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
              Security Center &amp; Alerts
            </h1>
            <span
              className={`text-xs font-bold px-2.5 py-0.5 rounded-full border shrink-0 ${
                activeEvents.length === 0
                  ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800'
                  : 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800'
              }`}
            >
              {activeEvents.length} Active Threats
            </span>
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Telemetry alarms, anti-theft incident logs, and owner remediation guidelines
          </p>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          <label htmlFor="device-security-select" className="sr-only">
            Select Device
          </label>
          <select
            id="device-security-select"
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

      {/* View Mode Bar: Device Threats vs Non-Repudiation Audit Trail */}
      <div className="flex flex-wrap items-center justify-between gap-2.5 sm:gap-3 border-b border-slate-200 dark:border-[#223049] pb-3">
        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={() => setViewMode('ALERTS')}
            className={`px-3.5 sm:px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 ${
              viewMode === 'ALERTS'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'bg-white dark:bg-[#121927] text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-[#223049] hover:bg-slate-50 dark:hover:bg-slate-800'
            }`}
          >
            <Shield className="w-4 h-4" />
            Device Threats ({events.length})
          </button>
          <button
            type="button"
            onClick={() => setViewMode('AUDIT_TRAIL')}
            className={`px-3.5 sm:px-4 py-2 rounded-xl text-xs font-bold transition-all flex items-center gap-2 ${
              viewMode === 'AUDIT_TRAIL'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'bg-white dark:bg-[#121927] text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-[#223049] hover:bg-slate-50 dark:hover:bg-slate-800'
            }`}
          >
            <Database className="w-4 h-4 text-cyan-500" />
            Immutable Audit Trail ({auditLogs.length})
          </button>
        </div>

        {viewMode === 'AUDIT_TRAIL' && (
          <button
            type="button"
            onClick={exportAuditLogJson}
            disabled={auditLogs.length === 0}
            className="px-3.5 py-1.5 text-xs font-semibold text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40 hover:bg-emerald-100 dark:hover:bg-emerald-900/40 border border-emerald-300 dark:border-emerald-800 rounded-xl transition-colors flex items-center gap-1.5 disabled:opacity-40 shadow-xs shrink-0"
            title="Download immutable audit trail JSON for forensic evaluation"
          >
            <Download className="w-3.5 h-3.5" />
            Export Audit JSON ({auditLogs.length})
          </button>
        )}
      </div>

      {viewMode === 'AUDIT_TRAIL' ? (
        <div className="bg-white dark:bg-[#121927] rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] overflow-hidden space-y-0">
          <div className="p-4 border-b border-slate-200 dark:border-[#223049] bg-slate-50 dark:bg-[#162032] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Database className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
              <h2 className="font-bold text-slate-900 dark:text-white text-xs uppercase tracking-wider font-mono">
                Immutable Audit Logs ({auditLogs.length})
              </h2>
            </div>
            <span className="text-[11px] text-emerald-600 dark:text-emerald-400 font-mono font-medium">
              Non-Repudiation Enforced (Append-Only)
            </span>
          </div>

          <div className="p-3 bg-indigo-50/50 dark:bg-indigo-950/20 border-b border-indigo-100 dark:border-indigo-900/30 text-[11px] text-indigo-900 dark:text-indigo-300">
            <strong>DBMS Security Policy:</strong> All command dispatches, device approvals, and revocations are immutably written to <code className="font-mono bg-white dark:bg-slate-800 px-1 py-0.5 rounded">/users/&#123;uid&#125;/audit_logs</code>. Firestore rules forbid client-side deletion or modification.
          </div>

          <div className="divide-y divide-slate-100 dark:divide-slate-800/80">
            {auditLogs.length === 0 ? (
              <div className="p-16 text-center space-y-3">
                <Database className="w-12 h-12 mx-auto text-slate-400 opacity-60" />
                <h3 className="font-bold text-slate-900 dark:text-white text-base">
                  No Audit Logs Recorded Yet
                </h3>
                <p className="text-xs text-slate-500 max-w-sm mx-auto leading-relaxed">
                  Dispatch a remote command or pair a device to generate your first immutable audit entry.
                </p>
              </div>
            ) : (
              auditLogs.map((log) => (
                <div key={log.id} className="p-4 hover:bg-slate-50/80 dark:hover:bg-[#162032] transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-mono font-bold px-2 py-0.5 rounded text-[10px] bg-slate-100 dark:bg-slate-800 text-indigo-600 dark:text-cyan-400 border border-slate-300 dark:border-slate-700">
                        {log.action}
                      </span>
                      <span className="font-mono text-slate-700 dark:text-slate-300 text-[11px]">
                        Target: {log.targetDeviceId || 'N/A'}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-500 font-mono">
                      Source: {log.initiatedFrom || 'WEB_CONSOLE'} {log.requestId ? `• Req: ${log.requestId}` : ''}
                    </p>
                  </div>
                  <div className="text-right">
                    <span className="text-[11px] text-slate-400 font-mono flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {log.timestamp ? new Date(log.timestamp).toLocaleString() : 'Just now'}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      ) : (
        <>
          {/* Filter Tabs & Bulk Actions */}
          <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1.5 bg-white dark:bg-[#121927] border border-slate-200 dark:border-[#223049] p-1 rounded-2xl shadow-xs">
          <button
            type="button"
            onClick={() => setFilter('ACTIVE')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all ${
              filter === 'ACTIVE'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            ACTIVE ({activeEvents.length})
          </button>
          <button
            type="button"
            onClick={() => setFilter('RESOLVED')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all ${
              filter === 'RESOLVED'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            RESOLVED ({resolvedEvents.length})
          </button>
          <button
            type="button"
            onClick={() => setFilter('DISMISSED')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all ${
              filter === 'DISMISSED'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            DISMISSED ({dismissedEvents.length})
          </button>
          <button
            type="button"
            onClick={() => setFilter('ALL')}
            className={`px-3.5 py-1.5 text-xs font-bold rounded-xl transition-all ${
              filter === 'ALL'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white'
            }`}
          >
            ALL ({events.length})
          </button>
        </div>

        {resolvedEvents.length > 0 && (
          <button
            type="button"
            onClick={handleBulkDismissResolved}
            className="px-3.5 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-xl transition-colors flex items-center gap-1.5"
          >
            <Archive className="w-3.5 h-3.5 text-slate-500" />
            Archive Resolved Alerts
          </button>
        )}
      </div>

      {/* Events List Container */}
      <div className="bg-white dark:bg-[#121927] rounded-3xl shadow-sm border border-slate-200 dark:border-[#223049] overflow-hidden">
        <div className="p-4 border-b border-slate-200 dark:border-[#223049] bg-slate-50 dark:bg-[#162032] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Shield className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
            <h2 className="font-bold text-slate-900 dark:text-white text-xs uppercase tracking-wider font-mono">
              Audit Trail ({filteredEvents.length})
            </h2>
          </div>
          <span className="text-[11px] text-slate-500">
            Immutable log stored with non-repudiation
          </span>
        </div>

        <div className="divide-y divide-slate-100 dark:divide-slate-800/80">
          {loading ? (
            <div className="p-8 space-y-4">
              <Skeleton className="w-full h-16" />
              <Skeleton className="w-full h-16" />
              <Skeleton className="w-full h-16" />
            </div>
          ) : filteredEvents.length === 0 ? (
            <div className="p-16 text-center space-y-3">
              <CheckCircle2 className="w-12 h-12 mx-auto text-emerald-500 opacity-80" />
              <h3 className="font-bold text-slate-900 dark:text-white text-base">
                {filter === 'ACTIVE' ? 'All Clear — No Active Threats' : 'No Security Events Found'}
              </h3>
              <p className="text-xs text-slate-500 max-w-sm mx-auto leading-relaxed">
                {filter === 'ACTIVE'
                  ? 'All devices are operating safely with zero active alerts. Dismissed or resolved alerts remain accessible in historical tabs.'
                  : `There are no ${filter.toLowerCase()} security events recorded for this device.`}
              </p>
            </div>
          ) : (
            filteredEvents.map((event) => {
              const isDismissed = event.status === 'DISMISSED';
              const isResolved = event.status === 'RESOLVED';
              const remediation = getRemediationAdvice(event.eventType);

              return (
                <div
                  key={event.id}
                  className={`p-5 transition-colors flex flex-col md:flex-row md:items-start justify-between gap-4 ${
                    isDismissed
                      ? 'bg-slate-50/50 dark:bg-[#0E1524]/40 opacity-70'
                      : isResolved
                      ? 'bg-emerald-50/20 dark:bg-emerald-950/10'
                      : 'hover:bg-slate-50/80 dark:hover:bg-[#162032]'
                  }`}
                >
                  {/* Left Column: Icon & Event Details */}
                  <div className="flex items-start gap-4 flex-1 min-w-0">
                    <div className="mt-1 shrink-0">
                      <div className="w-10 h-10 rounded-2xl bg-slate-100 dark:bg-[#1A263D] border border-slate-200 dark:border-slate-700 flex items-center justify-center">
                        {getSeverityIcon(event.severity)}
                      </div>
                    </div>

                    <div className="space-y-2 flex-1 min-w-0">
                      {/* Event Type & Badges */}
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-bold text-slate-900 dark:text-white text-sm font-mono">
                          {event.eventType}
                        </h3>
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase tracking-wider ${getSeverityBadge(
                            event.severity
                          )}`}
                        >
                          {event.severity}
                        </span>
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded border uppercase ${
                            isResolved
                              ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800'
                              : isDismissed
                              ? 'bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-300 dark:border-slate-700'
                              : 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-800'
                          }`}
                        >
                          {event.status || 'ACTIVE'}
                        </span>
                      </div>

                      {/* Description */}
                      <p className="text-xs text-slate-700 dark:text-slate-200 leading-relaxed font-medium">
                        {event.description}
                      </p>

                      {/* Remediation Advice Box */}
                      <div className="p-2.5 rounded-xl bg-slate-100 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 text-[11px] text-slate-600 dark:text-slate-300">
                        {remediation}
                      </div>

                      {/* Timestamp & Metadata */}
                      <div className="flex items-center gap-3 text-[10px] text-slate-400 font-mono pt-1">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {new Date(event.timestamp).toLocaleString()}
                        </span>
                        {event.deviceName && (
                          <span className="flex items-center gap-1">
                            <Smartphone className="w-3 h-3" />
                            {event.deviceName}
                          </span>
                        )}
                        {event.resolvedAt && (
                          <span className="text-emerald-600 dark:text-emerald-400">
                            • Resolved {new Date(event.resolvedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right Column: Actions */}
                  <div className="flex items-center gap-2 shrink-0 self-end md:self-center pt-2 md:pt-0">
                    {!isResolved && !isDismissed && (
                      <button
                        type="button"
                        onClick={() => setResolveTarget(event)}
                        disabled={actionLoading}
                        className="px-3.5 py-2 text-xs font-semibold text-emerald-700 dark:text-emerald-300 hover:bg-emerald-50 dark:hover:bg-emerald-950/40 bg-emerald-50/50 dark:bg-emerald-950/20 border border-emerald-300 dark:border-emerald-700 rounded-xl transition-colors flex items-center gap-1.5"
                      >
                        <Check className="w-3.5 h-3.5" />
                        Resolve Threat
                      </button>
                    )}

                    {!isDismissed ? (
                      <button
                        type="button"
                        onClick={() => setDismissTarget(event)}
                        disabled={actionLoading}
                        className="px-3.5 py-2 text-xs font-semibold text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl transition-colors flex items-center gap-1"
                      >
                        <Archive className="w-3.5 h-3.5 text-slate-400" />
                        Dismiss
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleRestoreAlert(event.id)}
                        disabled={actionLoading}
                        className="px-3.5 py-2 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl transition-colors flex items-center gap-1.5"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        Restore to Active
                      </button>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
      </>
      )}

      {/* Confirmation Dialog for Resolving Alert */}
      <ConfirmDialog
        isOpen={!!resolveTarget}
        title="Resolve Security Threat?"
        description={`Mark "${resolveTarget?.eventType}" as resolved? This confirms that appropriate security measures have been taken on the device.`}
        confirmLabel="Confirm Resolved"
        variant="primary"
        isLoading={actionLoading}
        onConfirm={handleResolveAlert}
        onCancel={() => setResolveTarget(null)}
      />

      {/* Confirmation Dialog for Dismissing Alert */}
      <ConfirmDialog
        isOpen={!!dismissTarget}
        title="Dismiss Alert?"
        description={`Dismiss "${dismissTarget?.eventType}" from the active queue? The event remains safely preserved in your historical audit logs.`}
        confirmLabel="Dismiss Alert"
        variant="warning"
        isLoading={actionLoading}
        onConfirm={handleDismissAlert}
        onCancel={() => setDismissTarget(null)}
      />
    </div>
  );
};
