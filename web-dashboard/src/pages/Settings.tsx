import React from 'react';
import { Sun, Moon, Laptop, Shield, User, CheckCircle2, Lock, Radio, Info, ExternalLink, GraduationCap, Github } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useTheme, ThemeMode } from '../context/ThemeContext';

export const Settings: React.FC = () => {
  const { user } = useAuth();
  const { theme, setTheme, resolvedTheme } = useTheme();

  return (
    <div className="space-y-8 max-w-4xl mx-auto animate-fade-in">
      <div>
        <h2 className="text-2xl font-bold font-mono tracking-tight text-slate-900 dark:text-white">
          System &amp; Appearance Settings
        </h2>
        <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
          Customize your cybersecurity console interface, theme modes, and verified security profile
        </p>
      </div>

      {/* 1. MANDATORY APPEARANCE THEME SELECTION (3 MODES) */}
      <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-5 transition-colors">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-indigo-500 dark:bg-cyan-400 animate-pulse" />
              <h3 className="text-base font-bold font-mono text-slate-900 dark:text-white">
                Appearance Settings
              </h3>
            </div>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
              Select exactly how SecureTrack renders across light, dark, or operating-system preferences
            </p>
          </div>
          <span className="text-[11px] font-mono px-2.5 py-1 rounded-full font-semibold border bg-slate-100 text-slate-700 border-slate-300 dark:bg-cyan-950/50 dark:text-cyan-400 dark:border-cyan-800">
            Active: {theme.toUpperCase()} {theme === 'system' ? `(${resolvedTheme})` : ''}
          </span>
        </div>

        {/* 3 Interactive Preview Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
          {/* Card 1: LIGHT MODE */}
          <button
            onClick={() => setTheme('light')}
            className={`group relative p-4 rounded-xl border text-left transition-all flex flex-col justify-between ${
              theme === 'light'
                ? 'border-indigo-600 dark:border-cyan-400 ring-2 ring-indigo-500/20 dark:ring-cyan-400/20 bg-indigo-50/40 dark:bg-slate-800/50 shadow-md'
                : 'border-slate-200 dark:border-[#223049] hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50/50 dark:bg-[#151E2E]/60'
            }`}
          >
            <div className="space-y-3">
              {/* Miniature Light Mockup */}
              <div className="w-full h-24 rounded-lg bg-[#F8FAFC] border border-slate-200 p-2 overflow-hidden shadow-inner flex flex-col justify-between">
                <div className="flex items-center justify-between border-b border-slate-200 pb-1.5">
                  <div className="flex items-center gap-1">
                    <div className="w-2.5 h-2.5 rounded bg-indigo-600" />
                    <div className="w-12 h-1.5 rounded bg-slate-800" />
                  </div>
                  <div className="w-3 h-3 rounded-full bg-cyan-500" />
                </div>
                <div className="grid grid-cols-2 gap-1.5 py-1">
                  <div className="bg-white border border-slate-200 rounded p-1">
                    <div className="w-8 h-1 rounded bg-slate-400 mb-1" />
                    <div className="w-4 h-2 rounded bg-indigo-600" />
                  </div>
                  <div className="bg-white border border-slate-200 rounded p-1">
                    <div className="w-8 h-1 rounded bg-slate-400 mb-1" />
                    <div className="w-4 h-2 rounded bg-emerald-500" />
                  </div>
                </div>
                <div className="w-full h-1.5 rounded bg-slate-200" />
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-xs font-mono text-slate-900 dark:text-white">
                    <Sun className="w-3.5 h-3.5 text-amber-500" />
                    Light Mode
                  </div>
                  {theme === 'light' && (
                    <CheckCircle2 className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
                  )}
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">
                  Crisp off-white surfaces, high readability, violet &amp; cyan highlights
                </p>
              </div>
            </div>
          </button>

          {/* Card 2: DARK MODE */}
          <button
            onClick={() => setTheme('dark')}
            className={`group relative p-4 rounded-xl border text-left transition-all flex flex-col justify-between ${
              theme === 'dark'
                ? 'border-indigo-600 dark:border-cyan-400 ring-2 ring-indigo-500/20 dark:ring-cyan-400/20 bg-indigo-50/40 dark:bg-slate-800/50 shadow-md'
                : 'border-slate-200 dark:border-[#223049] hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50/50 dark:bg-[#151E2E]/60'
            }`}
          >
            <div className="space-y-3">
              {/* Miniature Dark Mockup */}
              <div className="w-full h-24 rounded-lg bg-[#0A0E17] border border-[#223049] p-2 overflow-hidden shadow-inner flex flex-col justify-between">
                <div className="flex items-center justify-between border-b border-[#223049] pb-1.5">
                  <div className="flex items-center gap-1">
                    <div className="w-2.5 h-2.5 rounded bg-cyan-400 shadow-[0_0_6px_rgba(0,210,255,0.6)]" />
                    <div className="w-12 h-1.5 rounded bg-slate-200" />
                  </div>
                  <div className="w-3 h-3 rounded-full bg-cyan-400" />
                </div>
                <div className="grid grid-cols-2 gap-1.5 py-1">
                  <div className="bg-[#121927] border border-[#223049] rounded p-1">
                    <div className="w-8 h-1 rounded bg-slate-500 mb-1" />
                    <div className="w-4 h-2 rounded bg-cyan-400" />
                  </div>
                  <div className="bg-[#121927] border border-[#223049] rounded p-1">
                    <div className="w-8 h-1 rounded bg-slate-500 mb-1" />
                    <div className="w-4 h-2 rounded bg-emerald-400" />
                  </div>
                </div>
                <div className="w-full h-1.5 rounded bg-[#182234]" />
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-xs font-mono text-slate-900 dark:text-white">
                    <Moon className="w-3.5 h-3.5 text-cyan-400" />
                    Dark Mode
                  </div>
                  {theme === 'dark' && (
                    <CheckCircle2 className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
                  )}
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">
                  Deep midnight palette, neon electric cyan, optimal for night surveillance
                </p>
              </div>
            </div>
          </button>

          {/* Card 3: SYSTEM DEFAULT */}
          <button
            onClick={() => setTheme('system')}
            className={`group relative p-4 rounded-xl border text-left transition-all flex flex-col justify-between ${
              theme === 'system'
                ? 'border-indigo-600 dark:border-cyan-400 ring-2 ring-indigo-500/20 dark:ring-cyan-400/20 bg-indigo-50/40 dark:bg-slate-800/50 shadow-md'
                : 'border-slate-200 dark:border-[#223049] hover:border-slate-300 dark:hover:border-slate-700 bg-slate-50/50 dark:bg-[#151E2E]/60'
            }`}
          >
            <div className="space-y-3">
              {/* Miniature Split Mockup */}
              <div className="w-full h-24 rounded-lg overflow-hidden border border-slate-300 dark:border-[#223049] flex shadow-inner">
                {/* Left Half: Light */}
                <div className="w-1/2 bg-[#F8FAFC] p-2 flex flex-col justify-between border-r border-slate-300 dark:border-slate-700">
                  <div className="w-10 h-1.5 rounded bg-indigo-600" />
                  <div className="bg-white border border-slate-200 rounded p-1">
                    <div className="w-6 h-1 rounded bg-slate-400" />
                  </div>
                  <div className="w-8 h-1 rounded bg-slate-300" />
                </div>
                {/* Right Half: Dark */}
                <div className="w-1/2 bg-[#0A0E17] p-2 flex flex-col justify-between">
                  <div className="w-10 h-1.5 rounded bg-cyan-400" />
                  <div className="bg-[#121927] border border-[#223049] rounded p-1">
                    <div className="w-6 h-1 rounded bg-cyan-400" />
                  </div>
                  <div className="w-8 h-1 rounded bg-slate-700" />
                </div>
              </div>

              <div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-xs font-mono text-slate-900 dark:text-white">
                    <Laptop className="w-3.5 h-3.5 text-indigo-500 dark:text-violet-400" />
                    System Default
                  </div>
                  {theme === 'system' && (
                    <CheckCircle2 className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
                  )}
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">
                  Follows OS scheme without reload. Seamless transition between day and night
                </p>
              </div>
            </div>
          </button>
        </div>
      </div>

      {/* 2. USER PROFILE DETAILS */}
      <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4 transition-colors">
        <div className="flex items-center gap-2">
          <User className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
          <h3 className="text-base font-bold font-mono text-slate-900 dark:text-white">
            Operator Profile &amp; Identity
          </h3>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs pt-1">
          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <label className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400">
              Full Name
            </label>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm font-mono">
              {user?.fullName || 'SecureTrack Operator'}
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <label className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400">
              Verified Email
            </label>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm font-mono">
              {user?.email || 'N/A'}
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <label className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400">
              Emergency Contact
            </label>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm font-mono">
              {user?.phoneNumber || 'Configured via Android Settings'}
            </p>
          </div>

          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800">
            <label className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400">
              Auth Provider
            </label>
            <p className="font-semibold text-slate-900 dark:text-white mt-1 text-sm font-mono flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              Firebase Authentication (Zero-Trust)
            </p>
          </div>
        </div>
      </div>

      {/* 3. PLATFORM & SECURITY ARCHITECTURE */}
      <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-4 transition-colors">
        <div className="flex items-center gap-2">
          <Shield className="w-4 h-4 text-emerald-500 dark:text-emerald-400" />
          <h3 className="text-base font-bold font-mono text-slate-900 dark:text-white">
            Security &amp; Cloud Telemetry Specifications
          </h3>
        </div>

        <div className="space-y-3 text-xs text-slate-600 dark:text-slate-300">
          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800/60">
            <span className="font-medium">Cryptographic Device Pairing</span>
            <span className="font-mono text-emerald-600 dark:text-emerald-400 font-semibold">
              256-Bit SHA-256 Tokens
            </span>
          </div>

          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800/60">
            <span className="font-medium">Telemetry Backend</span>
            <span className="font-mono text-slate-900 dark:text-white font-semibold">
              Google Cloud Firestore (Real-time)
            </span>
          </div>

          <div className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-slate-800/60">
            <span className="font-medium">Remote Alarm Constraint</span>
            <span className="font-mono text-amber-600 dark:text-amber-400 font-semibold">
              Strict 5.0 Second Hard Limit
            </span>
          </div>

          <div className="flex items-center justify-between py-2">
            <span className="font-medium">Console Release Version</span>
            <span className="font-mono text-indigo-600 dark:text-cyan-400 font-semibold">
              SecureTrack Web v1.8.0 (Release)
            </span>
          </div>
        </div>
      </div>

      {/* 4. ABOUT SECURETRACK & PROJECT CREDITS */}
      <div className="bg-white dark:bg-[#121927] p-6 rounded-2xl border border-slate-200 dark:border-[#223049] shadow-sm space-y-5 transition-colors">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Info className="w-4 h-4 text-indigo-600 dark:text-cyan-400" />
            <h3 className="text-base font-bold font-mono text-slate-900 dark:text-white">
              About SecureTrack
            </h3>
          </div>
          <span className="text-[11px] font-mono px-2.5 py-1 rounded-full font-semibold border bg-emerald-50 text-emerald-700 border-emerald-300 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-800">
            Apache 2.0 Open Source
          </span>
        </div>

        <p className="text-xs text-slate-600 dark:text-slate-300 leading-relaxed">
          <strong>SecureTrack</strong> is an enterprise-grade mobile telemetry, loss prevention, and anti-theft defense platform engineered for Android devices, synchronized with a real-time cybersecurity web operations center. Engineered as an advanced academic project in <strong>Database Management Systems (DBMS) and Mobile Cybersecurity</strong>.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs pt-1">
          {/* Project Owner */}
          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-1">
            <div className="flex items-center gap-1.5 text-indigo-600 dark:text-cyan-400 font-mono text-[11px] font-semibold">
              <User className="w-3.5 h-3.5" />
              Project Lead, Owner &amp; Author
            </div>
            <p className="font-bold text-slate-900 dark:text-white text-sm font-mono">
              Dhanush S
            </p>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              System Architecture, Cloud Firestore Integration &amp; Release Management
            </p>
          </div>

          {/* Academic Guide */}
          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-1">
            <div className="flex items-center gap-1.5 text-emerald-600 dark:text-emerald-400 font-mono text-[11px] font-semibold">
              <GraduationCap className="w-3.5 h-3.5" />
              Academic &amp; DBMS Project Guide
            </div>
            <p className="font-bold text-slate-900 dark:text-white text-sm font-mono">
              Mohankumar H M
            </p>
            <p className="text-[11px] text-slate-500 dark:text-slate-400">
              Database Architecture, Relational Modeling &amp; Academic Review
            </p>
          </div>

          {/* Engineering Team */}
          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 space-y-1 md:col-span-2">
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

          {/* Project Coordinates */}
          <div className="p-3.5 rounded-xl bg-slate-50 dark:bg-[#162032] border border-slate-200 dark:border-slate-800 md:col-span-2 flex flex-wrap items-center justify-between gap-3">
            <div className="space-y-0.5">
              <div className="font-mono text-xs font-bold text-slate-900 dark:text-white">
                SecureTrack v1.8.0 (Build 7)
              </div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                Licensed under Apache-2.0 • Copyright &copy; 2026 Dhanush S
              </p>
            </div>
            <div className="flex items-center gap-3">
              <a
                href="https://github.com/dhanushs602848/SecureTrack"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-mono font-medium border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#1E293B] text-slate-800 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
              >
                <Github className="w-3.5 h-3.5" />
                GitHub
                <ExternalLink className="w-3 h-3 text-slate-400" />
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
