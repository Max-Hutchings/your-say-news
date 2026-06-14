/**
 * Editorial design tokens — the authoritative "Your Say News" visual language from the design
 * handoff (`frontend/mobile/ui-designs`). Paper-tone surfaces, ink text, a lime brand signal, and
 * teal=Agree / coral=Disagree, across three font families.
 *
 * Kept as a standalone module (rather than folded into the legacy enterprise `ThemeColors`) so the
 * editorial Stage-1 screens can consume the exact handoff values without disturbing screens still
 * on the older palette. Pull `isDark` from `useTheme()` and call {@link getEditorial}.
 */

export type EditorialPalette = {
    /** Screen background. */
    bg: string;
    /** Slightly tinted feed background. */
    bgFeed: string;
    /** Card / raised surface. */
    surface: string;
    /** A second, marginally darker surface (SSO sheet, inputs in dark). */
    surfaceAlt: string;
    /** Card / input border. */
    border: string;
    /** Primary text (ink). */
    ink: string;
    /** Secondary text. */
    secondary: string;
    /** Muted mono labels / eyebrows. */
    muted: string;
    /** Brand signal — logo, primary CTA, "YOU" badge, active indicator. NEVER vote data. */
    lime: string;
    /** Agree. */
    teal: string;
    /** Disagree. */
    coral: string;
    /** Track behind vote bars. */
    track: string;
    /** Persistent privacy note panel. */
    privacyBg: string;
    privacyBorder: string;
    privacyText: string;
    privacyIcon: string;
    /** Chip (unselected) — selected chips invert to {@link ink} bg / {@link bg} text. */
    chipBg: string;
    chipBorder: string;
    chipText: string;
    /** Focus ring for inputs / searchable selects. */
    focus: string;
};

const light: EditorialPalette = {
    bg: "#F6F1E7",
    bgFeed: "#F1EBDD",
    surface: "#FFFDF8",
    surfaceAlt: "#F4EFE3",
    border: "#DCD4C4",
    ink: "#1B1815",
    secondary: "#5A564E",
    muted: "#9A9183",
    lime: "#C6FF4A",
    teal: "#157A63",
    coral: "#D6402F",
    track: "#EDE6D8",
    privacyBg: "#EFF4DC",
    privacyBorder: "#DDE7BD",
    privacyText: "#4F6320",
    privacyIcon: "#5C7A1E",
    chipBg: "#FFFDF8",
    chipBorder: "#DCD4C4",
    chipText: "#3A352D",
    focus: "#157A63",
};

const dark: EditorialPalette = {
    bg: "#14110D",
    bgFeed: "#100E0A",
    surface: "#1B1712",
    surfaceAlt: "#1E1A15",
    border: "#2E2920",
    ink: "#F2ECDF",
    secondary: "#A39A8A",
    muted: "#8E8576",
    lime: "#C6FF4A",
    teal: "#3FB592",
    coral: "#FF6A57",
    track: "#2A251E",
    privacyBg: "#1B231A",
    privacyBorder: "#2C3A26",
    privacyText: "#AFC97F",
    privacyIcon: "#9FCB5B",
    chipBg: "#1E1A15",
    chipBorder: "#2E2920",
    chipText: "#E7E1D4",
    focus: "#3FB592",
};

export function getEditorial(isDark: boolean): EditorialPalette {
    return isDark ? dark : light;
}

/**
 * Font-family names — these match the `@expo-google-fonts/*` export keys loaded in the root layout.
 * Newsreader (serif) = editorial headlines & big numbers; Schibsted Grotesk = UI/body;
 * Spline Sans Mono = labels, eyebrows, data, privacy notes.
 */
export const EditorialFont = {
    serif: "Newsreader_500Medium",
    serifRegular: "Newsreader_400Regular",
    serifItalic: "Newsreader_400Regular_Italic",
    sans: "SchibstedGrotesk_400Regular",
    sansMedium: "SchibstedGrotesk_500Medium",
    sansSemiBold: "SchibstedGrotesk_600SemiBold",
    sansBold: "SchibstedGrotesk_700Bold",
    mono: "SplineSansMono_400Regular",
    monoMedium: "SplineSansMono_500Medium",
    monoSemiBold: "SplineSansMono_600SemiBold",
} as const;
