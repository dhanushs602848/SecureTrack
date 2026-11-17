import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || "AIzaSyANQQBX_C-SNwk-7k6bNTnPmuEA440sWH4",
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || "track-99c90.firebaseapp.com",
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || "track-99c90",
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || "track-99c90.firebasestorage.app",
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || "302801589438",
  appId: import.meta.env.VITE_FIREBASE_APP_ID || "1:302801589438:web:9b8fd9ae41f07f913dfaef"
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
