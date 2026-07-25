import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/admin/",
  plugins: [react()],
  server: {
    host: "localhost",
    port: 8083,
    strictPort: true,
  },
});
