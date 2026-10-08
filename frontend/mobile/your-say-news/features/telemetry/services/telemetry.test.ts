import { logEvent, recorder } from "./telemetry";

describe("logEvent", () => {
    afterEach(() => jest.restoreAllMocks());

    it("queues a sign-in failure for upload and writes the same codes to the device log", () => {
        const warn = jest.spyOn(console, "warn").mockImplementation(() => undefined);
        const log = jest.spyOn(recorder, "log").mockImplementation(() => undefined);

        logEvent("warn", "auth.sign_in_failed", { stage: "google", code: "10" });

        expect(log).toHaveBeenCalledWith({
            level: "warn", name: "auth.sign_in_failed", attributes: { stage: "google", code: "10" },
        });
        expect(warn.mock.calls).toEqual([["auth.sign_in_failed stage=google code=10"]]);
    });
});
