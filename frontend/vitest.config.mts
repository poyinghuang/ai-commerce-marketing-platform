import { configDefaults, defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    exclude: [...configDefaults.exclude, "e2e/**"],
    // Component suites replace the process-wide fetch implementation. Running files serially
    // prevents a mock owned by one jsdom environment from leaking into another under load.
    fileParallelism: false,
  },
});
