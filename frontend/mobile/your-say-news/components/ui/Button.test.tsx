import React from "react";
import { render, fireEvent, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { Button } from "./Button";

function renderWithTheme(ui: React.ReactElement) {
    return render(<ThemeProvider>{ui}</ThemeProvider>);
}

describe("Button", () => {
    it("renders its label", () => {
        renderWithTheme(<Button onPress={() => {}}>Sign in</Button>);
        expect(screen.getByText("Sign in")).toBeOnTheScreen();
    });

    it("calls onPress when tapped", () => {
        const onPress = jest.fn();
        renderWithTheme(<Button onPress={onPress}>Tap</Button>);

        fireEvent.press(screen.getByText("Tap"));

        expect(onPress).toHaveBeenCalledTimes(1);
    });

    it("does not call onPress when disabled", () => {
        const onPress = jest.fn();
        renderWithTheme(
            <Button onPress={onPress} disabled>
                Tap
            </Button>
        );

        fireEvent.press(screen.getByText("Tap"));

        expect(onPress).not.toHaveBeenCalled();
    });

    it("does not call onPress while loading", () => {
        const onPress = jest.fn();
        renderWithTheme(
            <Button onPress={onPress} loading>
                Saving
            </Button>
        );

        fireEvent.press(screen.getByText("Saving"));

        expect(onPress).not.toHaveBeenCalled();
    });
});
