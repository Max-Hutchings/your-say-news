/**
 * Themed Card Component
 * 
 * A card container component that follows the theme system.
 * 
 * Usage:
 *   <Card>Content here</Card>
 *   <Card variant="elevated" padding="lg">Elevated card</Card>
 *   <Card pressable onPress={handlePress}>Clickable card</Card>
 */

import React from 'react';
import {
  View,
  TouchableOpacity,
  TouchableOpacityProps,
  ViewStyle,
  ViewProps,
} from 'react-native';
import {
  useTheme,
  BorderRadius,
  Spacing,
  Shadows,
  Opacity,
  type ThemeColors,
} from '@/constants/theme';

// ============================================================================
// TYPES
// ============================================================================

export type CardVariant = 
  | 'default'
  | 'elevated'
  | 'outlined'
  | 'ghost';

export type CardPadding = 'none' | 'sm' | 'md' | 'lg';

export interface CardProps extends ViewProps {
  children: React.ReactNode;
  variant?: CardVariant;
  padding?: CardPadding;
  pressable?: boolean;
  onPress?: () => void;
  disabled?: boolean;
}

// ============================================================================
// STYLE HELPERS
// ============================================================================

const getCardStyles = (
  colors: ThemeColors,
  variant: CardVariant,
  padding: CardPadding,
  disabled: boolean
): ViewStyle => {
  // Base styles
  const base: ViewStyle = {
    borderRadius: BorderRadius.lg,
    overflow: 'hidden',
  };

  // Padding styles
  const paddingStyles: Record<CardPadding, ViewStyle> = {
    none: {},
    sm: { padding: Spacing.sm },
    md: { padding: Spacing.base },
    lg: { padding: Spacing.xl },
  };

  // Variant styles
  const variantStyles: Record<CardVariant, ViewStyle> = {
    default: {
      backgroundColor: colors.surface.primary,
      ...Shadows.sm,
    },
    elevated: {
      backgroundColor: colors.surface.elevated,
      ...Shadows.md,
    },
    outlined: {
      backgroundColor: colors.surface.primary,
      borderWidth: 1,
      borderColor: colors.border.primary,
    },
    ghost: {
      backgroundColor: 'transparent',
    },
  };

  return {
    ...base,
    ...paddingStyles[padding],
    ...variantStyles[variant],
    ...(disabled ? { opacity: Opacity.disabled } : {}),
  };
};

// ============================================================================
// COMPONENT
// ============================================================================

export function Card({
  children,
  variant = 'default',
  padding = 'md',
  pressable = false,
  onPress,
  disabled = false,
  style,
  ...rest
}: CardProps) {
  const { colors } = useTheme();
  
  const cardStyles = getCardStyles(colors, variant, padding, disabled);

  if (pressable && onPress) {
    return (
      <TouchableOpacity
        onPress={onPress}
        disabled={disabled}
        activeOpacity={Opacity.hover}
        style={[cardStyles, style]}
        // rest is ViewProps; TouchableOpacity accepts the same props at runtime
        // but types its focus/blur events differently, so bridge across.
        {...(rest as TouchableOpacityProps)}
      >
        {children}
      </TouchableOpacity>
    );
  }

  return (
    <View style={[cardStyles, style]} {...rest}>
      {children}
    </View>
  );
}

