/**
 * Enterprise Visual Effects System for Your Say News
 * 
 * This file defines shadows, border radii, and other visual effects
 * used throughout the application for consistent elevation and styling.
 * 
 * Usage:
 *   import { Shadows, BorderRadius, Opacity } from '@/constants/theme';
 *   <View style={[Shadows.md, { borderRadius: BorderRadius.lg }]} />
 */

import { Platform, ViewStyle } from 'react-native';

// ============================================================================
// BORDER RADIUS
// ============================================================================

/**
 * Border radius scale for rounded corners
 */
export const BorderRadius = {
  /** No rounding */
  none: 0,
  
  /** 2px - Subtle rounding */
  xs: 2,
  
  /** 4px - Small rounding */
  sm: 4,
  
  /** 6px - Medium-small rounding */
  md: 6,
  
  /** 8px - Medium rounding (base) */
  base: 8,
  
  /** 12px - Large rounding */
  lg: 12,
  
  /** 16px - Extra large rounding */
  xl: 16,
  
  /** 20px - 2x extra large */
  '2xl': 20,
  
  /** 24px - 3x extra large */
  '3xl': 24,
  
  /** 32px - 4x extra large */
  '4xl': 32,
  
  /** Full rounding (pill shape) */
  full: 9999,
} as const;

// ============================================================================
// SHADOWS (Cross-platform)
// ============================================================================

/**
 * Platform-aware shadow styles
 * iOS uses shadow properties, Android uses elevation
 */
export const Shadows = {
  /** No shadow */
  none: {
    ...Platform.select({
      ios: {
        shadowColor: 'transparent',
        shadowOffset: { width: 0, height: 0 },
        shadowOpacity: 0,
        shadowRadius: 0,
      },
      android: {
        elevation: 0,
      },
    }),
  } as ViewStyle,
  
  /** Extra small shadow - subtle lift */
  xs: {
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.05,
        shadowRadius: 2,
      },
      android: {
        elevation: 1,
      },
    }),
  } as ViewStyle,
  
  /** Small shadow - light elevation */
  sm: {
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.1,
        shadowRadius: 3,
      },
      android: {
        elevation: 2,
      },
    }),
  } as ViewStyle,
  
  /** Medium shadow - default cards */
  md: {
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.1,
        shadowRadius: 6,
      },
      android: {
        elevation: 4,
      },
    }),
  } as ViewStyle,
  
  /** Large shadow - elevated elements */
  lg: {
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 8 },
        shadowOpacity: 0.12,
        shadowRadius: 12,
      },
      android: {
        elevation: 8,
      },
    }),
  } as ViewStyle,
  
  /** Extra large shadow - modals */
  xl: {
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 12 },
        shadowOpacity: 0.15,
        shadowRadius: 20,
      },
      android: {
        elevation: 12,
      },
    }),
  } as ViewStyle,
  
  /** 2x extra large shadow - floating elements */
  '2xl': {
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 20 },
        shadowOpacity: 0.2,
        shadowRadius: 30,
      },
      android: {
        elevation: 16,
      },
    }),
  } as ViewStyle,
} as const;

/**
 * Colored shadows for brand elements
 */
export const ColoredShadows = {
  primary: {
    ...Platform.select({
      ios: {
        shadowColor: '#1D5CFF',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
      },
      android: {
        elevation: 6,
      },
    }),
  } as ViewStyle,
  
  accent: {
    ...Platform.select({
      ios: {
        shadowColor: '#F97316',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
      },
      android: {
        elevation: 6,
      },
    }),
  } as ViewStyle,
  
  success: {
    ...Platform.select({
      ios: {
        shadowColor: '#22C55E',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
      },
      android: {
        elevation: 6,
      },
    }),
  } as ViewStyle,
  
  error: {
    ...Platform.select({
      ios: {
        shadowColor: '#EF4444',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.3,
        shadowRadius: 8,
      },
      android: {
        elevation: 6,
      },
    }),
  } as ViewStyle,
} as const;

// ============================================================================
// OPACITY
// ============================================================================

/**
 * Opacity scale for consistent transparency
 */
export const Opacity = {
  /** Fully transparent */
  transparent: 0,
  
  /** Very faint */
  '5': 0.05,
  
  /** Light overlay */
  '10': 0.1,
  
  /** Subtle */
  '20': 0.2,
  
  /** Light */
  '30': 0.3,
  
  /** Medium-light */
  '40': 0.4,
  
  /** Half transparent */
  '50': 0.5,
  
  /** Medium */
  '60': 0.6,
  
  /** Medium-strong */
  '70': 0.7,
  
  /** Strong */
  '80': 0.8,
  
  /** Very strong */
  '90': 0.9,
  
  /** Disabled state */
  disabled: 0.5,
  
  /** Hover state */
  hover: 0.8,
  
  /** Pressed state */
  pressed: 0.6,
  
  /** Fully opaque */
  opaque: 1,
} as const;

// ============================================================================
// BORDERS
// ============================================================================

/**
 * Border width scale
 */
export const BorderWidth = {
  none: 0,
  hairline: 0.5,
  thin: 1,
  base: 1.5,
  medium: 2,
  thick: 3,
  heavy: 4,
} as const;

// ============================================================================
// ANIMATION DURATIONS
// ============================================================================

/**
 * Animation duration presets in milliseconds
 */
export const AnimationDuration = {
  /** Instant feedback - 75ms */
  instant: 75,
  
  /** Very fast transitions - 100ms */
  fastest: 100,
  
  /** Fast transitions - 150ms */
  fast: 150,
  
  /** Normal transitions - 200ms */
  normal: 200,
  
  /** Medium transitions - 300ms */
  medium: 300,
  
  /** Slow transitions - 400ms */
  slow: 400,
  
  /** Very slow transitions - 500ms */
  slower: 500,
  
  /** Slowest transitions - 700ms */
  slowest: 700,
  
  /** Page transitions - 300ms */
  page: 300,
  
  /** Modal enter - 250ms */
  modalEnter: 250,
  
  /** Modal exit - 200ms */
  modalExit: 200,
} as const;

// ============================================================================
// ANIMATION EASING
// ============================================================================

/**
 * Common easing functions for react-native-reanimated
 * Import Easing from 'react-native-reanimated' to use these
 */
export const AnimationEasing = {
  /** Standard ease - general purpose */
  standard: [0.4, 0, 0.2, 1] as [number, number, number, number],
  
  /** Ease in - accelerate */
  easeIn: [0.4, 0, 1, 1] as [number, number, number, number],
  
  /** Ease out - decelerate */
  easeOut: [0, 0, 0.2, 1] as [number, number, number, number],
  
  /** Ease in-out - accelerate then decelerate */
  easeInOut: [0.4, 0, 0.2, 1] as [number, number, number, number],
  
  /** Sharp - quick start, soft end */
  sharp: [0.4, 0, 0.6, 1] as [number, number, number, number],
  
  /** Bounce - slight overshoot */
  bounce: [0.68, -0.55, 0.265, 1.55] as [number, number, number, number],
} as const;

// ============================================================================
// TYPE EXPORTS
// ============================================================================

export type BorderRadiusKey = keyof typeof BorderRadius;
export type ShadowKey = keyof typeof Shadows;
export type OpacityKey = keyof typeof Opacity;

