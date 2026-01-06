/**
 * Enterprise Spacing System for Your Say News
 * 
 * This file defines the spacing scale used throughout the application
 * for margins, paddings, gaps, and layout dimensions.
 * 
 * The scale follows a 4px base unit for consistency.
 * 
 * Usage:
 *   import { Spacing, Layout } from '@/constants/theme';
 *   <View style={{ padding: Spacing.md, marginTop: Spacing.lg }} />
 */

// ============================================================================
// SPACING SCALE
// ============================================================================

/**
 * Core spacing scale based on 4px unit
 * Use these values for margins, paddings, and gaps
 */
export const Spacing = {
  /** 0px - No spacing */
  none: 0,
  
  /** 2px - Minimal spacing */
  '2xs': 2,
  
  /** 4px - Extra extra small */
  xs: 4,
  
  /** 8px - Small spacing */
  sm: 8,
  
  /** 12px - Medium-small spacing */
  md: 12,
  
  /** 16px - Medium spacing (base) */
  base: 16,
  
  /** 20px - Medium-large spacing */
  lg: 20,
  
  /** 24px - Large spacing */
  xl: 24,
  
  /** 32px - Extra large spacing */
  '2xl': 32,
  
  /** 40px - 2x extra large */
  '3xl': 40,
  
  /** 48px - 3x extra large */
  '4xl': 48,
  
  /** 56px - 4x extra large */
  '5xl': 56,
  
  /** 64px - 5x extra large */
  '6xl': 64,
  
  /** 80px - 6x extra large */
  '7xl': 80,
  
  /** 96px - 7x extra large */
  '8xl': 96,
  
  /** 128px - 8x extra large */
  '9xl': 128,
} as const;

// ============================================================================
// LAYOUT DIMENSIONS
// ============================================================================

/**
 * Common layout dimensions for consistent sizing
 */
export const Layout = {
  /** Screen edge padding */
  screenPadding: Spacing.base,
  
  /** Screen edge padding - compact */
  screenPaddingCompact: Spacing.md,
  
  /** Section padding (vertical) */
  sectionPadding: Spacing['2xl'],
  
  /** Card internal padding */
  cardPadding: Spacing.base,
  
  /** Card internal padding - compact */
  cardPaddingCompact: Spacing.md,
  
  /** Modal internal padding */
  modalPadding: Spacing.xl,
  
  /** List item padding (horizontal) */
  listItemPaddingX: Spacing.base,
  
  /** List item padding (vertical) */
  listItemPaddingY: Spacing.md,
  
  /** Input field padding (horizontal) */
  inputPaddingX: Spacing.base,
  
  /** Input field padding (vertical) */
  inputPaddingY: Spacing.md,
  
  /** Button padding (horizontal) */
  buttonPaddingX: Spacing.xl,
  
  /** Button padding (vertical) */
  buttonPaddingY: Spacing.md,
  
  /** Button padding - small (horizontal) */
  buttonSmallPaddingX: Spacing.base,
  
  /** Button padding - small (vertical) */
  buttonSmallPaddingY: Spacing.sm,
  
  /** Gap between form elements */
  formGap: Spacing.base,
  
  /** Gap between list items */
  listGap: Spacing.md,
  
  /** Gap between cards */
  cardGap: Spacing.base,
  
  /** Gap between inline elements */
  inlineGap: Spacing.sm,
  
  /** Gap in icon + text combinations */
  iconTextGap: Spacing.sm,
} as const;

// ============================================================================
// COMPONENT SIZES
// ============================================================================

/**
 * Standard sizes for common components
 */
export const ComponentSize = {
  // Icon sizes
  icon: {
    xs: 12,
    sm: 16,
    md: 20,
    base: 24,
    lg: 28,
    xl: 32,
    '2xl': 40,
    '3xl': 48,
  },
  
  // Avatar sizes
  avatar: {
    xs: 24,
    sm: 32,
    md: 40,
    base: 48,
    lg: 56,
    xl: 64,
    '2xl': 80,
    '3xl': 96,
  },
  
  // Button heights
  button: {
    sm: 32,
    md: 40,
    base: 48,
    lg: 56,
  },
  
  // Input heights
  input: {
    sm: 36,
    md: 44,
    base: 48,
    lg: 56,
  },
  
  // Chip/Badge heights
  chip: {
    sm: 24,
    md: 32,
    base: 36,
  },
  
  // Touch targets (minimum 44x44 for accessibility)
  touchTarget: {
    min: 44,
    base: 48,
    large: 56,
  },
  
  // Header heights
  header: {
    sm: 44,
    md: 56,
    base: 64,
  },
  
  // Tab bar heights
  tabBar: {
    base: 56,
    withLabels: 64,
  },
} as const;

// ============================================================================
// INSETS (Safe Areas)
// ============================================================================

/**
 * Common insets for safe area handling
 * These are fallback values - always prefer useSafeAreaInsets()
 */
export const Insets = {
  /** iOS status bar height fallback */
  statusBar: 44,
  
  /** iOS home indicator height fallback */
  homeIndicator: 34,
  
  /** Android navigation bar fallback */
  navigationBar: 48,
} as const;

// ============================================================================
// Z-INDEX SCALE
// ============================================================================

/**
 * Z-index values for layering
 */
export const ZIndex = {
  /** Below normal content */
  below: -1,
  
  /** Normal content level */
  base: 0,
  
  /** Slightly elevated (cards, buttons) */
  raised: 10,
  
  /** Navigation elements */
  navigation: 100,
  
  /** Dropdowns and popovers */
  dropdown: 200,
  
  /** Sticky headers */
  sticky: 300,
  
  /** Overlays and backdrops */
  overlay: 400,
  
  /** Modals and dialogs */
  modal: 500,
  
  /** Toasts and notifications */
  toast: 600,
  
  /** Tooltips */
  tooltip: 700,
  
  /** Maximum level */
  max: 9999,
} as const;

// ============================================================================
// TYPE EXPORTS
// ============================================================================

export type SpacingKey = keyof typeof Spacing;
export type LayoutKey = keyof typeof Layout;

