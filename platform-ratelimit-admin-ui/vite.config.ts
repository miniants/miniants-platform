import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  base: "./",
  root: "src/frontend",
  build: {
    outDir: "../../src/main/resources/META-INF/resources/platform-ratelimit-ui",
    emptyOutDir: true,
  },
  test: {
    environment: "jsdom",
    include: ["**/*.test.ts"],
  },
});
