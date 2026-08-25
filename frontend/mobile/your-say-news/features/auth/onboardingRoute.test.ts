import { resolveOnboardingDestination } from "./onboardingRoute";

const CONSENTED_AT = "2026-06-14T09:00:00Z";

describe("resolveOnboardingDestination", () => {
    it("sends a profiled but unconsented user to consent, never back through the wizard", () => {
        expect(
            resolveOnboardingDestination({
                consentedAt: null,
                hasCharacteristics: true,
                serverConfirmed: true,
            })
        ).toBe("consent");
    });

    it("sends that same user straight to the feed once they consent", () => {
        expect(
            resolveOnboardingDestination({
                consentedAt: CONSENTED_AT,
                hasCharacteristics: true,
                serverConfirmed: true,
            })
        ).toBe("feed");
    });

    it("asks for consent before characteristics for a brand new account", () => {
        expect(
            resolveOnboardingDestination({
                consentedAt: null,
                hasCharacteristics: false,
                serverConfirmed: true,
            })
        ).toBe("consent");
    });

    it("runs the wizard only once the server confirms there is no profile", () => {
        expect(
            resolveOnboardingDestination({
                consentedAt: CONSENTED_AT,
                hasCharacteristics: false,
                serverConfirmed: true,
            })
        ).toBe("characteristics");
    });

    it("waits rather than re-asking when the profile flag is not server-confirmed", () => {
        expect(
            resolveOnboardingDestination({
                consentedAt: CONSENTED_AT,
                hasCharacteristics: false,
                serverConfirmed: false,
            })
        ).toBe("checking");
    });
});
