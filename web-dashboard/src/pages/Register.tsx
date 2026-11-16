import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Shield, Lock, Mail, User, Phone } from 'lucide-react';
import { authService } from '../services/api';
import { useAuth } from '../context/AuthContext';

export const Register: React.FC = () => {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await authService.register(email, password, fullName, phone);
      login(res.data.accessToken, res.data.refreshToken, res.data.user);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F8F9FB] flex items-center justify-center p-4">
      <div className="max-w-md w-full bg-white rounded-2xl shadow-xl border border-[#DFE2E7] p-8 space-y-6">
        <div className="text-center space-y-2">
          <div className="inline-flex p-3 bg-[#D8E6FF] rounded-2xl text-[#152C4F]">
            <Shield className="w-10 h-10" />
          </div>
          <h2 className="text-2xl font-bold text-[#152C4F]">Create SecureTrack Account</h2>
          <p className="text-sm text-[#62666D]">Track smart. Stay secure.</p>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-[#1A1C1E] mb-1">Full Name</label>
            <div className="relative">
              <User className="w-5 h-5 absolute left-3 top-3 text-[#62666D]" />
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="John Doe"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-[#DFE2E7] focus:outline-none focus:ring-2 focus:ring-[#255DA8] text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#1A1C1E] mb-1">Email Address</label>
            <div className="relative">
              <Mail className="w-5 h-5 absolute left-3 top-3 text-[#62666D]" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="name@example.com"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-[#DFE2E7] focus:outline-none focus:ring-2 focus:ring-[#255DA8] text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#1A1C1E] mb-1">Phone Number</label>
            <div className="relative">
              <Phone className="w-5 h-5 absolute left-3 top-3 text-[#62666D]" />
              <input
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+1 555 0192"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-[#DFE2E7] focus:outline-none focus:ring-2 focus:ring-[#255DA8] text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[#1A1C1E] mb-1">Password</label>
            <div className="relative">
              <Lock className="w-5 h-5 absolute left-3 top-3 text-[#62666D]" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-[#DFE2E7] focus:outline-none focus:ring-2 focus:ring-[#255DA8] text-sm"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 bg-[#152C4F] text-white font-semibold rounded-xl shadow-md hover:bg-[#1e3a66] transition-all disabled:opacity-50"
          >
            {loading ? 'Creating Account...' : 'Register'}
          </button>
        </form>

        <p className="text-center text-sm text-[#62666D]">
          Already have an account?{' '}
          <Link to="/login" className="text-[#255DA8] font-semibold hover:underline">
            Sign In
          </Link>
        </p>
      </div>
    </div>
  );
};
