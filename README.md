# FitTrack

A dark-mode fitness exercise library inspired by [Gymky](https://gymky.app).

## Features

- **16+ exercises** spanning Chest, Back, Legs, Core, Shoulders, Arms, and Cardio
- **Step-by-step descriptions** explaining form and technique for each exercise
- **Embedded YouTube tutorial videos** accessible via the "Watch" button on each card
- **Dark mode UI** — near-black backgrounds, emerald-green accent, smooth card layout
- **Filter & search** — filter by muscle-group category, difficulty level, or free-text search

## Usage

Open `index.html` in any modern browser — no build step required.

```
FitTrack/
├── index.html      # App shell & layout
├── styles.css      # Dark mode stylesheet (Gymky-inspired)
├── exercises.js    # Exercise data (name, muscles, description, YouTube video ID)
└── app.js          # Filtering, search, and modal logic
```

## Screenshots

The app uses a gymky-style dark palette:

| Token | Value | Purpose |
|-------|-------|---------|
| `--bg-base` | `#0d0d0f` | Page background |
| `--bg-card` | `#1e1e24` | Exercise cards |
| `--accent` | `#6ee7b7` | Buttons & highlights (emerald) |
| `--text-primary` | `#f1f1f3` | Main text |

## Adding exercises

Edit `exercises.js` and add a new object to the `exercises` array:

```js
{
  id: 17,
  name: "Cable Row",
  category: "back",          // chest | back | legs | core | shoulders | arms | cardio
  muscles: ["Lats", "Biceps"],
  difficulty: "beginner",    // beginner | intermediate | advanced
  sets: "3",
  reps: "12–15",
  description: "...",
  videoId: "YouTube_video_ID",
}
```
