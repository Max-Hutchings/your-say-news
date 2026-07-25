import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { AccountScreen } from "./AccountScreen";

const mockBack = jest.fn();
const mockPush = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ back: mockBack, push: mockPush }),
}));
jest.mock("@expo/vector-icons", () => ({ Ionicons: () => null }));

const mockLogout = jest.fn();
let mockAuthState: {
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  logout: typeof mockLogout;
};
jest.mock("@/features/auth", () => ({
  useAuthStore: (selector: (state: typeof mockAuthState) => unknown) =>
    selector(mockAuthState),
}));

beforeEach(() => {
  jest.clearAllMocks();
  mockAuthState = {
    email: "amina.khan@example.org",
    firstName: "Amina",
    lastName: "Khan",
    logout: mockLogout,
  };
  mockLogout.mockResolvedValue(undefined);
});

function renderScreen() {
  return render(
    <ThemeProvider>
      <AccountScreen />
    </ThemeProvider>,
  );
}

test("shows the reader identity and opens account destinations", () => {
  renderScreen();

  expect(screen.getByText("Amina Khan")).toBeTruthy();
  expect(screen.getByText("amina.khan@example.org")).toBeTruthy();
  expect(screen.getByText("A")).toBeTruthy();

  fireEvent.press(screen.getByText("Profile"));
  fireEvent.press(screen.getByText("Settings"));
  fireEvent.press(screen.getByLabelText("Close"));

  expect(mockPush).toHaveBeenNthCalledWith(1, "/profiles/me");
  expect(mockPush).toHaveBeenNthCalledWith(2, "/settings");
  expect(mockBack).toHaveBeenCalledTimes(1);
});

test("logging out waits for the auth store action", async () => {
  renderScreen();

  fireEvent.press(screen.getByLabelText("Log out"));

  await waitFor(() => expect(mockLogout).toHaveBeenCalledTimes(1));
});

test("uses privacy-safe fallbacks when profile claims are absent", () => {
  mockAuthState = { email: null, firstName: null, lastName: null, logout: mockLogout };

  renderScreen();

  expect(screen.getByText("Your account")).toBeTruthy();
  expect(screen.getByText("?")).toBeTruthy();
  expect(screen.queryByText(/@example/)).toBeNull();
});
