import React from 'react';
import {
  Sun,
  Moon,
  Laptop,
  Shield,
  User,
  CheckCircle2,
  Lock,
  Radio,
  Info,
  ExternalLink,
  GraduationCap,
  Github,
  KeyRound,
  Eye,
  Sliders,
  HelpCircle,
  FileCheck
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

export const Settings: React.FC = () => {
  const { user } = useAuth();
  const { theme, setTheme, resolvedTheme } = useTheme();

  return (
    <div className="space-y-8 max-w-4xl mx-auto transition-colors duration-200">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          System &amp; Account Settings
        </h1>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
          Configure console appearance, account profile, privacy consent, and protection permissions
        </p>
      </div>

      {/* 1. ACCOUNT AND PROFILE SECTION */}
      <section
        aria-labelledby="section-account-title"
        className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4"
      >
        <div className="flex items-center gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
          <User className="w-5 h-5 text-indigo-600 dark:text-cyan-400" />
          <div>
            <h2 id="section-account-title" className="text-base font-bold text-slate-900 dark:text-white">
              Account &amp; Operator Profile
            </h2>
            <p className="text-xs text-slate-500">Verified credentials and fleet administrator identity</p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs pt-1">
          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <span className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400 block">
              Full Name
            </span>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm">
              {user?.fullName || 'SecureTrack Operator'}
            </p>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <span className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400 block">
              Verified Email
            </span>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm font-mono">
              {user?.email || 'N/A'}
            </p>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <span className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400 block">
              Emergency Phone Number
            </span>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm font-mono">
              {user?.phoneNumber || 'Configured via handset settings'}
            </p>
          </div>

          <div className="p-3.5 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <span className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400 block">
              Authentication Provider
            </span>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm flex items-center gap-1.5 font-mono">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              Firebase Auth (Zero-Trust Session)
            </p>
          </div>
        </div>
      </section>

      {/* 2. APPEARANCE SETTINGS SECTION */}
      <section
        aria-labelledby="section-appearance-title"
        className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4"
      >
        <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <Sun className="w-5 h-5 text-amber-500" />
            <div>
              <h2 id="section-appearance-title" className="text-base font-bold text-slate-900 dark:text-white">
                Appearance &amp; Theme
              </h2>
              <p className="text-xs text-slate-500">Select dashboard color theme and contrast preferences</p>
            </div>
          </div>
          <span className="text-xs font-mono px-3 py-1 rounded-full font-bold border bg-slate-100 text-slate-700 border-slate-300 dark:bg-cyan-950/50 dark:text-cyan-400 dark:border-cyan-800">
            Active: {theme.toUpperCase()} {theme === 'system' ? `(${resolvedTheme})` : ''}
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
          {/* Light Mode */}
          <button
            type="button"
            onClick={() => setTheme('light')}
            className={`p-4 rounded-2xl border text-left transition-all flex flex-col justify-between ${
              theme === 'light'
                ? 'border-indigo-600 dark:border-cyan-400 ring-2 ring-indigo-500/20 bg-indigo-50/40 dark:bg-slate-800/50 shadow-xs'
                : 'border-slate-200 dark:border-[#223049] hover:border-slate-300 bg-slate-50/50 dark:bg-[#151E2E]/60'
            }`}
          >
            <div className="space-y-3">
              <div className="w-full h-20 rounded-xl bg-[#F8FAFC] border border-slate-200 p-2.5 flex flex-col justify-between">
                <div className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded bg-indigo-600" />
                  <div className="w-12 h-1.5 rounded bg-slate-400" />
                </div>
                <div className="w-full h-1.5 rounded bg-slate-200" />
              </div>
              <div className="flex items-center justify-between">
                <span className="font-bold text-xs text-slate-900 dark:text-white flex items-center gap-1.5">
                  <Sun className="w-3.5 h-3.5 text-amber-500" /> Light
                </span>
                {theme === 'light' && <CheckCircle2 className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />}
              </div>
            </div>
          </button>

          {/* Dark Mode */}
          <button
            type="button"
            onClick={() => setTheme('dark')}
            className={`p-4 rounded-2xl border text-left transition-all flex flex-col justify-between ${
              theme === 'dark'
                ? 'border-indigo-600 dark:border-cyan-400 ring-2 ring-cyan-400/20 bg-indigo-50/40 dark:bg-slate-800/50 shadow-xs'
                : 'border-slate-200 dark:border-[#223049] hover:border-slate-300 bg-slate-50/50 dark:bg-[#151E2E]/60'
            }`}
          >
            <div className="space-y-3">
              <div className="w-full h-20 rounded-xl bg-[#0A0E17] border border-[#223049] p-2.5 flex flex-col justify-between">
                <div className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded bg-cyan-400" />
                  <div className="w-12 h-1.5 rounded bg-slate-600" />
                </div>
                <div className="w-full h-1.5 rounded bg-slate-800" />
              </div>
              <div className="flex items-center justify-between">
                <span className="font-bold text-xs text-slate-900 dark:text-white flex items-center gap-1.5">
                  <Moon className="w-3.5 h-3.5 text-cyan-400" /> Dark
                </span>
                {theme === 'dark' && <CheckCircle2 className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />}
              </div>
            </div>
          </button>

          {/* System Mode */}
          <button
            type="button"
            onClick={() => setTheme('system')}
            className={`p-4 rounded-2xl border text-left transition-all flex flex-col justify-between ${
              theme === 'system'
                ? 'border-indigo-600 dark:border-cyan-400 ring-2 ring-indigo-500/20 bg-indigo-50/40 dark:bg-slate-800/50 shadow-xs'
                : 'border-slate-200 dark:border-[#223049] hover:border-slate-300 bg-slate-50/50 dark:bg-[#151E2E]/60'
            }`}
          >
            <div className="space-y-3">
              <div className="w-full h-20 rounded-xl overflow-hidden border border-slate-300 dark:border-[#223049] flex">
                <div className="w-1/2 bg-[#F8FAFC] p-2 border-r border-slate-200" />
                <div className="w-1/2 bg-[#0A0E17] p-2" />
              </div>
              <div className="flex items-center justify-between">
                <span className="font-bold text-xs text-slate-900 dark:text-white flex items-center gap-1.5">
                  <Laptop className="w-3.5 h-3.5 text-indigo-500 dark:text-cyan-400" /> System
                </span>
                {theme === 'system' && <CheckCircle2 className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />}
              </div>
            </div>
          </button>
        </div>
      </section>

      {/* 3. PRIVACY AND CONSENT SECTION */}
      <section
        aria-labelledby="section-privacy-title"
        className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4"
      >
        <div className="flex items-center gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
          <FileCheck className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
          <div>
            <h2 id="section-privacy-title" className="text-base font-bold text-slate-900 dark:text-white">
              Privacy, Consent &amp; Anti-Stalking Safeguards
            </h2>
            <p className="text-xs text-slate-500">Transparent user consent policies and rights</p>
          </div>
        </div>

        <div className="space-y-3 text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-2">
            <h3 className="font-bold text-slate-900 dark:text-white text-xs">
              Continuous Notification Transparency
            </h3>
            <p>
              SecureTrack mandates an active foreground notification on all enrolled Android handsets whenever tracking or defense services are active. Secret, covert, or hidden surveillance is strictly prohibited.
            </p>
          </div>

          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-2">
            <h3 className="font-bold text-slate-900 dark:text-white text-xs">
              How the Device User Can Stop Tracking
            </h3>
            <p>
              The device holder has unilateral control at all times:
            </p>
            <ul className="list-disc ml-5 space-y-1 text-slate-600 dark:text-slate-300">
              <li>Turn off Location Services in Android Settings at any time.</li>
              <li>Revoke Background Location or Device Admin permissions in app settings.</li>
              <li>Uninstall the SecureTrack application or tap "Unpair Handset" within the app.</li>
            </ul>
          </div>
        </div>
      </section>

      {/* 4. DEVICE PERMISSIONS AND PROTECTION GUIDE */}
      <section
        aria-labelledby="section-permissions-title"
        className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4"
      >
        <div className="flex items-center gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
          <Sliders className="w-5 h-5 text-indigo-600 dark:text-cyan-400" />
          <div>
            <h2 id="section-permissions-title" className="text-base font-bold text-slate-900 dark:text-white">
              Device Permissions &amp; Protection Guide
            </h2>
            <p className="text-xs text-slate-500">Prerequisites for maximum handset defense and recovery</p>
          </div>
        </div>

        <div className="space-y-2.5 text-xs text-slate-600 dark:text-slate-300">
          <div className="flex items-start justify-between p-3 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <div>
              <strong className="text-slate-900 dark:text-white block">Background Location (Always Allow)</strong>
              <span className="text-[11px] text-slate-500">Enables periodic GPS location tracking while the phone screen is off.</span>
            </div>
            <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border border-emerald-300 dark:border-emerald-800 shrink-0">
              REQUIRED
            </span>
          </div>

          <div className="flex items-start justify-between p-3 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <div>
              <strong className="text-slate-900 dark:text-white block">Device Administration Policy</strong>
              <span className="text-[11px] text-slate-500">Allows authorized remote screen lock when lost or stolen.</span>
            </div>
            <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-indigo-100 dark:bg-indigo-950/60 text-indigo-800 dark:text-indigo-300 border border-indigo-300 dark:border-indigo-800 shrink-0">
              RECOMMENDED
            </span>
          </div>

          <div className="flex items-start justify-between p-3 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <div>
              <strong className="text-slate-900 dark:text-white block">Battery Optimization Exclusion</strong>
              <span className="text-[11px] text-slate-500">Prevents Android Doze mode from killing the background tracking service.</span>
            </div>
            <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-indigo-100 dark:bg-indigo-950/60 text-indigo-800 dark:text-indigo-300 border border-indigo-300 dark:border-indigo-800 shrink-0">
              RECOMMENDED
            </span>
          </div>
        </div>
      </section>

      {/* 5. SECURITY & CRYPTOGRAPHIC INFORMATION */}
      <section
        aria-labelledby="section-security-title"
        className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4"
      >
        <div className="flex items-center gap-2 border-b border-slate-100 dark:border-slate-800 pb-3">
          <Shield className="w-5 h-5 text-indigo-600 dark:text-cyan-400" />
          <div>
            <h2 id="section-security-title" className="text-base font-bold text-slate-900 dark:text-white">
              Security Specifications &amp; Architecture
            </h2>
            <p className="text-xs text-slate-500">Zero-trust cryptographic protocols and audit controls</p>
          </div>
        </div>

        <div className="space-y-3 text-xs text-slate-600 dark:text-slate-300">
          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800">
            <span className="font-medium">Device Pairing Cryptography</span>
            <span className="font-mono text-emerald-600 dark:text-emerald-400 font-semibold">
              256-Bit SHA-256 Tokens (Single-Use, 10m TTL)
            </span>
          </div>
          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800">
            <span className="font-medium">Cloud Database &amp; Security Rules</span>
            <span className="font-mono text-slate-800 dark:text-slate-200 font-semibold">
              Google Cloud Firestore (Strict Path Isolation)
            </span>
          </div>
          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800">
            <span className="font-medium">Device Account Quota</span>
            <span className="font-mono text-slate-800 dark:text-slate-200 font-semibold">
              Maximum 3 Active Devices per Account
            </span>
          </div>
          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800">
            <span className="font-medium">Command Expiration Limit</span>
            <span className="font-mono text-amber-600 dark:text-amber-400 font-semibold">
              15-Minute Queue Expiry Guard
            </span>
          </div>
          <div className="flex items-center justify-between py-2">
            <span className="font-medium">Command Rate-Limiting</span>
            <span className="font-mono text-slate-800 dark:text-slate-200 font-semibold">
              Alarm: 2m Cooldown • Lock: 1m Cooldown • Locate: 5 / 10m
            </span>
          </div>
        </div>
      </section>

      {/* 6. HELP AND PROJECT INFORMATION */}
      <section
        aria-labelledby="section-help-title"
        className="bg-white dark:bg-[#121927] p-6 rounded-3xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4"
      >
        <div className="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
          <div className="flex items-center gap-2">
            <Info className="w-5 h-5 text-indigo-600 dark:text-cyan-400" />
            <div>
              <h2 id="section-help-title" className="text-base font-bold text-slate-900 dark:text-white">
                About SecureTrack &amp; Project Information
              </h2>
              <p className="text-xs text-slate-500">Academic origins, credits, and open-source licensing</p>
            </div>
          </div>
          <span className="text-xs font-mono px-3 py-1 rounded-full font-bold border bg-emerald-50 text-emerald-700 border-emerald-300 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800">
            Apache 2.0 Open Source
          </span>
        </div>

        <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
          <strong>SecureTrack</strong> is an enterprise-grade mobile telemetry, loss prevention, and anti-theft defense platform engineered for Android devices, synchronized with a real-time web operations center. Built as an advanced project in <strong>Database Management Systems (DBMS) and Mobile Security</strong>.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs pt-1">
          {/* Project Lead */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-1">
            <div className="flex items-center gap-1.5 text-indigo-600 dark:text-cyan-400 font-mono text-[11px] font-semibold">
              <User className="w-3.5 h-3.5" />
              Project Lead, Owner &amp; Author
            </div>
            <p className="font-bold text-slate-900 dark:text-white text-sm">Dhanush S</p>
            <p className="text-[11px] text-slate-500">
              System Architecture, Cloud Firestore Integration &amp; Release Management
            </p>
          </div>

          {/* Academic Guide */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-1">
            <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-mono text-[11px] font-semibold">
              <GraduationCap className="w-3.5 h-3.5" />
              Academic &amp; DBMS Guide
            </div>
            <p className="font-bold text-slate-900 dark:text-white text-sm">Mohankumar H M</p>
            <p className="text-[11px] text-slate-500">
              Database Architecture, Relational Modeling &amp; Academic Review
            </p>
          </div>

          {/* Core Engineering Contributors */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-1 md:col-span-2">
            <div className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400 font-semibold">
              Core Engineering Team &amp; Contributors
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 pt-1 font-mono text-[11px]">
              <div className="text-slate-700 dark:text-slate-300">• Deeksith H G <span className="text-slate-400 text-[10px]">(Security)</span></div>
              <div className="text-slate-700 dark:text-slate-300">• Deeksith K <span className="text-slate-400 text-[10px]">(Android Client)</span></div>
              <div className="text-slate-700 dark:text-slate-300">• Puneethkumar N E <span className="text-slate-400 text-[10px]">(Database &amp; QA)</span></div>
              <div className="text-slate-700 dark:text-slate-300">• Deeksith Raj N <span className="text-slate-400 text-[10px]">(UI/UX &amp; Maps)</span></div>
              <div className="text-slate-700 dark:text-slate-300">• Deepak D S <span className="text-slate-400 text-[10px]">(QA &amp; Docs)</span></div>
            </div>
          </div>

          {/* Links */}
          <div className="p-4 rounded-2xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 md:col-span-2 flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-bold text-slate-900 dark:text-white text-xs font-mono">
                SecureTrack v1.8.0 Release
              </p>
              <p className="text-[11px] text-slate-500">
                Licensed under Apache-2.0 • Copyright &copy; 2026 Dhanush S
              </p>
            </div>
            <a
              href="https://github.com/dhanushs602848/SecureTrack"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#1E293B] text-slate-800 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            >
              <Github className="w-3.5 h-3.5" />
              GitHub Repository
              <ExternalLink className="w-3 h-3 text-slate-400" />
            </a>
          </div>
        </div>
      </section>
    </div>
  );
};
