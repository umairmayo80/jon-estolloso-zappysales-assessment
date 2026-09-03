# Directory Desk Override

This override applies to every authenticated application screen. It takes precedence over the generic MASTER pattern.

## Product intent

An administrator should be able to scan a people directory, open a record, and safely update a profile or address without the interface feeling like a marketing site or a dense operations console.

## Visual direction

- Support both Directory Desk schemes: light uses a `#F8FAFC` canvas, white
  surfaces, `#0F172A` ink, `#2563EB` primary action, and `#E4ECFC` borders;
  deep-slate dark uses a `#0F172A` canvas, `#172033` surfaces, `#F8FAFC` ink,
  `#60A5FA` primary/focus treatment, and `#334155` borders.
- Use Inter throughout. Use 600-700 weights for headings and 400-500 for body/labels.
- Favor clean 1px borders, 8px/12px radii, and shallow elevation only for temporary overlays. Do not use gradients, glass effects, decorative illustrations, or marketing hero sections.
- A thin cobalt vertical spine distinguishes the selected navigation item and selected directory row. Color never supplies the only state cue.

## Color mode and accessibility

- MUI color schemes are selected through its `data` selector, starting from
  the system preference. Components must use semantic palette tokens so both
  schemes receive the appropriate surface, foreground, and border colors.
- Normal text must retain at least 4.5:1 contrast against the canvas and
  raised surfaces. In dark mode, use `#F8FAFC` primary or `#CBD5E1` secondary
  text on `#0F172A` / `#172033` surfaces.
- Focus and non-text controls require at least 3:1 contrast against adjacent
  surfaces. Use a visible 2px `#60A5FA` indicator in dark mode; primary
  dark-mode controls use `#0F172A` label text on `#60A5FA`.
- Success, warning, and destructive states use scheme-appropriate colors with
  an accessible on-color text label, and never rely on color alone.

## Layout and navigation

- At desktop widths, use a 240px left rail, a 64px top bar, and a centered content canvas. At small widths, collapse navigation into a labeled menu in the top bar.
- The directory is the primary workspace: desktop may use a data grid; mobile must use readable person cards rather than a squeezed table.
- The record page has a breadcrumb/back action, identity header, profile section, and addresses section. Route-backed editors use a right drawer on desktop and full-screen route on mobile.
- Preserve filters and scroll on return to the directory. Provide one clear primary action per screen.

## Interaction and accessibility

- Maintain 4.5:1 normal text contrast, visible 2px focus indicators, keyboard-reachable controls, 44px or larger touch targets, and 8px gaps between adjacent controls.
- Use MUI SVG icons only. Icon-only controls need accessible names; decorative icons are hidden from assistive technology.
- Use short 150-200ms opacity/color/transform transitions only; honor `prefers-reduced-motion`.
- Forms use visible labels, helper copy, inline validation after blur, a linked focusable error summary after invalid submit, loading feedback, and an unsaved-change confirmation.
- Reserve layout space for async content with skeletons. Ensure sticky bars and drawers never obscure focused controls.
