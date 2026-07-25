import { YES_NO_OPTIONS } from "./options";

describe("user-characteristic presentation options", () => {
    it("keeps the frontend-owned boolean choices stable", () => {
        expect(YES_NO_OPTIONS).toEqual([
            { label: "Yes", value: "YES" },
            { label: "No", value: "NO" },
        ]);
    });
});
