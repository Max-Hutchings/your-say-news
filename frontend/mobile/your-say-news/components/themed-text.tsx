/**
 * Themed Text Component
 * 
 * A Text component that automatically adapts to the current theme
 * and supports the typography system.
 * 
 * Usage:
 *   <ThemedText>Default body text</ThemedText>
 *   <ThemedText variant="h1">Heading 1</ThemedText>
 *   <ThemedText variant="bodySmall" color="secondary">Small muted text</ThemedText>
 */

import { Text, type TextProps, StyleSheet } from 'react-native';
import { 
  useThemeColors, 
  Typography, 
  type ThemeColors,
  type TypographyVariant,
} from '@/constants/theme';

// Legacy type mapping for backward compatibility
type LegacyType = 'default' | 'title' | 'defaultSemiBold' | 'subtitle' | 'link';

// Text color variants
type TextColorVariant = 
  | 'primary'
  | 'secondary'
  | 'tertiary'
  | 'disabled'
  | 'inverse'
  | 'link'
  | 'success'
  | 'warning'
  | 'error'
  | 'info';

export type ThemedTextProps = TextProps & {
  /** Typography variant from the theme system */
  variant?: TypographyVariant;
  /** Text color variant */
  color?: TextColorVariant;
  /** Legacy: light mode override color */
  lightColor?: string;
  /** Legacy: dark mode override color */
  darkColor?: string;
  /** Legacy type prop for backward compatibility */
  type?: LegacyType;
};

const getTextColor = (colors: ThemeColors, colorVariant: TextColorVariant): string => {
  switch (colorVariant) {
    case 'primary':
      return colors.text.primary;
    case 'secondary':
      return colors.text.secondary;
    case 'tertiary':
      return colors.text.tertiary;
    case 'disabled':
      return colors.text.disabled;
    case 'inverse':
      return colors.text.inverse;
    case 'link':
      return colors.text.link;
    case 'success':
      return colors.status.success;
    case 'warning':
      return colors.status.warning;
    case 'error':
      return colors.status.error;
    case 'info':
      return colors.status.info;
    default:
      return colors.text.primary;
  }
};

// Map legacy types to new variants
const legacyTypeToVariant: Record<LegacyType, TypographyVariant> = {
  default: 'bodyMedium',
  title: 'h1',
  defaultSemiBold: 'labelLarge',
  subtitle: 'h4',
  link: 'link',
};

export function ThemedText({
  style,
  variant,
  color = 'primary',
  lightColor,
  darkColor,
  type,
  ...rest
}: ThemedTextProps) {
  const colors = useThemeColors();
  
  // Determine the typography variant
  const typographyVariant: TypographyVariant = variant ?? (type ? legacyTypeToVariant[type] : 'bodyMedium');
  
  // Get typography styles
  const typographyStyle = Typography[typographyVariant];
  
  // Get text color (handle legacy link type specially)
  const textColor = type === 'link' 
    ? colors.text.link 
    : getTextColor(colors, color);

  return (
    <Text
      style={[
        typographyStyle,
        { color: textColor },
        style,
      ]}
      {...rest}
    />
  );
}

// Legacy styles export for backward compatibility
export const legacyStyles = StyleSheet.create({
  default: {
    fontSize: 16,
    lineHeight: 24,
  },
  defaultSemiBold: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '600',
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    lineHeight: 32,
  },
  subtitle: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  link: {
    lineHeight: 30,
    fontSize: 16,
  },
});
