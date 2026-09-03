# UI design — Directory Desk

## Design source and selected direction

The reviewable visual source of truth is
[`design-system/profile-directory/MASTER.md`](../design-system/profile-directory/MASTER.md).
It was created using the requested UI/UX design guidance and is not regenerated
or overwritten during implementation.

Three static mockups are provided for review:

- `design/mockups/directory-desk.html` — **selected**
- `design/mockups/operations-workbench.html` — alternate dense operations view
- `design/mockups/profile-ledger.html` — alternate profile-first view

Directory Desk is a light, precise B2B workspace: fog-white canvas, cobalt
active-state spine, high-legibility directory, and a focused profile/address
workspace. It prioritizes scanning and correction over decorative treatment.

## Visual system

| Token | Value / rule |
| --- | --- |
| Canvas | `#F8FAFC` |
| Surfaces | white with subtle `#E4ECFC` borders |
| Text | slate `#0F172A`, muted `#475569` |
| Primary action/focus | cobalt `#2563EB` |
| Success | restrained green `#059669`, always paired with text/icon |
| Destructive | red `#DC2626`, visually separated from primary actions |
| Typeface | Inter, 16px minimum body text, 1.5+ line-height |
| Rhythm | 4px/8px base scale; 16/24/32px common section spacing |
| Icons | one SVG icon family; icon-only controls have accessible names |
| Motion | 150–300ms opacity/transform transitions; no layout-shifting hover; respect reduced motion |

MUI semantic theme tokens implement these values. Components do not hard-code
arbitrary hex values. Elevation is intentionally restrained; no decorative
gradients, emoji icons, glass effects, or large animated elements are used.

## Route and interaction model

| Route | Purpose | Desktop pattern | Mobile pattern |
| --- | --- | --- | --- |
| `/login` | administrator sign-in | centered secure card | full-width focused form |
| `/users` | searchable directory | Data Grid with toolbar | accessible profile cards |
| `/users/new` | create profile | route-backed drawer | full-screen form route |
| `/users/:userId` | profile and address management | detail workspace | stacked detail view |
| `/users/:userId/edit` | edit profile | drawer | full-screen form route |
| `/users/:userId/addresses/new` | add address | drawer | full-screen form route |
| `/users/:userId/addresses/:addressId/edit` | edit address | drawer | full-screen form route |

The directory's `query`, `status`, `sort`, and `page` live in the URL. Query
state and scroll position are retained when returning from a profile/editor.
The desktop grid is never forced onto a narrow screen; mobile changes to cards
that expose the same primary actions.

## Form, feedback, and accessibility rules

- Use visible labels, required indicators, descriptive helper text, and
  semantic email/text controls; placeholders never replace labels.
- Validate on blur and submit. Keep field-level errors next to their controls;
  after a failed multi-error submit, move focus to a linked error summary.
- Disable/save buttons during asynchronous submission and announce success or
  failure via a non-focus-stealing live region.
- Ask for confirmation before soft delete. A `412` concurrency result preserves
  the draft and offers a clear refresh/retry path.
- Keep keyboard focus visible and unobscured by sticky bars/drawers; restore
  focus to the triggering control on close and move focus to main content after
  a route transition.
- Provide 44px+ touch targets with at least 8px spacing. Do not rely on hover,
  dragging, swipe, or color alone for a meaningful action/state.
- Meet 4.5:1 normal-text contrast and 3:1 non-text-control contrast. Respect
  `prefers-reduced-motion`; the app's light theme is the v1 shipped theme,
  while any future dark theme must be independently contrast-tested.

## Responsive acceptance criteria

| Width | Expected behavior |
| --- | --- |
| 390px | single-column login/detail/forms; cards replace grid; no horizontal scroll |
| 768px | wider cards and balanced gutters; navigation remains discoverable |
| 1024px | directory toolbar and table controls fit without truncating essentials |
| 1440px | sidebar/workspace hierarchy, full Data Grid, route-backed editor drawer |

Before release, test a keyboard-only path, screen-reader labels for all
standalone icons, slow-network skeleton/error states, 200% zoom, and reduced
motion. The frontend test suite and mockup review make these checks repeatable.
