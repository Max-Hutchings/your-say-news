/**
 * Themed View Component
 * 
 * A View component that automatically adapts to the current theme.
 * 
 * Usage:
 *   <ThemedView>Content here</ThemedView>
 *   <ThemedView variant="secondary">Secondary background</ThemedView>
 *   <ThemedView variant="surface">Card-like surface</ThemedView>
 */

import { View, type ViewProps } from 'react-native';
import { useThemeColors, type ThemeColors } from '@/constants/theme';

export type ThemedViewVariant = 
  | 'primary'      // Main background
  | 'secondary'    // Secondary background
  | 'tertiary'     // Tertiary background
  | 'surface'      // Card/modal surface
  | 'elevated'     // Elevated surface
  | 'transparent'; // No background

export type ThemedViewProps = ViewProps & {
  /** Background variant */
  variant?: ThemedViewVariant;
  /** Legacy: light mode override color */
  lightColor?: string;
  /** Legacy: dark mode override color */
  darkColor?: string;
};

const getBackgroundColor = (
  colors: ThemeColors,
  variant: ThemedViewVariant,
  lightColor?: string,
  darkColor?: string
): string | undefined => {
  // Legacy override support
  if (lightColor || darkColor) {
    return colors.background.primary; // Will be overridden by style
  }
  
  switch (variant) {
    case 'primary':
      return colors.background.primary;
    case 'secondary':
      return colors.background.secondary;
    case 'tertiary':
      return colors.background.tertiary;
    case 'surface':
      return colors.surface.primary;
    case 'elevated':
      return colors.surface.elevated;
    case 'transparent':
      return 'transparent';
    default:
      return colors.background.primary;
  }
};

export function ThemedView({ 
  style, 
  variant = 'primary',
  lightColor, 
  darkColor, 
  ...otherProps 
}: ThemedViewProps) {
  const colors = useThemeColors();
  const backgroundColor = getBackgroundColor(colors, variant, lightColor, darkColor);

  return <View style={[{ backgroundColor }, style]} {...otherProps} />;
}
