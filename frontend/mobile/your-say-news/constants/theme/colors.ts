/**
 * Enterprise Color System for Your Say News
 * 
 * This file defines the complete color palette for the application.
 * Colors are organized into semantic categories and support both light and dark modes.
 * 
 * Usage:
 *   import { Colors, useThemeColors } from '@/constants/theme';
 *   const colors = useThemeColors();
 *   <View style={{ backgroundColor: colors.background.primary }} />
 */

// ============================================================================
// BRAND COLORS - Core brand identity colors
// ============================================================================

export const BrandColors = {
  // Primary brand color - Used for main actions, links, primary buttons
  primary: {
    50: '#EEF4FF',
    100: '#DCE8FF',
    200: '#B9D1FF',
    300: '#85B0FF',
    400: '#4A85FF',
    500: '#1D5CFF',  // Main brand color
    600: '#0043F5',
    700: '#0034DB',
    800: '#002CB2',
    900: '#00278C',
    950: '#001654',
  },
  
  // Secondary/Accent - Used for highlights, accents, secondary actions
  accent: {
    50: '#FFF7ED',
    100: '#FFEDD5',
    200: '#FED7AA',
    300: '#FDBA74',
    400: '#FB923C',
    500: '#F97316',  // Main accent
    600: '#EA580C',
    700: '#C2410C',
    800: '#9A3412',
    900: '#7C2D12',
    950: '#431407',
  },

  // Tertiary - Used for alternative accents and variety
  tertiary: {
    50: '#F0FDF4',
    100: '#DCFCE7',
    200: '#BBF7D0',
    300: '#86EFAC',
    400: '#4ADE80',
    500: '#22C55E',  // Main tertiary
    600: '#16A34A',
    700: '#15803D',
    800: '#166534',
    900: '#14532D',
    950: '#052E16',
  },
} as const;

// ============================================================================
// SEMANTIC COLORS - Colors with specific meaning/intent
// ============================================================================

export const SemanticColors = {
  success: {
    light: '#22C55E',
    dark: '#4ADE80',
    background: {
      light: '#F0FDF4',
      dark: '#052E16',
    },
    border: {
      light: '#86EFAC',
      dark: '#166534',
    },
  },
  
  warning: {
    light: '#F59E0B',
    dark: '#FBBF24',
    background: {
      light: '#FFFBEB',
      dark: '#451A03',
    },
    border: {
      light: '#FCD34D',
      dark: '#92400E',
    },
  },
  
  error: {
    light: '#EF4444',
    dark: '#F87171',
    background: {
      light: '#FEF2F2',
      dark: '#450A0A',
    },
    border: {
      light: '#FCA5A5',
      dark: '#991B1B',
    },
  },
  
  info: {
    light: '#3B82F6',
    dark: '#60A5FA',
    background: {
      light: '#EFF6FF',
      dark: '#172554',
    },
    border: {
      light: '#93C5FD',
      dark: '#1E40AF',
    },
  },
} as const;

// ============================================================================
// NEUTRAL COLORS - Grays for text, backgrounds, borders
// ============================================================================

export const NeutralColors = {
  white: '#FFFFFF',
  black: '#000000',
  
  // Slate gray palette - cooler, professional tone
  slate: {
    50: '#F8FAFC',
    100: '#F1F5F9',
    150: '#E9EEF4',
    200: '#E2E8F0',
    300: '#CBD5E1',
    400: '#94A3B8',
    500: '#64748B',
    600: '#475569',
    700: '#334155',
    800: '#1E293B',
    850: '#162032',
    900: '#0F172A',
    950: '#020617',
  },
  
  // Zinc gray palette - warmer, modern tone
  zinc: {
    50: '#FAFAFA',
    100: '#F4F4F5',
    150: '#ECECEE',
    200: '#E4E4E7',
    300: '#D4D4D8',
    400: '#A1A1AA',
    500: '#71717A',
    600: '#52525B',
    700: '#3F3F46',
    800: '#27272A',
    850: '#1F1F22',
    900: '#18181B',
    950: '#09090B',
  },
} as const;

// ============================================================================
// THEME-AWARE COLOR TOKENS
// ============================================================================

export type ColorScheme = 'light' | 'dark';

export interface ThemeColors {
  // Background colors
  background: {
    primary: string;
    secondary: string;
    tertiary: string;
    elevated: string;
    overlay: string;
  };
  
