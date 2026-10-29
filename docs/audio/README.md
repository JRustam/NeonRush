# Original audio assets

All seven audio files in `app/src/main/res/raw/` were synthesized specifically for Neon Rush by `tools/generate_audio.py`. No third-party samples, recordings, voices, or pre-existing song melodies are included.

| File | Description | Format |
| --- | --- | --- |
| `afterlight.ogg` | **Afterlight Circuit** — original 100 BPM, 16-bar synthwave loop (38.4 seconds), with pulsing bass, arpeggios, pads and drums | Stereo Vorbis, 32 kHz |
| `crash.wav` | Glass-like fragments and a sharp impact | Mono PCM16, 22.05 kHz |
| `energy.wav` | Short descending electric zap | Mono PCM16, 22.05 kHz |
| `whoosh.wav` | Filtered-noise near-miss sweep | Mono PCM16, 22.05 kHz |
| `power_up.wav` | Ascending activation arpeggio | Mono PCM16, 22.05 kHz |
| `game_over.wav` | Descending end-of-run motif | Mono PCM16, 22.05 kHz |
| `click.wav` | Short electronic UI tick | Mono PCM16, 22.05 kHz |

## Reproduce

The checked-in assets are sufficient to build the app. To regenerate them, install Python 3, NumPy, and FFmpeg, then run:

```sh
python3 tools/generate_audio.py
```

The generator has a fixed random seed. It uses `libvorbis` when available, otherwise FFmpeg's native Vorbis encoder; compressed bytes can differ across encoders and versions. Music tails wrap across the loop boundary. Effects stay well below SoundPool's decoded sample limit. Audio is normalized with headroom, and additional playback gain keeps effects controlled.

## Asset permission

The generated audio files and their generator are dedicated to the public domain under **CC0-1.0**. To the extent permitted by law, all copyright and related rights in these original assets are waived. They may be used, copied, modified, and redistributed, including commercially, without attribution. They are provided without warranty. This dedication covers these audio assets and their generator only, not unrelated project code or dependencies.

Canonical license: https://creativecommons.org/publicdomain/zero/1.0/legalcode
