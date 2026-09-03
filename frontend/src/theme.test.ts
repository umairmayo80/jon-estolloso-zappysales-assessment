import { getContrastRatio, type CssVarsTheme, type Palette } from '@mui/material/styles';
import { describe, expect, it } from 'vitest';
import { appTheme } from './theme';

const cssVarsTheme = appTheme as typeof appTheme & CssVarsTheme;

function paletteFor(scheme: 'light' | 'dark'): Palette {
  const colorScheme = cssVarsTheme.colorSchemes[scheme];
  if (!colorScheme) throw new Error(`Missing ${scheme} color scheme`);
  return colorScheme.palette;
}

function expectContrast(foreground: string, background: string, minimum: number) {
  expect(getContrastRatio(foreground, background)).toBeGreaterThanOrEqual(minimum);
}

describe('appTheme color schemes', () => {
  it('provides light and dark palettes through MUI data selectors', () => {
    expect(cssVarsTheme.colorSchemeSelector).toBe('data');
    expect(paletteFor('light').mode).toBe('light');
    expect(paletteFor('dark').mode).toBe('dark');
  });

  it('keeps deep-slate dark text, controls, and focus treatment accessible', () => {
    const dark = paletteFor('dark');

    expect(dark.canvas).toBe('#0F172A');
    expect(dark.background.default).toBe(dark.canvas);
    expect(dark.primary.main).toBe('#60A5FA');

    for (const [foreground, background] of [
      [dark.text.primary, dark.canvas],
      [dark.text.secondary, dark.canvas],
      [dark.text.primary, dark.background.paper],
      [dark.text.secondary, dark.background.paper],
      [dark.primary.contrastText, dark.primary.main],
      [dark.primary.contrastText, dark.primary.light],
      [dark.success.contrastText, dark.success.main],
      [dark.error.contrastText, dark.error.main],
      [dark.warning.contrastText, dark.warning.main],
    ]) expectContrast(foreground, background, 4.5);

    for (const surface of [dark.canvas, dark.background.paper]) {
      expectContrast(dark.primary.main, surface, 3);
    }
  });
});
