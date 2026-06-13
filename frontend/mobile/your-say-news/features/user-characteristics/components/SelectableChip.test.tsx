import React from "react";
import { render, fireEvent, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { SelectableChip } from "./SelectableChip";

function renderWithTheme(ui: React.ReactElement) {
    return render(<ThemeProvider>{ui}</ThemeProvider>);
}

describe("SelectableChip", () => {
    it("renders its label", () => {
        renderWithTheme(<SelectableChip label="Asian" selected={false} onPress={() => {}} />);
        expect(screen.getByText("Asian")).toBeOnTheScreen();
    });

    it("calls onPress when tapped", () => {
        const onPress = jest.fn();
        renderWithTheme(<SelectableChip label="Asian" selected={false} onPress={onPress} />);

        fireEvent.press(screen.getByText("Asian"));

        expect(onPress).toHaveBeenCalledTimes(1);
    });

    it("uses inverse text colour when selected", () => {
        const { rerender } = renderWithTheme(
            <SelectableChip label="Asian" selected={false} onPress={() => {}} />
        );
        const unselectedColor = screen.getByText("Asian").props.style
            .flat()
            .find((s: Record<string, unknown>) => s && "color" in s)?.color;

        rerender(
            <ThemeProvider>
                <SelectableChip label="Asian" selected onPress={() => {}} />
            </ThemeProvider>
        );
        const selectedColor = screen.getByText("Asian").props.style
            .flat()
            .find((s: Record<string, unknown>) => s && "color" in s)?.color;

        // Selected and unselected chips render visibly different text colours.
        expect(selectedColor).not.toEqual(unselectedColor);
    });
});
