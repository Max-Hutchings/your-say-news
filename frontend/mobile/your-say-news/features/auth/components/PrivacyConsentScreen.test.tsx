import React from "react";
import { render, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PrivacyConsentScreen } from "./PrivacyConsentScreen";
import { useAuthStore } from "../services/authContext";
import { recordConsent } from "../services/ConsentService";

const mockReplace = jest.fn();
jest.mock("expo-router", () => ({ useRouter: () => ({ replace: mockReplace }) }));
jest.mock("../services/ConsentService", () => ({ recordConsent: jest.fn() }));

function renderWithTheme(ui: React.ReactElement) {
    return render(<ThemeProvider>{ui}</ThemeProvider>);
}

describe("PrivacyConsentScreen", () => {
    beforeEach(() => {
        mockReplace.mockClear();
        (recordConsent as jest.Mock).mockReset();
        useAuthStore.setState({ consentedAt: null });
    });

    it("states the privacy promise plainly", () => {
        renderWithTheme(<PrivacyConsentScreen />);
        expect(
            screen.getByText("Your name or email is never shown beside a vote, to anyone.")
        ).toBeOnTheScreen();
    });

    it("records consent, stores the timestamp, and advances to the wizard", async () => {
        (recordConsent as jest.Mock).mockResolvedValue("2026-06-13T10:00:00Z");

        renderWithTheme(<PrivacyConsentScreen />);
        fireEvent.press(screen.getByText("I agree — continue"));

        await waitFor(() => expect(recordConsent).toHaveBeenCalledTimes(1));
        await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/usercharacteristics"));
        expect(useAuthStore.getState().consentedAt).toBe("2026-06-13T10:00:00Z");
    });

    it("does not advance if recording consent fails", async () => {
        (recordConsent as jest.Mock).mockRejectedValue(new Error("network"));

        renderWithTheme(<PrivacyConsentScreen />);
        fireEvent.press(screen.getByText("I agree — continue"));

        await waitFor(() => expect(recordConsent).toHaveBeenCalledTimes(1));
        expect(mockReplace).not.toHaveBeenCalled();
        expect(useAuthStore.getState().consentedAt).toBeNull();
    });
});
