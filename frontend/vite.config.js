import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxyTarget = env.VITE_API_PROXY_TARGET || "http://localhost:8080";

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": { target: proxyTarget, changeOrigin: true },
        "/oauth2": { target: proxyTarget, changeOrigin: true },
        "/login/oauth2": { target: proxyTarget, changeOrigin: true },
        "/actuator": { target: proxyTarget, changeOrigin: true },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setupTests.js",
      css: true,
      clearMocks: true,
      testTimeout: 10_000,
    },
  };
});
