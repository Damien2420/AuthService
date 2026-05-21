import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import path from "path"
import { defineConfig, loadEnv } from "vite"

export default defineConfig(({ mode }) => {
  // Build-time 驗證：確保所有必要環境變數在建置前已設定
  const env = loadEnv(mode, process.cwd(), "");
  const required = ["VITE_API_BASE_URL", "VITE_GOOGLE_CLIENT_ID"];
  const missing = required.filter((key) => !env[key]);
  if (missing.length > 0) {
    throw new Error(`[vite] 缺少必要環境變數：${missing.join(", ")}`);
  }

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      open: false,
    },
  };
})