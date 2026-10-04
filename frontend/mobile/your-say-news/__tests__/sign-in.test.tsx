// Lives outside app/ because expo-router treats every file in app/ as a route.
jest.mock("expo-constants", () => ({
    __esModule: true,
    default: { expoConfig: { extra: { AUTH_MODE: "google" } } },
}));
const mockLogin = jest.fn();
jest.mock("@/features/auth", () => ({
    ...jest.requireActual("@/features/auth/signInErrorMessage"),
    useAuthStore: () => ({ login: mockLogin }),
}));

import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import SignInScreen from "@/app/sign-in";

beforeEach(() => mockLogin.mockReset());

test("a hosted Google failure shows the stage and provider code", async () => {
    mockLogin.mockResolvedValue({ status: "failed", stage: "google", code: "10" });
    render(<SignInScreen />);

    fireEvent.press(screen.getByText("Continue with Google"));

    expect(await screen.findByText("Google rejected this sign-in (google: 10).")).toBeTruthy();
});

test("an unexpected login exception shows the service-unavailable message", async () => {
    mockLogin.mockRejectedValue(new Error("network down"));
    render(<SignInScreen />);

    fireEvent.press(screen.getByText("Continue with Google"));

    expect(await screen.findByText("The authentication service is not available.")).toBeTruthy();
});