  // Surface colors (cards, modals, etc.)
  surface: {
    primary: string;
    secondary: string;
    tertiary: string;
    elevated: string;
    inverse: string;
  };
  
  // Text colors
  text: {
    primary: string;
    secondary: string;
    tertiary: string;
    disabled: string;
    inverse: string;
    link: string;
  };
  
  // Border colors
  border: {
    primary: string;
    secondary: string;
    focus: string;
    error: string;
  };
  
  // Interactive element colors
  interactive: {
    primary: string;
    primaryHover: string;
    primaryPressed: string;
    primaryDisabled: string;
    secondary: string;
    secondaryHover: string;
    secondaryPressed: string;
  };
  
  // Icon colors
  icon: {
    primary: string;
    secondary: string;
    tertiary: string;
    inverse: string;
    brand: string;
  };
  
  // Status/semantic colors
  status: {
    success: string;
    successBackground: string;
    warning: string;
    warningBackground: string;
    error: string;
    errorBackground: string;
    info: string;
    infoBackground: string;
  };
  
  // Brand colors
  brand: {
    primary: string;
    primaryLight: string;
    primaryDark: string;
    accent: string;
    accentLight: string;
  };
  
  // Input/Form colors
  input: {
    background: string;
    border: string;
    borderFocus: string;
    placeholder: string;
    text: string;
  };
  
  // Navigation colors
  navigation: {
    background: string;
    active: string;
    inactive: string;
    indicator: string;
  };
  
  // Skeleton/Loading colors
  skeleton: {
    base: string;
    highlight: string;
  };
}

// ============================================================================
// LIGHT THEME
// ============================================================================

export const LightTheme: ThemeColors = {
  background: {
    primary: NeutralColors.white,
    secondary: NeutralColors.slate[50],
    tertiary: NeutralColors.slate[100],
    elevated: NeutralColors.white,
    overlay: 'rgba(15, 23, 42, 0.5)',
  },
  
  surface: {
    primary: NeutralColors.white,
    secondary: NeutralColors.slate[50],
    tertiary: NeutralColors.slate[100],
    elevated: NeutralColors.white,
    inverse: NeutralColors.slate[900],
  },
  
  text: {
    primary: NeutralColors.slate[900],
    secondary: NeutralColors.slate[600],
    tertiary: NeutralColors.slate[500],
    disabled: NeutralColors.slate[400],
    inverse: NeutralColors.white,
    link: BrandColors.primary[600],
  },
  
  border: {
    primary: NeutralColors.slate[200],
    secondary: NeutralColors.slate[300],
    focus: BrandColors.primary[500],
    error: SemanticColors.error.light,
  },
  
  interactive: {
    primary: BrandColors.primary[500],
    primaryHover: BrandColors.primary[600],
    primaryPressed: BrandColors.primary[700],
    primaryDisabled: BrandColors.primary[300],
    secondary: NeutralColors.slate[100],
    secondaryHover: NeutralColors.slate[200],
    secondaryPressed: NeutralColors.slate[300],
  },
  
  icon: {
    primary: NeutralColors.slate[700],
    secondary: NeutralColors.slate[500],
    tertiary: NeutralColors.slate[400],
    inverse: NeutralColors.white,
    brand: BrandColors.primary[500],
  },
  
  status: {
    success: SemanticColors.success.light,
    successBackground: SemanticColors.success.background.light,
    warning: SemanticColors.warning.light,
    warningBackground: SemanticColors.warning.background.light,
    error: SemanticColors.error.light,
    errorBackground: SemanticColors.error.background.light,
    info: SemanticColors.info.light,
    infoBackground: SemanticColors.info.background.light,
  },
  
  brand: {
    primary: BrandColors.primary[500],
    primaryLight: BrandColors.primary[400],
    primaryDark: BrandColors.primary[700],
    accent: BrandColors.accent[500],
    accentLight: BrandColors.accent[400],
  },
  
  input: {
    background: NeutralColors.white,
    border: NeutralColors.slate[300],
    borderFocus: BrandColors.primary[500],
    placeholder: NeutralColors.slate[400],
    text: NeutralColors.slate[900],
  },
  
  navigation: {
    background: NeutralColors.white,
    active: BrandColors.primary[500],
    inactive: NeutralColors.slate[500],
    indicator: BrandColors.primary[500],
  },
  
  skeleton: {
    base: NeutralColors.slate[200],
    highlight: NeutralColors.slate[100],
  },
};

