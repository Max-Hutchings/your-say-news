/**
 * Theme System - Central Export
 * 
 * This is the main entry point for the Your Say News theme system.
 * Import everything you need from this single file.
 * 
 * Usage:
 *   import { 
 *     Colors, 
 *     Typography, 
 *     Spacing,
 *     useTheme,
 *     useThemeColors,
 *     ThemeProvider,
 *   } from '@/constants/theme';
 */

// ============================================================================
// COLOR EXPORTS
// ============================================================================

export {
  // Brand colors
  BrandColors,
  
  // Semantic colors
  SemanticColors,
  
  // Neutral colors
  NeutralColors,
  
  // Theme-specific color sets
  LightTheme,
  DarkTheme,
  
  // Legacy compatibility
  Colors,
  
  // Helper functions
  getThemeColors,
  withOpacity,
  
  // Types
  type ThemeColors,
  type ColorScheme,
} from './colors';

// ============================================================================
// TYPOGRAPHY EXPORTS
// ============================================================================

export {
  // Font configuration
  FontFamily,
  FontWeight,
  FontSize,
  LineHeight,
  LineHeightMultiplier,
  LetterSpacing,
  
  // Pre-defined text styles
  Typography,
  
  // Types
  type TypographyVariant,
  type FontSizeKey,
  type FontWeightKey,
} from './typography';

// ============================================================================
// SPACING EXPORTS
// ============================================================================

export {
  // Spacing scale
  Spacing,
  
  // Layout dimensions
  Layout,
  
  // Component sizes
  ComponentSize,
  
  // Safe area insets
  Insets,
  
  // Z-index scale
  ZIndex,
  
  // Types
  type SpacingKey,
  type LayoutKey,
} from './spacing';

// ============================================================================
// VISUAL EFFECTS EXPORTS
// ============================================================================

export {
  // Border radius
  BorderRadius,
  
  // Shadows
  Shadows,
  ColoredShadows,
  
  // Opacity
  Opacity,
  
  // Borders
  BorderWidth,
  
  // Animation
  AnimationDuration,
  AnimationEasing,
  
  // Types
  type BorderRadiusKey,
  type ShadowKey,
  type OpacityKey,
} from './effects';

// ============================================================================
// THEME PROVIDER & HOOKS
// ============================================================================

export {
  // Provider
  ThemeProvider,
  
  // Hooks
  useTheme,
  useThemeColors,
  useThemeColorScheme,
  useIsDarkMode,
  useThemedStyles,
  
  // Types
  type ThemeProviderProps,
  type ThemeContextValue,
} from './ThemeProvider';

