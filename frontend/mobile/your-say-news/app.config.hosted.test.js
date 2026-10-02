/**
 * @jest-environment node
 */
/* global jest, describe, it, expect, afterEach */
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const ORIGINAL_ENV = process.env;
let fixtureDir;

function setHostedEnv(env) {
    const { EAS_BUILD, GOOGLE_SERVICES_JSON, ...inherited } = ORIGINAL_ENV;
    process.env = {
        ...inherited,
        APP_ENV: "development",
        EXPO_PUBLIC_API_BASE_URL: "https://dev.yoursaynews.com/api/",
        ...env,
    };
}

function loadHostedConfig(env) {
    setHostedEnv(env);
    let config;
    jest.isolateModules(() => {
        config = require("./app.config.hosted.js").default;
    });
    return config;
}

function androidClient(packageName, appId, apiKey) {
    return {
        client_info: {
            mobilesdk_app_id: appId,
            android_client_info: { package_name: packageName },
        },
        api_key: [{ current_key: apiKey }],
        oauth_client: [
            { client_id: `${packageName}.android.apps.googleusercontent.com`, client_type: 1 },
            { client_id: `${packageName}.web.apps.googleusercontent.com`, client_type: 3 },
        ],
    };
}

// A Firebase project can hold several Android apps; ours is deliberately listed second.
function writeGoogleServicesFixture(projectId = "your-say-news-development") {
    fixtureDir = mkdtempSync(join(tmpdir(), "ysn-"));
    const file = join(fixtureDir, "google-services.json");
    writeFileSync(
        file,
        JSON.stringify({
            project_info: { project_id: projectId },
            client: [
                androidClient("com.yoursaynews.other", "1:787102990070:android:other", "AIza-other-key"),
                androidClient("com.yoursaynews.app", "1:787102990070:android:abc123", "AIza-test-key"),
            ],
        }),
    );
    return file;
}

afterEach(() => {
    process.env = ORIGINAL_ENV;
    if (fixtureDir) {
        rmSync(fixtureDir, { recursive: true, force: true });
        fixtureDir = undefined;
    }
});

describe("hosted app config", () => {
    it("loads on a laptop or CI without the Firebase file so eas commands need no local copy", () => {
        const config = loadHostedConfig({});

        expect(config.android.googleServicesFile).toBeUndefined();
        expect(config.extra.FIREBASE_PROJECT_ID).toBeUndefined();
        expect(config.extra).toMatchObject({
            USER_SERVICE_HOST: "https://dev.yoursaynews.com/api",
            USER_SERVICE_PORT: "",
            POST_SERVICE_HOST: "https://dev.yoursaynews.com/api",
            POST_SERVICE_PORT: "",
            CHARACTERISTIC_SERVICE_HOST: "https://dev.yoursaynews.com/api",
            CHARACTERISTIC_SERVICE_PORT: "",
        });
    });

    it("fails the EAS build when the Firebase file variable is missing", () => {
        expect(() => loadHostedConfig({ EAS_BUILD: "true" })).toThrow(
            "Missing required Expo environment variable: GOOGLE_SERVICES_JSON",
        );
    });

    it("embeds the Firebase client config of com.yoursaynews.app when the file is provided", () => {
        const file = writeGoogleServicesFixture();

        const config = loadHostedConfig({ EAS_BUILD: "true", GOOGLE_SERVICES_JSON: file });

        expect(config.android.googleServicesFile).toBe(file);
        expect(config.extra).toMatchObject({
            FIREBASE_PROJECT_ID: "your-say-news-development",
            FIREBASE_API_KEY: "AIza-test-key",
            FIREBASE_APP_ID: "1:787102990070:android:abc123",
            GOOGLE_WEB_CLIENT_ID: "com.yoursaynews.app.web.apps.googleusercontent.com",
        });
    });

    it("rejects a Firebase file from another project so the dev app cannot ship with it", () => {
        const file = writeGoogleServicesFixture("your-say-news-production");

        expect(() => loadHostedConfig({ EAS_BUILD: "true", GOOGLE_SERVICES_JSON: file })).toThrow(
            "GOOGLE_SERVICES_JSON must contain the development Firebase config for com.yoursaynews.app",
        );
    });

    it("keeps the app.json Android package when app.config.js merges in the hosted config", () => {
        const file = writeGoogleServicesFixture();
        setHostedEnv({ GOOGLE_SERVICES_JSON: file });

        let config;
        jest.isolateModules(() => {
            const appConfig = require("./app.config.js").default;
            config = appConfig({
                config: { android: { package: "com.yoursaynews.app" }, extra: { eas: { projectId: "p1" } } },
            });
        });

        expect(config.android).toEqual({ package: "com.yoursaynews.app", googleServicesFile: file });
        expect(config.extra.eas).toEqual({ projectId: "p1" });
        expect(config.extra.AUTH_MODE).toBe("google");
    });
});
