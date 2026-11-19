import React, { useState, useEffect } from 'react';
import { deviceService } from '../services/api';
import { Device } from '../types';
import { EmailAuthProvider, reauthenticateWithCredential } from 'firebase/auth';
import { doc, setDoc, serverTimestamp } from 'firebase/firestore';
import { auth, db } from '../services/firebase';
import { ShieldCheck, Lock, ExternalLink, AlertCircle, Eye, EyeOff } from 'lucide-react';

export const LostPhoneRecovery: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [reauthPassword, setReauthPassword] = useState('');
  const [showReauthPassword, setShowReauthPassword] = useState(false);
  const [revealedDeviceId, setRevealedDeviceId] = useState<string | null>(null);
  const [showReauthModal, setShowReauthModal] = useState(false);
  const [targetDeviceId, setTargetDeviceId] = useState<string | null>(null);
  const [authError, setAuthError] = useState('');
  const [recoveryInfo, setRecoveryInfo] = useState<{ imei1?: string; imei2?: string } | null>(null);

  // Optional manual registration form state
  const [editingDeviceId, setEditingDeviceId] = useState<string | null>(null);
  const [newImei1, setNewImei1] = useState('');
  const [newImei2, setNewImei2] = useState('');
  const [consentGiven, setConsentGiven] = useState(false);
  const [imeiError, setImeiError] = useState('');
  const [saveSuccess, setSaveSuccess] = useState('');

  useEffect(() => {
    fetchDevices();
    return () => {
      setRevealedDeviceId(null);
      setRecoveryInfo(null);
    };
  }, []);

  const fetchDevices = async () => {
    try {
      const data = await deviceService.getDevices();
      setDevices(data);
    } catch (error) {
      console.error('Failed to load devices', error);
    } finally {
      setLoading(false);
    }
  };

  const validateLuhn = (imei: string): boolean => {
    if (!/^\d{15}$/.test(imei)) return false;
    let sum = 0;
    for (let i = 14; i >= 0; i--) {
      let digit = parseInt(imei.charAt(i), 10);
      if ((14 - i) % 2 !== 0) {
        digit *= 2;
        if (digit > 9) digit -= 9;
      }
      sum += digit;
    }
    return sum % 10 === 0;
  };

  const handleRevealRequest = (deviceId: string) => {
    setTargetDeviceId(deviceId);
    setShowReauthModal(true);
    setAuthError('');
  };

  const handleReauthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    if (!reauthPassword.trim()) {
      setAuthError('Please enter your account password.');
      return;
    }

    try {
      const user = auth.currentUser;
      if (!user || !user.email) {
        setAuthError('User not properly authenticated.');
        return;
      }

      // Genuine Firebase Authentication re-authentication check
      const credential = EmailAuthProvider.credential(user.email, reauthPassword);
      await reauthenticateWithCredential(user, credential);

      if (targetDeviceId) {
        const info = await deviceService.getRecoveryInfo(targetDeviceId);
        setRecoveryInfo(info);
        setRevealedDeviceId(targetDeviceId);
      }

      setShowReauthModal(false);
      setReauthPassword('');
      setTargetDeviceId(null);
    } catch (error: any) {
      console.error('Re-authentication failed', error);
      setAuthError('Invalid password. Identity verification failed.');
    }
  };

  const handleSaveImei = async (e: React.FormEvent) => {
    e.preventDefault();
    setImeiError('');
    setSaveSuccess('');

    if (!editingDeviceId) return;
    if (!consentGiven) {
      setImeiError('Explicit consent is required to securely store recovery identifiers.');
      return;
    }

    if (newImei1 && !validateLuhn(newImei1)) {
      setImeiError('IMEI 1 is invalid. Must be exactly 15 digits and pass Luhn checksum verification.');
      return;
    }

    if (newImei2 && !validateLuhn(newImei2)) {
      setImeiError('IMEI 2 is invalid. Must be exactly 15 digits and pass Luhn checksum verification.');
      return;
    }

    try {
      const user = auth.currentUser;
      if (!user) throw new Error('Not authenticated');

      await setDoc(doc(db, 'users', user.uid, 'private_recovery', editingDeviceId), {
        imei1: newImei1 || null,
        imei2: newImei2 || null,
        consentGiven: true,
        updatedAt: serverTimestamp()
      }, { merge: true });

      setSaveSuccess('IMEI data saved securely in private recovery vault.');
      setEditingDeviceId(null);
      setNewImei1('');
      setNewImei2('');
      setConsentGiven(false);
      fetchDevices();
    } catch (err: any) {
      setImeiError(err.message || 'Failed to save IMEI information.');
    }
  };

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div>
        <h1 className="text-3xl font-bold text-[#152C4F]">Lost Phone Recovery & IMEI Vault</h1>
        <p className="text-sm text-[#64748B] mt-1">Official emergency procedures & protected device identification</p>
      </div>

      {saveSuccess && (
        <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl text-sm">
          {saveSuccess}
        </div>
      )}

      {/* Official Recovery Steps */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-[#CBD5E1] space-y-4">
        <h2 className="text-xl font-semibold text-[#152C4F] flex items-center gap-2">
          <ShieldCheck className="w-5 h-5 text-[#255DA8]" />
          Report Stolen Phone (CEIR India Protocol)
        </h2>
        <p className="text-sm text-[#64748B]">
          If your device is permanently stolen or lost, government and law enforcement portals can blacklist its IMEI across all telecom carriers.
        </p>
        <ol className="list-decimal ml-6 space-y-2 text-sm text-[#334155]">
          <li>File a Police Complaint (FIR) immediately and retain the digital copy.</li>
          <li>Procure a replacement SIM card for your mobile number from your telecom provider.</li>
          <li>Reveal your registered IMEI numbers below (requires password re-authentication).</li>
          <li>
            Submit the official block request at{' '}
            <a
              href="https://www.ceir.gov.in/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-[#255DA8] hover:underline font-semibold inline-flex items-center gap-1"
            >
              CEIR Official Portal <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </li>
        </ol>
      </div>

      {/* Device Finding Backup */}
      <div className="bg-white p-6 rounded-2xl shadow-sm border border-[#CBD5E1] space-y-3">
        <h2 className="text-xl font-semibold text-[#152C4F]">Google Find Hub (Backup Location)</h2>
        <p className="text-sm text-[#64748B]">
          If the phone is still powered on and linked to your Google Account, you can utilize Google Find Hub to ring, lock, or secure it as an independent recovery fallback.
        </p>
        <a
          href="https://android.com/find"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 bg-[#152C4F] text-white px-4 py-2.5 rounded-xl font-semibold hover:bg-[#1e3a66] transition-colors text-sm"
        >
          Open Google Find Hub <ExternalLink className="w-4 h-4" />
        </a>
      </div>

      {/* Registered Devices List */}
      <h2 className="text-2xl font-bold text-[#152C4F] pt-4">Your Enrolled Devices</h2>
      {loading ? (
        <p className="text-[#64748B] text-sm">Loading devices...</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {devices.map((device) => (
            <div key={device.id} className="bg-white p-6 rounded-2xl shadow-sm border border-[#CBD5E1] space-y-4">
              <div>
                <h3 className="text-lg font-bold text-[#152C4F]">{device.deviceName}</h3>
                <p className="text-xs text-[#64748B]">{device.model} • Android {device.androidVersion}</p>
              </div>

              {revealedDeviceId === device.id ? (
                <div className="bg-blue-50/60 p-4 rounded-xl border border-blue-200 space-y-2">
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-[#334155]">IMEI 1:</span>
                    <span className="font-mono font-bold text-[#152C4F] select-all bg-white px-2 py-0.5 rounded border border-blue-100">
                      {recoveryInfo?.imei1 || 'Not registered'}
                    </span>
                  </div>
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-[#334155]">IMEI 2:</span>
                    <span className="font-mono font-bold text-[#152C4F] select-all bg-white px-2 py-0.5 rounded border border-blue-100">
                      {recoveryInfo?.imei2 || 'Not registered'}
                    </span>
                  </div>
                  <button
                    onClick={() => {
                      setRevealedDeviceId(null);
                      setRecoveryInfo(null);
                    }}
                    className="mt-2 text-xs text-[#255DA8] hover:underline font-semibold"
                  >
                    Lock Sensitive View
                  </button>
                </div>
              ) : (
                <div className="space-y-2">
                  <button
                    onClick={() => handleRevealRequest(device.id)}
                    className="w-full flex items-center justify-center gap-2 bg-[#F0F4FA] text-[#152C4F] font-semibold py-2.5 rounded-xl hover:bg-[#e2eaf5] transition-colors text-sm border border-[#CBD5E1]"
                  >
                    <Lock className="w-4 h-4 text-[#255DA8]" />
                    Verify Password to Reveal IMEI
                  </button>
                </div>
              )}
            </div>
          ))}
          {devices.length === 0 && (
            <p className="text-[#64748B] text-sm col-span-2">No registered devices found.</p>
          )}
        </div>
      )}

      {/* Re-Authentication Modal */}
      {showReauthModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white p-6 rounded-2xl shadow-2xl max-w-md w-full border border-[#CBD5E1] space-y-4">
            <div>
              <h3 className="text-xl font-bold text-[#152C4F]">Security Re-Authentication</h3>
              <p className="text-xs text-[#64748B] mt-1">
                IMEI numbers are confidential telecom identifiers. Enter your password to authenticate access.
              </p>
            </div>

            {authError && (
              <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-xs rounded-xl flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                {authError}
              </div>
            )}

            <form onSubmit={handleReauthSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#334155] mb-1">Account Password</label>
                <div className="relative">
                  <input
                    type={showReauthPassword ? 'text' : 'password'}
                    value={reauthPassword}
                    onChange={(e) => setReauthPassword(e.target.value)}
                    placeholder="Enter current password"
                    className="w-full pl-4 pr-11 py-2.5 rounded-xl border border-[#CBD5E1] bg-white text-[#152C4F] placeholder-[#64748B] focus:outline-none focus:border-[#315C97] focus:ring-2 focus:ring-[#315C97]/20 text-sm"
                    autoFocus
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowReauthPassword(!showReauthPassword)}
                    aria-label={showReauthPassword ? 'Hide password' : 'Show password'}
                    className="absolute right-3 top-2.5 text-[#64748B] hover:text-[#152C4F] p-1 rounded-md transition-colors focus:outline-none focus:ring-1 focus:ring-[#315C97]"
                  >
                    {showReauthPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => { setShowReauthModal(false); setReauthPassword(''); setAuthError(''); setShowReauthPassword(false); }}
                  className="px-4 py-2 text-sm text-[#64748B] hover:text-[#152C4F] rounded-xl"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-[#152C4F] text-white rounded-xl text-sm font-semibold hover:bg-[#1e3a66] transition-colors"
                >
                  Verify & Reveal
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
