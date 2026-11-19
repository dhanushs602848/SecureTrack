import React, { createContext, useContext, useEffect, useState } from 'react';
import { User } from '../types';
import { auth, db } from '../services/firebase';
import {
  onAuthStateChanged,
  onIdTokenChanged,
  User as FirebaseUser
} from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { authService } from '../services/api';

interface AuthContextType {
  user: User | null;
  firebaseUser: FirebaseUser | null;
  token: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<any>;
  loginWithGoogle: () => Promise<any>;
  register: (email: string, password: string, fullName: string, phoneNumber?: string) => Promise<any>;
  sendPasswordReset: (email: string) => Promise<any>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [firebaseUser, setFirebaseUser] = useState<FirebaseUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsubscribeAuth = onAuthStateChanged(auth, async (fbUser) => {
      setFirebaseUser(fbUser);
      if (fbUser) {
        try {
          const idToken = await fbUser.getIdToken();
          setToken(idToken);

          const userDoc = await getDoc(doc(db, 'users', fbUser.uid));
          const fullName = userDoc.exists()
            ? (userDoc.data()?.fullName || fbUser.displayName || fbUser.email || 'User')
            : (fbUser.displayName || fbUser.email || 'User');
          const phoneNumber = userDoc.exists() ? (userDoc.data()?.phoneNumber || '') : '';

          setUser({
            id: fbUser.uid,
            email: fbUser.email || '',
            fullName: fullName,
            phoneNumber: phoneNumber
          });
        } catch (error) {
          console.error('Error fetching user profile in onAuthStateChanged:', error);
          setUser({
            id: fbUser.uid,
            email: fbUser.email || '',
            fullName: fbUser.displayName || fbUser.email || 'User'
          });
        }
      } else {
        setUser(null);
        setToken(null);
      }
      setLoading(false);
    });

    const unsubscribeToken = onIdTokenChanged(auth, async (fbUser) => {
      if (fbUser) {
        const idToken = await fbUser.getIdToken();
        setToken(idToken);
      } else {
        setToken(null);
      }
    });

    return () => {
      unsubscribeAuth();
      unsubscribeToken();
    };
  }, []);

  const login = async (email: string, password: string) => {
    return await authService.login(email, password);
  };

  const loginWithGoogle = async () => {
    return await authService.loginWithGoogle();
  };

  const register = async (email: string, password: string, fullName: string, phoneNumber?: string) => {
    return await authService.register(email, password, fullName, phoneNumber);
  };

  const sendPasswordReset = async (email: string) => {
    return await authService.sendPasswordReset(email);
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
    setToken(null);
    setFirebaseUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        firebaseUser,
        token,
        loading,
        login,
        loginWithGoogle,
        register,
        sendPasswordReset,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
