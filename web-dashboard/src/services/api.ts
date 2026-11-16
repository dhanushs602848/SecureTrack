import axios from 'axios';
import { ApiResponse, Device, DeviceCommand, LocationRecord, SecurityEvent, User } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const authService = {
  login: async (email: string, password: string) => {
    const res = await api.post<ApiResponse<{ accessToken: string; refreshToken: string; user: User }>>('/auth/login', { email, password });
    return res.data;
  },
  register: async (email: string, password: string, fullName: string, phoneNumber?: string) => {
    const res = await api.post<ApiResponse<{ accessToken: string; refreshToken: string; user: User }>>('/auth/register', { email, password, fullName, phoneNumber });
    return res.data;
  },
  getCurrentUser: async () => {
    const res = await api.get<ApiResponse<User>>('/users/me');
    return res.data.data;
  },
};

export const deviceService = {
  getDevices: async () => {
    const res = await api.get<ApiResponse<Device[]>>('/devices');
    return res.data.data;
  },
  getDevice: async (id: number) => {
    const res = await api.get<ApiResponse<Device>>(`/devices/${id}`);
    return res.data.data;
  },
  createPairingCode: async () => {
    const res = await api.post<ApiResponse<{ code: string; expiresAt: string }>>('/pairing/create');
    return res.data.data;
  },
};

export const locationService = {
  getLatestLocation: async (deviceId: number) => {
    const res = await api.get<ApiResponse<LocationRecord>>(`/locations/latest/${deviceId}`);
    return res.data.data;
  },
  getLocationHistory: async (deviceId: number) => {
    const res = await api.get<ApiResponse<LocationRecord[]>>(`/locations/history/${deviceId}`);
    return res.data.data;
  },
};

export const securityService = {
  getSecurityEvents: async () => {
    const res = await api.get<ApiResponse<SecurityEvent[]>>('/security-events');
    return res.data.data;
  },
  getDeviceSecurityEvents: async (deviceId: number) => {
    const res = await api.get<ApiResponse<SecurityEvent[]>>(`/security-events/${deviceId}`);
    return res.data.data;
  },
};

export const commandService = {
  getDeviceCommands: async (deviceId: number) => {
    const res = await api.get<ApiResponse<DeviceCommand[]>>(`/devices/${deviceId}/commands`);
    return res.data.data;
  },
  sendCommand: async (deviceId: number, commandType: string, payload?: string) => {
    const res = await api.post<ApiResponse<DeviceCommand>>(`/devices/${deviceId}/commands`, { commandType, payload });
    return res.data.data;
  },
};

export default api;
