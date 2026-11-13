# Chanting Buddy promo clip

A 17-second vertical promo (1080×1920, 30 fps) for the v1.4.2 release:
cycles the four scenes (Monk / Lotus / Temple dawn / Abstract light) with
their names, shows the Calm / Minimal / Off motion-level switch, and ends on
"Chanting Buddy — it just chants. Free on GitHub." plus the Ko-fi line.

## Render

```bash
npm install
npx remotion render src/index.ts Promo out/promo.mp4 --codec h264
npx remotion render src/index.ts Promo out/promo.gif --codec gif
```

Rendered output is committed under `docs/media/`
(`chanting-buddy-v1.4.2.mp4` + the GIF embedded in the README).

## Fonts

Uses **Urbanist** (bundled copy in `public/urbanist.ttf`), licensed under the
SIL Open Font License 1.1 — the same font the app ships. See the app repo's
LICENSES.md.

## Notes

- `node_modules/` and `out/` are gitignored; this folder is not part of the
  Android build.
- Colors match the app theme (Summit palette).
