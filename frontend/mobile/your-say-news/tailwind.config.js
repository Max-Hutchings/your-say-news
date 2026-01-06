/**
 * Tailwind CSS Configuration for Your Say News
 * 
 * This configuration integrates with the theme system for consistency
 * between JavaScript styles and Tailwind utility classes.
 * 
 * The theme tokens are duplicated here for NativeWind compatibility,
 * but the source of truth is the TypeScript theme files in constants/theme/
 */

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,jsx,ts,tsx}',
    './components/**/*.{js,jsx,ts,tsx}',
  ],
  presets: [require('nativewind/preset')],
  darkMode: 'class',
  theme: {
    extend: {
      // ======================================================================
      // COLORS - Matches constants/theme/colors.ts
      // ======================================================================
      colors: {
        // Brand Primary
        primary: {
          50: '#EEF4FF',
          100: '#DCE8FF',
          200: '#B9D1FF',
          300: '#85B0FF',
          400: '#4A85FF',
          500: '#1D5CFF',
          600: '#0043F5',
          700: '#0034DB',
          800: '#002CB2',
          900: '#00278C',
          950: '#001654',
          DEFAULT: '#1D5CFF',
        },
        
        // Brand Accent
        accent: {
          50: '#FFF7ED',
          100: '#FFEDD5',
          200: '#FED7AA',
          300: '#FDBA74',
          400: '#FB923C',
          500: '#F97316',
          600: '#EA580C',
          700: '#C2410C',
          800: '#9A3412',
          900: '#7C2D12',
          950: '#431407',
          DEFAULT: '#F97316',
        },
        
        // Brand Tertiary
        tertiary: {
          50: '#F0FDF4',
          100: '#DCFCE7',
          200: '#BBF7D0',
          300: '#86EFAC',
          400: '#4ADE80',
          500: '#22C55E',
          600: '#16A34A',
          700: '#15803D',
          800: '#166534',
          900: '#14532D',
          950: '#052E16',
          DEFAULT: '#22C55E',
        },
        
        // Semantic - Success
        success: {
          light: '#22C55E',
          dark: '#4ADE80',
          DEFAULT: '#22C55E',
        },
        
        // Semantic - Warning
        warning: {
          light: '#F59E0B',
          dark: '#FBBF24',
          DEFAULT: '#F59E0B',
        },
        
        // Semantic - Error
        error: {
          light: '#EF4444',
          dark: '#F87171',
          DEFAULT: '#EF4444',
        },
        
        // Semantic - Info
        info: {
          light: '#3B82F6',
          dark: '#60A5FA',
          DEFAULT: '#3B82F6',
        },
        
        // Background colors
        background: {
          primary: {
            light: '#FFFFFF',
            dark: '#020617',
          },
          secondary: {
            light: '#F8FAFC',
            dark: '#0F172A',
          },
          tertiary: {
            light: '#F1F5F9',
            dark: '#162032',
          },
        },
        
        // Surface colors
        surface: {
          primary: {
            light: '#FFFFFF',
            dark: '#0F172A',
          },
          secondary: {
            light: '#F8FAFC',
            dark: '#162032',
          },
          elevated: {
            light: '#FFFFFF',
            dark: '#1E293B',
          },
        },
        
        // Border colors
        border: {
          primary: {
            light: '#E2E8F0',
            dark: '#1E293B',
          },
          secondary: {
            light: '#CBD5E1',
            dark: '#334155',
          },
        },
      },
      
      // ======================================================================
      // FONT FAMILY - Matches constants/theme/typography.ts
      // ======================================================================
      fontFamily: {
        sans: ['System', 'Roboto', 'sans-serif'],
        mono: ['Menlo', 'monospace'],
      },
      
      // ======================================================================
      // FONT SIZE - Matches constants/theme/typography.ts
      // ======================================================================
      fontSize: {
        '2xs': ['10px', { lineHeight: '14px' }],
        'xs': ['12px', { lineHeight: '16px' }],
        'sm': ['14px', { lineHeight: '20px' }],
        'base': ['16px', { lineHeight: '24px' }],
        'md': ['18px', { lineHeight: '26px' }],
        'lg': ['20px', { lineHeight: '28px' }],
        'xl': ['24px', { lineHeight: '32px' }],
        '2xl': ['28px', { lineHeight: '36px' }],
        '3xl': ['32px', { lineHeight: '40px' }],
        '4xl': ['36px', { lineHeight: '44px' }],
        '5xl': ['42px', { lineHeight: '52px' }],
        '6xl': ['48px', { lineHeight: '60px' }],
        '7xl': ['56px', { lineHeight: '68px' }],
      },
      
      // ======================================================================
      // SPACING - Matches constants/theme/spacing.ts
      // ======================================================================
      spacing: {
        '2xs': '2px',
        'xs': '4px',
        'sm': '8px',
        'md': '12px',
        'base': '16px',
        'lg': '20px',
        'xl': '24px',
        '2xl': '32px',
        '3xl': '40px',
        '4xl': '48px',
        '5xl': '56px',
        '6xl': '64px',
        '7xl': '80px',
        '8xl': '96px',
        '9xl': '128px',
      },
      
      // ======================================================================
      // BORDER RADIUS - Matches constants/theme/effects.ts
      // ======================================================================
      borderRadius: {
        'xs': '2px',
        'sm': '4px',
        'md': '6px',
        'base': '8px',
        'lg': '12px',
        'xl': '16px',
        '2xl': '20px',
        '3xl': '24px',
        '4xl': '32px',
      },
      
      // ======================================================================
      // OPACITY - Matches constants/theme/effects.ts
      // ======================================================================
      opacity: {
        '5': '0.05',
        '10': '0.1',
        '20': '0.2',
        '30': '0.3',
        '40': '0.4',
        '60': '0.6',
        '70': '0.7',
        '80': '0.8',
        '90': '0.9',
        'disabled': '0.5',
        'hover': '0.8',
        'pressed': '0.6',
      },
      
      // ======================================================================
      // Z-INDEX - Matches constants/theme/spacing.ts
      // ======================================================================
      zIndex: {
        'below': '-1',
        'base': '0',
        'raised': '10',
        'navigation': '100',
        'dropdown': '200',
        'sticky': '300',
        'overlay': '400',
        'modal': '500',
        'toast': '600',
        'tooltip': '700',
        'max': '9999',
      },
      
      // ======================================================================
      // ANIMATION - Matches constants/theme/effects.ts
      // ======================================================================
      transitionDuration: {
        'instant': '75ms',
        'fastest': '100ms',
        'fast': '150ms',
        'normal': '200ms',
        'medium': '300ms',
        'slow': '400ms',
        'slower': '500ms',
        'slowest': '700ms',
        'page': '300ms',
        'modal-enter': '250ms',
        'modal-exit': '200ms',
      },
      
      // ======================================================================
      // BOX SHADOW - Matches constants/theme/effects.ts
      // ======================================================================
      boxShadow: {
        'xs': '0 1px 2px rgba(0, 0, 0, 0.05)',
        'sm': '0 1px 3px rgba(0, 0, 0, 0.1)',
        'md': '0 4px 6px rgba(0, 0, 0, 0.1)',
        'lg': '0 8px 12px rgba(0, 0, 0, 0.12)',
        'xl': '0 12px 20px rgba(0, 0, 0, 0.15)',
        '2xl': '0 20px 30px rgba(0, 0, 0, 0.2)',
        'primary': '0 4px 8px rgba(29, 92, 255, 0.3)',
        'accent': '0 4px 8px rgba(249, 115, 22, 0.3)',
        'success': '0 4px 8px rgba(34, 197, 94, 0.3)',
        'error': '0 4px 8px rgba(239, 68, 68, 0.3)',
      },
    },
  },
  plugins: [],
};

