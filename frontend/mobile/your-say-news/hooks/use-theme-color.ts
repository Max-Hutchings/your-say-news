/**
 * Theme Color Hook for Your Say News
 * 
 * This hook provides backward compatibility with the legacy color system
 * while integrating with the new comprehensive theme system.
 * 
 * For new code, prefer using:
 *   import { useThemeColors } from '@/constants/theme';
 *   const colors = useThemeColors();
 * 
 * Learn more about light and dark modes:
 * https://docs.expo.dev/guides/color-schemes/
 */

import { Colors, useThemeColors, useThemeColorScheme } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';

/**
 * Legacy hook for backward compatibility
 * @deprecated Use useThemeColors() from '@/constants/theme' instead
 */
export function useThemeColor(
  props: { light?: string; dark?: string },
  colorName: keyof typeof Colors.light & keyof typeof Colors.dark
) {
  const theme = useColorScheme() ?? 'light';
  const colorFromProps = props[theme];

  if (colorFromProps) {
    return colorFromProps;
  } else {
    return Colors[theme][colorName];
  }
}

/**
 * Re-export new theme hooks for convenience
 */
export { useThemeColors, useThemeColorScheme };
