/**
 * Enterprise Typography System for Your Say News
 * 
 * This file defines the complete typography system including:
 * - Font families
 * - Font weights
 * - Font sizes with line heights
 * - Pre-defined text styles for consistency
 * 
 * Usage:
 *   import { Typography, FontFamily, FontSize } from '@/constants/theme';
 *   <Text style={Typography.h1} />
 *   <Text style={{ fontFamily: FontFamily.medium, fontSize: FontSize.lg }} />
 */

import { TextStyle, Platform } from 'react-native';

// ============================================================================
// FONT FAMILIES
// ============================================================================

/**
 * Font family configuration
 * 
 * To use custom fonts, add them to your app:
 * 1. Add font files to assets/fonts/
 * 2. Configure expo-font or app.json
 * 3. Update these values to match your font names
 * 
 * For now, using system fonts with fallbacks
 */
export const FontFamily = {
  // Sans-serif fonts (Primary)
  light: Platform.select({
    ios: 'System',
    android: 'Roboto',
    default: 'System',
  }),
  regular: Platform.select({
    ios: 'System',
    android: 'Roboto',
    default: 'System',
  }),
  medium: Platform.select({
    ios: 'System',
    android: 'Roboto',
    default: 'System',
  }),
  semiBold: Platform.select({
    ios: 'System',
    android: 'Roboto',
    default: 'System',
  }),
  bold: Platform.select({
    ios: 'System',
    android: 'Roboto',
    default: 'System',
  }),
  
  // Monospace fonts (Code/Data)
  mono: Platform.select({
    ios: 'Menlo',
    android: 'monospace',
    default: 'monospace',
  }),
} as const;

// ============================================================================
// FONT WEIGHTS
// ============================================================================

export const FontWeight = {
  thin: '100' as TextStyle['fontWeight'],
  extraLight: '200' as TextStyle['fontWeight'],
  light: '300' as TextStyle['fontWeight'],
  regular: '400' as TextStyle['fontWeight'],
  medium: '500' as TextStyle['fontWeight'],
  semiBold: '600' as TextStyle['fontWeight'],
  bold: '700' as TextStyle['fontWeight'],
  extraBold: '800' as TextStyle['fontWeight'],
  black: '900' as TextStyle['fontWeight'],
} as const;

// ============================================================================
// FONT SIZES
// ============================================================================

/**
 * Font size scale following a modular type scale
 * Each step is roughly 1.2x the previous (minor third)
 */
export const FontSize = {
  // Extra small - captions, labels
  '2xs': 10,
  xs: 12,
  
  // Small - secondary text, metadata
  sm: 14,
  
  // Base - body text
  base: 16,
  
  // Medium - emphasized body text
  md: 18,
  
  // Large - subheadings
  lg: 20,
  xl: 24,
  
  // Extra large - headings
  '2xl': 28,
  '3xl': 32,
  '4xl': 36,
  '5xl': 42,
  '6xl': 48,
  '7xl': 56,
} as const;

// ============================================================================
// LINE HEIGHTS
// ============================================================================

/**
 * Line height multipliers
 * These are applied relative to font size
 */
export const LineHeightMultiplier = {
  none: 1,
  tight: 1.25,
  snug: 1.375,
  normal: 1.5,
  relaxed: 1.625,
  loose: 2,
} as const;

/**
 * Absolute line heights for specific font sizes
 */
export const LineHeight = {
  '2xs': 14,
  xs: 16,
  sm: 20,
  base: 24,
  md: 26,
  lg: 28,
  xl: 32,
  '2xl': 36,
  '3xl': 40,
  '4xl': 44,
  '5xl': 52,
  '6xl': 60,
  '7xl': 68,
} as const;

// ============================================================================
// LETTER SPACING
// ============================================================================

export const LetterSpacing = {
  tighter: -0.8,
  tight: -0.4,
  normal: 0,
  wide: 0.4,
  wider: 0.8,
  widest: 1.6,
} as const;

// ============================================================================
// PRE-DEFINED TEXT STYLES
// ============================================================================

/**
 * Complete typography styles for consistent text rendering
 * These combine font family, size, weight, line height, and letter spacing
 */
