/**
 * Themed Button Component
 * 
 * A comprehensive button component that follows the theme system.
 * 
 * Usage:
 *   <Button onPress={handlePress}>Click me</Button>
 *   <Button variant="secondary" size="lg">Large Secondary</Button>
 *   <Button variant="outline" loading>Loading...</Button>
 */

import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  ViewStyle,
  TextStyle,
  StyleSheet,
} from 'react-native';
import {
  useTheme,
  Typography,
  BorderRadius,
  Spacing,
  ComponentSize,
  Opacity,
  Shadows,
  type ThemeColors,
} from '@/constants/theme';

// ============================================================================
// TYPES
// ============================================================================

export type ButtonVariant = 
  | 'primary'
  | 'secondary'
  | 'outline'
  | 'ghost'
  | 'destructive';

export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps {
  children: React.ReactNode;
  onPress?: () => void;
  variant?: ButtonVariant;
  size?: ButtonSize;
  disabled?: boolean;
  loading?: boolean;
  fullWidth?: boolean;
  style?: ViewStyle;
  textStyle?: TextStyle;
}

// ============================================================================
// STYLE HELPERS
// ============================================================================

const getButtonStyles = (
  colors: ThemeColors,
  variant: ButtonVariant,
  size: ButtonSize,
  disabled: boolean,
  fullWidth: boolean
): ViewStyle => {
  // Base styles
  const base: ViewStyle = {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: BorderRadius.base,
    ...(fullWidth ? { width: '100%' } : {}),
  };

  // Size styles
  const sizeStyles: Record<ButtonSize, ViewStyle> = {
    sm: {
      height: ComponentSize.button.sm,
      paddingHorizontal: Spacing.md,
      gap: Spacing.xs,
    },
    md: {
      height: ComponentSize.button.md,
      paddingHorizontal: Spacing.base,
      gap: Spacing.sm,
    },
    lg: {
      height: ComponentSize.button.lg,
      paddingHorizontal: Spacing.xl,
      gap: Spacing.sm,
    },
  };

  // Variant styles
  const variantStyles: Record<ButtonVariant, ViewStyle> = {
    primary: {
      backgroundColor: disabled 
        ? colors.interactive.primaryDisabled 
        : colors.interactive.primary,
      ...Shadows.sm,
    },
    secondary: {
      backgroundColor: disabled
        ? colors.surface.tertiary
        : colors.interactive.secondary,
    },
    outline: {
      backgroundColor: 'transparent',
      borderWidth: 1.5,
      borderColor: disabled 
        ? colors.border.primary 
        : colors.border.secondary,
    },
    ghost: {
      backgroundColor: 'transparent',
    },
    destructive: {
      backgroundColor: disabled
        ? colors.status.errorBackground
        : colors.status.error,
      ...Shadows.sm,
    },
  };

  return {
    ...base,
    ...sizeStyles[size],
    ...variantStyles[variant],
    opacity: disabled ? Opacity.disabled : Opacity.opaque,
  };
};

const getTextStyles = (
  colors: ThemeColors,
  variant: ButtonVariant,
  size: ButtonSize,
  disabled: boolean
): TextStyle => {
  // Size typography
  const sizeTypography: Record<ButtonSize, TextStyle> = {
    sm: Typography.buttonSmall,
    md: Typography.buttonMedium,
    lg: Typography.buttonLarge,
  };

  // Variant text colors
  const variantColors: Record<ButtonVariant, string> = {
    primary: colors.text.inverse,
    secondary: colors.text.primary,
    outline: disabled ? colors.text.disabled : colors.text.primary,
    ghost: disabled ? colors.text.disabled : colors.brand.primary,
    destructive: colors.text.inverse,
  };

  return {
    ...sizeTypography[size],
    color: variantColors[variant],
  };
};

// ============================================================================
// COMPONENT
// ============================================================================

export function Button({
  children,
  onPress,
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  fullWidth = false,
  style,
  textStyle,
}: ButtonProps) {
  const { colors } = useTheme();
  
  const isDisabled = disabled || loading;
  
  const buttonStyles = getButtonStyles(colors, variant, size, isDisabled, fullWidth);
  const labelStyles = getTextStyles(colors, variant, size, isDisabled);
  
  // Loading indicator color
  const loaderColor = variant === 'primary' || variant === 'destructive'
    ? colors.text.inverse
    : colors.brand.primary;

  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={Opacity.pressed}
      style={[buttonStyles, style]}
    >
      {loading && (
        <ActivityIndicator 
          size="small" 
          color={loaderColor} 
        />
      )}
      {typeof children === 'string' ? (
        <Text style={[labelStyles, textStyle]}>{children}</Text>
      ) : (
        children
      )}
    </TouchableOpacity>
  );
}

