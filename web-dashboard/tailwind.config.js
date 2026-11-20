/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        cyber: {
          bg: '#0A0E17',
          card: '#121927',
          cardElevated: '#182234',
          border: '#223049',
          cyan: '#00D2FF',
          indigo: '#6366F1',
          violet: '#8B5CF6',
          emerald: '#10B981',
          amber: '#F59E0B',
          coral: '#EF4444',
          slate: {
            50: '#F8FAFC',
            100: '#F1F5F9',
            200: '#E2E8F0',
            300: '#CBD5E1',
            400: '#94A3B8',
            500: '#64748B',
            600: '#475569',
            700: '#334155',
            800: '#1E293B',
            900: '#0F172A',
          }
        },
        navy: {
          900: '#152C4F',
          800: '#1e3a66',
        },
        brandBlue: '#255DA8',
        lightBlue: '#D8E6FF',
        bgApp: '#F8F9FB',
        borderCol: '#DFE2E7'
      }
    },
  },
  plugins: [],
}
