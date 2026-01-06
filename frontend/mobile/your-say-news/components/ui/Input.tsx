/**
 * Themed Input Component
 * 
 * A text input component that follows the theme system.
 * 
 * Usage:
 *   <Input placeholder="Enter text" value={value} onChangeText={setValue} />
 *   <Input label="Email" error="Invalid email" />
 *   <Input size="lg" variant="filled" />
 */

import React, { useState } from 'react';
import {
  View,
  TextInput,
  Text,
  TextInputProps,
  ViewStyle,
  TextStyle,
} from 'react-native';
import {
  useTheme,
  Typography,
  BorderRadius,
  Spacing,
  ComponentSize,
  BorderWidth,
  type ThemeColors,
} from '@/constants/theme';

// ============================================================================
// TYPES
// ============================================================================

export type InputVariant = 'outlined' | 'filled';
export type InputSize = 'sm' | 'md' | 'lg';

export interface InputProps extends Omit<TextInputProps, 'style'> {
  label?: string;
  error?: string;
  helperText?: string;
  variant?: InputVariant;
  size?: InputSize;
  disabled?: boolean;
  containerStyle?: ViewStyle;
  inputStyle?: TextStyle;
}

// ============================================================================
// STYLE HELPERS
// ============================================================================

const getContainerStyles = (): ViewStyle => ({
  width: '100%',
});

const getLabelStyles = (colors: ThemeColors): TextStyle => ({
  ...Typography.labelMedium,
  color: colors.text.secondary,
  marginBottom: Spacing.xs,
});

const getInputStyles = (
  colors: ThemeColors,
  variant: InputVariant,
  size: InputSize,
  isFocused: boolean,
  hasError: boolean,
  disabled: boolean
): ViewStyle & TextStyle => {
  // Size styles
  const sizeStyles: Record<InputSize, ViewStyle> = {
    sm: { height: ComponentSize.input.sm, paddingHorizontal: Spacing.sm },
    md: { height: ComponentSize.input.md, paddingHorizontal: Spacing.md },
    lg: { height: ComponentSize.input.lg, paddingHorizontal: Spacing.base },
  };

  // Border color logic
  let borderColor = colors.input.border;
  if (hasError) {
    borderColor = colors.status.error;
  } else if (isFocused) {
    borderColor = colors.input.borderFocus;
  }

  // Variant styles
  const variantStyles: Record<InputVariant, ViewStyle> = {
    outlined: {
      backgroundColor: colors.input.background,
      borderWidth: BorderWidth.thin,
      borderColor,
    },
    filled: {
      backgroundColor: disabled ? colors.surface.tertiary : colors.surface.secondary,
      borderWidth: 0,
      borderBottomWidth: BorderWidth.medium,
      borderBottomColor: borderColor,
      borderRadius: 0,
      borderTopLeftRadius: BorderRadius.base,
      borderTopRightRadius: BorderRadius.base,
    },
  };

  return {
    ...Typography.bodyMedium,
    color: disabled ? colors.text.disabled : colors.input.text,
    borderRadius: BorderRadius.base,
    ...sizeStyles[size],
    ...variantStyles[variant],
  };
};

const getHelperTextStyles = (colors: ThemeColors, isError: boolean): TextStyle => ({
  ...Typography.caption,
  color: isError ? colors.status.error : colors.text.tertiary,
  marginTop: Spacing.xs,
});

// ============================================================================
// COMPONENT
// ============================================================================

export function Input({
  label,
  error,
  helperText,
  variant = 'outlined',
  size = 'md',
  disabled = false,
  containerStyle,
  inputStyle,
  onFocus,
  onBlur,
  ...rest
}: InputProps) {
  const { colors } = useTheme();
  const [isFocused, setIsFocused] = useState(false);
  
  const hasError = Boolean(error);
  
  const handleFocus = (e: any) => {
    setIsFocused(true);
    onFocus?.(e);
  };
  
  const handleBlur = (e: any) => {
    setIsFocused(false);
    onBlur?.(e);
  };

  const containerStyles = getContainerStyles();
  const labelStyles = getLabelStyles(colors);
  const inputStyles = getInputStyles(colors, variant, size, isFocused, hasError, disabled);
  const helperStyles = getHelperTextStyles(colors, hasError);

  return (
    <View style={[containerStyles, containerStyle]}>
      {label && (
        <Text style={labelStyles}>{label}</Text>
      )}
      
      <TextInput
        editable={!disabled}
        placeholderTextColor={colors.input.placeholder}
        style={[inputStyles, inputStyle]}
        onFocus={handleFocus}
        onBlur={handleBlur}
        {...rest}
      />
      
      {(error || helperText) && (
        <Text style={helperStyles}>
          {error || helperText}
        </Text>
      )}
    </View>
  );
}

