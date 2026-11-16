/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
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
