import React, { useEffect, useState } from 'react';
import { Volume2, ShieldAlert, ArrowRight, X, CheckCircle2, Clock } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { securityService } from '../services/api';
import { SecurityEvent } from '../types';

export const RealtimeAlertBanner: React.FC = () => {
  const [activeAlarmEvent, setActiveAlarmEvent] = useState<SecurityEvent | null>(null);
  const [dismissedLocally, setDismissedLocally] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const unsub = securityService.subscribeToSecurityEvents((events) => {
      // Find latest un-dismissed SECURITY_ALARM_TRIGGERED event
      const alarmEvent = events.find(
        (e) => e.eventType === 'SECURITY_ALARM_TRIGGERED' && e.status !== 'DISMISSED'
      );
      setActiveAlarmEvent(alarmEvent || null);
    });
    return () => unsub();
  }, []);

  if (!activeAlarmEvent || activeAlarmEvent.id === dismissedLocally) {
    return null;
  }

  const isCompleted = activeAlarmEvent.executionStatus === 'COMPLETED' || activeAlarmEvent.status === 'RESOLVED';
  const isStarted = activeAlarmEvent.executionStatus === 'STARTED' || !activeAlarmEvent.executionStatus;

  const handleDismissAlert = async () => {
    try {
      await securityService.dismissAlert(activeAlarmEvent.id);
      setDismissedLocally(activeAlarmEvent.id);
    } catch (e) {
      console.error('Failed to dismiss alarm alert', e);
      setDismissedLocally(activeAlarmEvent.id);
    }
  };

  return (
    <div className="w-full bg-gradient-to-r from-amber-500/15 via-rose-500/15 to-indigo-500/15 dark:from-amber-950/40 dark:via-rose-950/40 dark:to-indigo-950/30 border-b border-amber-300 dark:border-amber-700/60 p-3.5 transition-all animate-fade-in shadow-md">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-9 h-9 rounded-xl bg-amber-100 dark:bg-amber-950/70 border border-amber-300 dark:border-amber-700 text-amber-700 dark:text-amber-400 flex items-center justify-center shrink-0 shadow-[0_0_12px_rgba(245,158,11,0.25)]">
            <Volume2 className="w-5 h-5 animate-pulse" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-mono text-xs font-bold text-amber-800 dark:text-amber-300 uppercase tracking-wider flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-rose-500 animate-ping inline-block" />
                Emergency Security Alarm Triggered
              </span>
              <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded-full border ${
                isCompleted
                  ? 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-700'
                  : isStarted
                  ? 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-700'
                  : 'bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300 border-rose-300 dark:border-rose-700'
              }`}>
                {isCompleted ? 'COMPLETED (5s Cycle)' : isStarted ? 'ACTIVE / SOUNDING' : activeAlarmEvent.executionStatus}
              </span>
            </div>
            <p className="text-xs text-slate-700 dark:text-slate-300 mt-0.5 truncate">
              <strong>Device:</strong> {activeAlarmEvent.deviceName || 'Protected Phone'} •{' '}
              <span className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
                {new Date(activeAlarmEvent.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
              </span>
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2.5 shrink-0 self-end sm:self-center">
          <button
            onClick={() => navigate('/security')}
            className="flex items-center gap-1 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold transition-all shadow-sm"
          >
            <span>Review Event</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleDismissAlert}
            className="px-3 py-1.5 bg-white dark:bg-[#162032] hover:bg-slate-100 dark:hover:bg-[#1e2b42] text-slate-700 dark:text-slate-300 border border-slate-300 dark:border-slate-700 rounded-xl text-xs font-semibold transition-all shadow-sm"
            title="Acknowledge and dismiss security alarm"
          >
            Dismiss
          </button>
          <button
            onClick={() => setDismissedLocally(activeAlarmEvent.id)}
            className="p-1.5 text-slate-400 hover:text-slate-600 dark:hover:text-white rounded-lg transition-colors"
            title="Hide banner"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
