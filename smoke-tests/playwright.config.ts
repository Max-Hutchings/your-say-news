import { defineConfig, devices } from "@playwright/test";
import { resolve } from "node:path";

const smokeRoot = __dirname;

export default defineConfig({
  testDir: resolve(smokeRoot, "specs"),
  outputDir: resolve(smokeRoot, "artifacts/test-results"),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  reporter: [
    ["line"],
    [
      "html",
      {
        open: "never",
        outputFolder: resolve(smokeRoot, "artifacts/report"),
      },
    ],
  ],
  use: {
    ...devices["Desktop Chrome"],
    baseURL: process.env.SMOKE_BASE_URL ?? "http://localhost:5173",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
      },
    },
  ],
});
