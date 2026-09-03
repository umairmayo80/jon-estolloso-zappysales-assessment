# Directory Desk Override

This override applies to every authenticated application screen. It takes precedence over the generic MASTER pattern.

## Product intent

An administrator should be able to scan a people directory, open a record, and safely update a profile or address without the interface feeling like a marketing site or a dense operations console.

## Visual direction

- Use the light Directory Desk workspace: `#F8FAFC` canvas, white surfaces, `#0F172A` ink, `#2563EB` primary action, `#059669` success, `#DC2626` destructive, and `#E4ECFC` borders.
- Use Inter throughout. Use 600-700 weights for headings and 400-500 for body/labels.
- Favor clean 1px borders, 8px/12px radii, and shallow elevation only for temporary overlays. Do not use gradients, glass effects, decorative illustrations, or marketing hero sections.
- A thin cobalt vertical spine distinguishes the selected navigation item and selected directory row. Color never supplies the only state cue.

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