// ============================================================================
// DARK THEME
// ============================================================================

export const DarkTheme: ThemeColors = {
  background: {
    primary: NeutralColors.slate[950],
    secondary: NeutralColors.slate[900],
    tertiary: NeutralColors.slate[850],
    elevated: NeutralColors.slate[900],
    overlay: 'rgba(0, 0, 0, 0.7)',
  },
  
  surface: {
    primary: NeutralColors.slate[900],
    secondary: NeutralColors.slate[850],
    tertiary: NeutralColors.slate[800],
    elevated: NeutralColors.slate[800],
    inverse: NeutralColors.white,
  },
  
  text: {
    primary: NeutralColors.slate[50],
    secondary: NeutralColors.slate[300],
    tertiary: NeutralColors.slate[400],
    disabled: NeutralColors.slate[600],
    inverse: NeutralColors.slate[900],
    link: BrandColors.primary[400],
  },
  
  border: {
    primary: NeutralColors.slate[800],
    secondary: NeutralColors.slate[700],
    focus: BrandColors.primary[400],
    error: SemanticColors.error.dark,
  },
  
  interactive: {
    primary: BrandColors.primary[500],
    primaryHover: BrandColors.primary[400],
    primaryPressed: BrandColors.primary[600],
    primaryDisabled: BrandColors.primary[800],
    secondary: NeutralColors.slate[800],
    secondaryHover: NeutralColors.slate[700],
    secondaryPressed: NeutralColors.slate[600],
  },
  
  icon: {
    primary: NeutralColors.slate[200],
    secondary: NeutralColors.slate[400],
    tertiary: NeutralColors.slate[500],
    inverse: NeutralColors.slate[900],
    brand: BrandColors.primary[400],
  },
  
  status: {
    success: SemanticColors.success.dark,
    successBackground: SemanticColors.success.background.dark,
    warning: SemanticColors.warning.dark,
    warningBackground: SemanticColors.warning.background.dark,
    error: SemanticColors.error.dark,
    errorBackground: SemanticColors.error.background.dark,
    info: SemanticColors.info.dark,
    infoBackground: SemanticColors.info.background.dark,
  },
  
  brand: {
    primary: BrandColors.primary[500],
    primaryLight: BrandColors.primary[400],
    primaryDark: BrandColors.primary[600],
    accent: BrandColors.accent[500],
    accentLight: BrandColors.accent[400],
  },
  
  input: {
    background: NeutralColors.slate[900],
    border: NeutralColors.slate[700],
    borderFocus: BrandColors.primary[400],
    placeholder: NeutralColors.slate[500],
    text: NeutralColors.slate[50],
  },
  
  navigation: {
    background: NeutralColors.slate[950],
    active: BrandColors.primary[400],
    inactive: NeutralColors.slate[500],
    indicator: BrandColors.primary[400],
  },
  
  skeleton: {
    base: NeutralColors.slate[800],
    highlight: NeutralColors.slate[700],
  },
};

// ============================================================================
// LEGACY COMPATIBILITY - For backward compatibility with existing code
// ============================================================================

export const Colors = {
  light: {
    text: LightTheme.text.primary,
    background: LightTheme.background.primary,
    tint: LightTheme.brand.primary,
    icon: LightTheme.icon.secondary,
    tabIconDefault: LightTheme.navigation.inactive,
    tabIconSelected: LightTheme.navigation.active,
  },
  dark: {
    text: DarkTheme.text.primary,
    background: DarkTheme.background.primary,
    tint: DarkTheme.brand.primary,
    icon: DarkTheme.icon.secondary,
    tabIconDefault: DarkTheme.navigation.inactive,
    tabIconSelected: DarkTheme.navigation.active,
  },
} as const;

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get theme colors based on color scheme
 */
export const getThemeColors = (colorScheme: ColorScheme): ThemeColors => {
  return colorScheme === 'dark' ? DarkTheme : LightTheme;
};

/**
 * Create a transparent version of a color
 */
export const withOpacity = (color: string, opacity: number): string => {
  // Handle hex colors
  if (color.startsWith('#')) {
    const hex = color.replace('#', '');
    const r = parseInt(hex.substring(0, 2), 16);
    const g = parseInt(hex.substring(2, 4), 16);
    const b = parseInt(hex.substring(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${opacity})`;
  }
  // Return as-is for rgba/rgb colors
  return color;
};

