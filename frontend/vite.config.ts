import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: { host: "127.0.0.1", proxy: {
    "/api/utenti": {target:"http://127.0.0.1:8101",rewrite:p=>p.replace("/api/utenti","/api")},
    "/api/pagamento": {target:"http://127.0.0.1:8102",rewrite:p=>p.replace("/api/pagamento","/api")},
    "/api/luce-business": {target:"http://127.0.0.1:8104",rewrite:p=>p.replace("/api/luce-business","/api")},
    "/api/luce": {target:"http://127.0.0.1:8103",rewrite:p=>p.replace("/api/luce","/api")},
    "/api/gas": {target:"http://127.0.0.1:8105",rewrite:p=>p.replace("/api/gas","/api")},
  }},
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    include: ["src/**/*.test.{ts,tsx}"],
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ["react", "react-dom", "react-router-dom"],
          charts: ["recharts"],
        },
      },
    },
  },
});
