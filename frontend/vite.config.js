import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The browser talks to Vite, which forwards /api to the backend, so the backend needs no CORS config.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
