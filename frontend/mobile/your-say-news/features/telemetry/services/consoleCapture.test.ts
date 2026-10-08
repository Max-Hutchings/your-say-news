import {
    MAX_CONSOLE_RECORDS_PER_LAUNCH,
    installConsoleCapture,
    uninstallConsoleCapture,
    writeToDeviceLog,
    type LogSink,
} from "./consoleCapture";

describe("console capture", () => {
    let printed: { level: string; args: unknown[] }[];
    let sink: LogSink & { log: jest.Mock };

    beforeEach(() => {
        printed = [];
        for (const level of ["log", "info", "warn", "error"] as const) {
            jest.spyOn(console, level).mockImplementation((...args: unknown[]) => {
                printed.push({ level, args });
            });
        }
        sink = { log: jest.fn() };
        installConsoleCapture(sink);
    });

    afterEach(() => {
        uninstallConsoleCapture();
        jest.restoreAllMocks();
    });

    it("forwards warnings and errors as console records and still prints them to the device log", () => {
        console.warn("Slow image load", 3, "retries");
        console.error(new TypeError("x is undefined"));

        expect(sink.log.mock.calls).toEqual([
            [{ level: "warn", name: "console", message: "Slow image load 3 retries" }],
            [{ level: "error", name: "console", message: "TypeError: x is undefined" }],
        ]);
        expect(printed.map((line) => line.level)).toEqual(["warn", "error"]);
    });

    it("never serialises objects, which could hold a user's profile or email", () => {
        console.warn("Failed to save", { email: "jane@example.com", firstName: "Jane" }, ["a"]);

        expect(sink.log).toHaveBeenCalledWith({ level: "warn", name: "console", message: "Failed to save [object] [object]" });
    });

    it("leaves console.log and console.info on the device only", () => {
        console.log("render tick");
        console.info("Network/request error:", "timeout");

        expect(sink.log).not.toHaveBeenCalled();
        expect(printed).toHaveLength(2);
    });

    it("stops forwarding after the per-launch cap so a warning loop cannot flood the queue", () => {
        for (let i = 0; i < MAX_CONSOLE_RECORDS_PER_LAUNCH + 5; i++) console.warn("again");

        expect(sink.log).toHaveBeenCalledTimes(MAX_CONSOLE_RECORDS_PER_LAUNCH);
        expect(printed).toHaveLength(MAX_CONSOLE_RECORDS_PER_LAUNCH + 5);
    });

    it("does not loop when recording a line itself writes a warning", () => {
        sink.log.mockImplementation(() => console.warn("storage full"));

        console.error("boom");

        expect(sink.log).toHaveBeenCalledTimes(1);
        expect(printed.map((line) => line.args[0])).toEqual(["boom", "storage full"]);
    });

    it("writes structured diagnostics to the device log without forwarding them twice", () => {
        writeToDeviceLog("warn", "auth.sign_in_failed stage=google code=10");

        expect(printed).toEqual([{ level: "warn", args: ["auth.sign_in_failed stage=google code=10"] }]);
        expect(sink.log).not.toHaveBeenCalled();
    });
});
