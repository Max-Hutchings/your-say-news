/**
 * Theme Provider for Your Say News
 * 
 * This provider wraps your app and provides theme context throughout.
 * It automatically handles light/dark mode switching and provides
 * typed access to all theme tokens.
 * 
 * Usage:
 *   // In _layout.tsx
 *   import { ThemeProvider } from '@/constants/theme';
 *   
 *   export default function RootLayout() {
 *     return (
 *       <ThemeProvider>
 *         <Stack />
 *       </ThemeProvider>
 *     );
 *   }
 *   
 *   // In components
 *   import { useTheme, useThemeColors } from '@/constants/theme';
 *   
 *   const { colors, isDark, toggleTheme } = useTheme();
 *   // OR just colors:
 *   const colors = useThemeColors();
 */

import React, { createContext, useContext, useMemo, useState, useCallback, ReactNode } from 'react';
import { useColorScheme as useSystemColorScheme } from 'react-native';
import { 
  ThemeColors, 
  LightTheme, 
  DarkTheme, 
  ColorScheme,
  getThemeColors,
} from './colors';
import { Typography } from './typography';
import { Spacing, Layout, ComponentSize, ZIndex } from './spacing';
import { BorderRadius, Shadows, Opacity, BorderWidth, AnimationDuration } from './effects';

// ============================================================================
// THEME CONTEXT TYPES
// ============================================================================

export interface ThemeContextValue {
  /** Current color scheme */
  colorScheme: ColorScheme;
  
  /** Whether dark mode is active */
  isDark: boolean;
  
  /** All theme colors for current mode */
  colors: ThemeColors;
  
  /** Toggle between light and dark mode */
  toggleTheme: () => void;
  
  /** Set specific color scheme */
  setColorScheme: (scheme: ColorScheme | 'system') => void;
  
  /** Whether using system color scheme */
  isSystemTheme: boolean;
  
  // All theme tokens
  typography: typeof Typography;
  spacing: typeof Spacing;
  layout: typeof Layout;
  componentSize: typeof ComponentSize;
  borderRadius: typeof BorderRadius;
  shadows: typeof Shadows;
  opacity: typeof Opacity;
  borderWidth: typeof BorderWidth;
  animation: typeof AnimationDuration;
  zIndex: typeof ZIndex;
}

// ============================================================================
// THEME CONTEXT
// ============================================================================

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

// ============================================================================
// THEME PROVIDER
// ============================================================================

export interface ThemeProviderProps {
  children: ReactNode;
  /** Initial color scheme. Defaults to 'system' */
  initialColorScheme?: ColorScheme | 'system';
}

export function ThemeProvider({ 
  children, 
  initialColorScheme = 'system' 
}: ThemeProviderProps) {
  const systemColorScheme = useSystemColorScheme();
  
  // State for manual theme override
  const [themePreference, setThemePreference] = useState<ColorScheme | 'system'>(
    initialColorScheme
  );
  
  // Determine actual color scheme
  const colorScheme: ColorScheme = useMemo(() => {
    if (themePreference === 'system') {
      return systemColorScheme ?? 'light';
    }
    return themePreference;
  }, [themePreference, systemColorScheme]);
  
  // Get theme colors
  const colors = useMemo(() => getThemeColors(colorScheme), [colorScheme]);
  
  // Toggle theme handler
  const toggleTheme = useCallback(() => {
    setThemePreference((prev) => {
      if (prev === 'system') {
        // If currently system, switch to opposite of current system theme
        return systemColorScheme === 'dark' ? 'light' : 'dark';
      }
      return prev === 'dark' ? 'light' : 'dark';
    });
  }, [systemColorScheme]);
  
  // Set specific color scheme
  const setColorScheme = useCallback((scheme: ColorScheme | 'system') => {
    setThemePreference(scheme);
  }, []);
  
  // Memoized context value
  const contextValue = useMemo<ThemeContextValue>(
    () => ({
      colorScheme,
      isDark: colorScheme === 'dark',
      colors,
      toggleTheme,
      setColorScheme,
      isSystemTheme: themePreference === 'system',
      typography: Typography,
      spacing: Spacing,
      layout: Layout,
      componentSize: ComponentSize,
      borderRadius: BorderRadius,
      shadows: Shadows,
      opacity: Opacity,
      borderWidth: BorderWidth,
      animation: AnimationDuration,
      zIndex: ZIndex,
    }),
    [colorScheme, colors, toggleTheme, setColorScheme, themePreference]
  );
  
  return (
    <ThemeContext.Provider value={contextValue}>
      {children}
    </ThemeContext.Provider>
  );
}

// ============================================================================
// HOOKS
// ============================================================================

/**
 * Get full theme context including colors, typography, spacing, etc.
 * 
 * @example
 * const { colors, isDark, typography, spacing } = useTheme();
 */
export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  
  return context;
}

/**
 * Get just the current theme colors
 * Convenience hook for components that only need colors
 * 
 * @example
 * const colors = useThemeColors();
 * <View style={{ backgroundColor: colors.background.primary }} />
 */
export function useThemeColors(): ThemeColors {
  const context = useContext(ThemeContext);
  
  if (context === undefined) {
    // Fallback for usage outside provider (returns light theme)
    console.warn('useThemeColors used outside ThemeProvider, defaulting to light theme');
    return LightTheme;
  }
  
  return context.colors;
}

/**
 * Get the current color scheme ('light' or 'dark')
 * 
 * @example
 * const scheme = useThemeColorScheme();
 * const bgColor = scheme === 'dark' ? '#000' : '#fff';
 */
export function useThemeColorScheme(): ColorScheme {
  const context = useContext(ThemeContext);
  
  if (context === undefined) {
    // Fallback
    return 'light';
  }
  
  return context.colorScheme;
}

/**
 * Check if dark mode is active
 * 
 * @example
 * const isDark = useIsDarkMode();
 */
export function useIsDarkMode(): boolean {
  const context = useContext(ThemeContext);
  return context?.isDark ?? false;
}

// ============================================================================
// STYLED HOOK
// ============================================================================

/**
 * Create themed styles using a factory function
 * 
 * @example
 * const styles = useThemedStyles((theme) => ({
 *   container: {
 *     backgroundColor: theme.colors.background.primary,
 *     padding: theme.spacing.md,
 *     borderRadius: theme.borderRadius.lg,
 *   },
 *   title: {
 *     ...theme.typography.h1,
 *     color: theme.colors.text.primary,
 *   },
 * }));
 */
export function useThemedStyles<T>(
  styleFactory: (theme: ThemeContextValue) => T
): T {
  const theme = useTheme();
  return useMemo(() => styleFactory(theme), [styleFactory, theme]);
}

