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
    /** Text/icons placed on lime CTAs and badges. */
    onLime: string;
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

    /**
     * Support-question "inverted onto ink" block — the motion is always drawn on a
     * near-black panel in BOTH themes (only the exact black shifts), with light text.
     */
    inkBlock: string;
    onInkBlock: string;
    onInkBlockMuted: string;
    /**
     * Agree/Disagree PREVIEW pills as drawn on {@link inkBlock} (compose + Pepper).
     * Distinct from {@link teal}/{@link coral}, which are for on-paper vote data.
     */
    agreePreview: string;
    agreePreviewBorder: string;
    disagreePreview: string;
    disagreePreviewBorder: string;
    /**
     * Agree/Disagree VOTE buttons in the feed (unvoted state) — a soft teal/coral tint with a
     * matching border; the label uses {@link teal}/{@link coral}.
     */
    voteAgreeBg: string;
    voteAgreeBorder: string;
    voteDisagreeBg: string;
    voteDisagreeBorder: string;
    /** Body text inside the "case for" / "case against" cards (labels use {@link teal}/{@link coral}). */
    caseForText: string;
    caseAgainstText: string;
    /** Lime outline + oversized quote mark on the motion box. */
    motionBorder: string;
    /** Solid Agree button — a filled green with dark text; Disagree is an outlined dark/coral button. */
    voteAgreeFill: string;
    voteAgreeOnFill: string;
    voteDisagreeFill: string;
    /** Text / scrim for content laid over media (photo & video wells). */
    onMedia: string;
    mediaScrim: string;
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
    onLime: "#1B1815",
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
    inkBlock: "#1B1815",
    onInkBlock: "#F2ECDF",
    onInkBlockMuted: "#8E8576",
    agreePreview: "#3FB592",
    agreePreviewBorder: "#2E634F",
    disagreePreview: "#FF6A57",
    disagreePreviewBorder: "#6B342B",
    voteAgreeBg: "#EAF4EE",
    voteAgreeBorder: "#CFE3D6",
    voteDisagreeBg: "#F8ECE9",
    voteDisagreeBorder: "#EBCFC9",
    caseForText: "#34503F",
    caseAgainstText: "#6B3A33",
    motionBorder: "#B4C36B",
    voteAgreeFill: "#3E9B7C",
    voteAgreeOnFill: "#10231A",
    voteDisagreeFill: "#F6E7E3",
    onMedia: "#F2ECDF",
    mediaScrim: "rgba(15,13,10,0.7)",
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
    onLime: "#1B1815",
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
    inkBlock: "#0F0D0A",
    onInkBlock: "#F2ECDF",
    onInkBlockMuted: "#8E8576",
    agreePreview: "#3FB592",
    agreePreviewBorder: "#2E634F",
    disagreePreview: "#FF6A57",
    disagreePreviewBorder: "#6B342B",
    voteAgreeBg: "#17251F",
    voteAgreeBorder: "#2E4A3E",
    voteDisagreeBg: "#2A1A17",
    voteDisagreeBorder: "#4A2F2A",
    caseForText: "#A9CBB5",
    caseAgainstText: "#E3B0A8",
    motionBorder: "#6E7B3C",
    voteAgreeFill: "#54A386",
    voteAgreeOnFill: "#10231A",
    voteDisagreeFill: "#2E1512",
    onMedia: "#F2ECDF",
    mediaScrim: "rgba(15,13,10,0.7)",
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

/**
 * Feed media boxes — two fixed shapes that the image carousel and the video both inherit, so media
 * never renders squashed or near-square. There are exactly two sizes: LANDSCAPE is a wide 16:9,
 * PORTRAIT is a tall 4:5. Both derive their pixel height from the feed viewport width, so a given
 * orientation is the same shape on every device and every post. Values are width ÷ height.
 */
export const FeedMediaAspect = {
    landscape: 16 / 9,
    portrait: 4 / 5,
} as const;

export type FeedMediaOrientation = "LANDSCAPE" | "PORTRAIT";

/** Pixel height of the feed media box for an orientation at the given viewport width. */
export function feedMediaHeight(orientation: FeedMediaOrientation, viewportWidth: number): number {
    const aspect = orientation === "PORTRAIT" ? FeedMediaAspect.portrait : FeedMediaAspect.landscape;
    return Math.round(viewportWidth / aspect);
}