export const Typography = {
  // ==========================================================================
  // DISPLAY - Large hero text
  // ==========================================================================
  displayLarge: {
    fontFamily: FontFamily.bold,
    fontSize: FontSize['7xl'],
    lineHeight: LineHeight['7xl'],
    fontWeight: FontWeight.bold,
    letterSpacing: LetterSpacing.tight,
  } as TextStyle,
  
  displayMedium: {
    fontFamily: FontFamily.bold,
    fontSize: FontSize['6xl'],
    lineHeight: LineHeight['6xl'],
    fontWeight: FontWeight.bold,
    letterSpacing: LetterSpacing.tight,
  } as TextStyle,
  
  displaySmall: {
    fontFamily: FontFamily.bold,
    fontSize: FontSize['5xl'],
    lineHeight: LineHeight['5xl'],
    fontWeight: FontWeight.bold,
    letterSpacing: LetterSpacing.tight,
  } as TextStyle,
  
  // ==========================================================================
  // HEADINGS
  // ==========================================================================
  h1: {
    fontFamily: FontFamily.bold,
    fontSize: FontSize['4xl'],
    lineHeight: LineHeight['4xl'],
    fontWeight: FontWeight.bold,
    letterSpacing: LetterSpacing.tight,
  } as TextStyle,
  
  h2: {
    fontFamily: FontFamily.bold,
    fontSize: FontSize['3xl'],
    lineHeight: LineHeight['3xl'],
    fontWeight: FontWeight.bold,
    letterSpacing: LetterSpacing.tight,
  } as TextStyle,
  
  h3: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize['2xl'],
    lineHeight: LineHeight['2xl'],
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  h4: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.xl,
    lineHeight: LineHeight.xl,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  h5: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.lg,
    lineHeight: LineHeight.lg,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  h6: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.md,
    lineHeight: LineHeight.md,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  // ==========================================================================
  // BODY TEXT
  // ==========================================================================
  bodyLarge: {
    fontFamily: FontFamily.regular,
    fontSize: FontSize.lg,
    lineHeight: LineHeight.lg,
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  bodyMedium: {
    fontFamily: FontFamily.regular,
    fontSize: FontSize.base,
    lineHeight: LineHeight.base,
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  bodySmall: {
    fontFamily: FontFamily.regular,
    fontSize: FontSize.sm,
    lineHeight: LineHeight.sm,
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  // ==========================================================================
  // LABELS - Form labels, buttons, etc.
  // ==========================================================================
  labelLarge: {
    fontFamily: FontFamily.medium,
    fontSize: FontSize.base,
    lineHeight: LineHeight.base,
    fontWeight: FontWeight.medium,
    letterSpacing: LetterSpacing.wide,
  } as TextStyle,
  
  labelMedium: {
    fontFamily: FontFamily.medium,
    fontSize: FontSize.sm,
    lineHeight: LineHeight.sm,
    fontWeight: FontWeight.medium,
    letterSpacing: LetterSpacing.wide,
  } as TextStyle,
  
  labelSmall: {
    fontFamily: FontFamily.medium,
    fontSize: FontSize.xs,
    lineHeight: LineHeight.xs,
    fontWeight: FontWeight.medium,
    letterSpacing: LetterSpacing.wider,
  } as TextStyle,
  
  // ==========================================================================
  // CAPTIONS - Small helper text
  // ==========================================================================
  caption: {
    fontFamily: FontFamily.regular,
    fontSize: FontSize.xs,
    lineHeight: LineHeight.xs,
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  captionSmall: {
    fontFamily: FontFamily.regular,
    fontSize: FontSize['2xs'],
    lineHeight: LineHeight['2xs'],
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.wide,
  } as TextStyle,
  
  // ==========================================================================
  // OVERLINES - Uppercase category labels
  // ==========================================================================
  overline: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.xs,
    lineHeight: LineHeight.xs,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.widest,
    textTransform: 'uppercase',
  } as TextStyle,
  
  // ==========================================================================
  // SPECIAL STYLES
  // ==========================================================================
  link: {
    fontFamily: FontFamily.medium,
    fontSize: FontSize.base,
    lineHeight: LineHeight.base,
    fontWeight: FontWeight.medium,
    letterSpacing: LetterSpacing.normal,
    textDecorationLine: 'underline',
  } as TextStyle,
  
  buttonLarge: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.base,
    lineHeight: LineHeight.base,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.wide,
  } as TextStyle,
  
  buttonMedium: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.sm,
    lineHeight: LineHeight.sm,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.wide,
  } as TextStyle,
  
  buttonSmall: {
    fontFamily: FontFamily.semiBold,
    fontSize: FontSize.xs,
    lineHeight: LineHeight.xs,
    fontWeight: FontWeight.semiBold,
    letterSpacing: LetterSpacing.wide,
  } as TextStyle,
  
  code: {
    fontFamily: FontFamily.mono,
    fontSize: FontSize.sm,
    lineHeight: LineHeight.sm,
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.normal,
  } as TextStyle,
  
  quote: {
    fontFamily: FontFamily.regular,
    fontSize: FontSize.lg,
    lineHeight: LineHeight.lg,
    fontWeight: FontWeight.regular,
    letterSpacing: LetterSpacing.normal,
    fontStyle: 'italic',
  } as TextStyle,
} as const;

// ============================================================================
// TYPE EXPORTS
// ============================================================================

export type TypographyVariant = keyof typeof Typography;
export type FontSizeKey = keyof typeof FontSize;
export type FontWeightKey = keyof typeof FontWeight;

