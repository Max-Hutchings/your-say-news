import React from "react";
import { render, fireEvent, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { WizardScale, newsWord } from "./WizardScale";

function renderWithTheme(ui: React.ReactElement) {
    return render(<ThemeProvider>{ui}</ThemeProvider>);
}

describe("newsWord", () => {
    it.each([
        [0, "Headlines only"],
        [2, "Headlines only"],
        [3, "Occasional"],
        [4, "Occasional"],
        [5, "Daily reader"],
        [6, "Daily reader"],
        [7, "Well-read"],
        [8, "Well-read"],
        [9, "News junkie"],
        [10, "News junkie"],
    ])("maps %i to %s", (value, word) => {
        expect(newsWord(value)).toBe(word);
    });
});

describe("WizardScale", () => {
    it("shows a placeholder until a value is chosen", () => {
        renderWithTheme(<WizardScale value={null} onChange={() => {}} />);
        expect(screen.getByText("–")).toBeOnTheScreen();
        expect(screen.getByText("Tap to rate")).toBeOnTheScreen();
    });

    it("reports the tapped bar's index and reflects the word label", () => {
        const onChange = jest.fn();
        const { rerender } = renderWithTheme(<WizardScale value={null} onChange={onChange} />);

        fireEvent.press(screen.getByLabelText("News following 8 of 10"));
        expect(onChange).toHaveBeenCalledWith(8);

        rerender(
            <ThemeProvider>
                <WizardScale value={8} onChange={onChange} />
            </ThemeProvider>
        );
        expect(screen.getByText("8")).toBeOnTheScreen();
        expect(screen.getByText("Well-read")).toBeOnTheScreen();
    });
});
