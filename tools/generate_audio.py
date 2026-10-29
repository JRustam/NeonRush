#!/usr/bin/env python3
"""Original Neon Rush audio. Requires Python 3, NumPy and ffmpeg (authoring only).
No samples, recordings, third-party melodies or external audio inputs are used.
SPDX-License-Identifier: CC0-1.0
"""
from pathlib import Path
import subprocess
import tempfile
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'app/src/main/res/raw'
RNG = np.random.default_rng(8026)

def wav(path, signal, rate):
    signal = np.asarray(signal)
    peak = np.max(np.abs(signal))
    if peak > 0:
        signal = signal * (.88 / peak)
    with wave.open(str(path), 'wb') as f:
        f.setnchannels(1 if signal.ndim == 1 else signal.shape[1])
        f.setsampwidth(2)
        f.setframerate(rate)
        f.writeframes((signal * 32767).astype('<i2').tobytes())

def hz(note):
    return 440 * 2 ** ((note - 69) / 12)

def envelope(t, duration, attack=.008, release=.08):
    return np.minimum(t / attack, 1) * np.clip((duration - t) / release, 0, 1)

def synth(note, duration, rate, bright=False):
    t = np.arange(int(duration * rate)) / rate
    # Finite harmonic additive oscillator; no aliased discontinuous sawtooth.
    value = sum(np.sin(2*np.pi*hz(note)*n*t) / n**(1.1 if bright else 2)
                for n in range(1, 9) if hz(note)*n < rate/2)
    return value * envelope(t, duration) * np.exp(-t * (3 if bright else .4))

def music():
    rate, beat = 32000, .6  # 100 BPM, 16 bars, 38.4-second seamless loop.
    length = int(64 * beat * rate)
    mix = np.zeros((length, 2))
    def add(signal, seconds, gain, pan=0):
        start = int(seconds * rate) % length
        stereo = signal[:, None] * np.array([np.sqrt((1-pan)/2), np.sqrt((1+pan)/2)]) * gain
        first = min(len(signal), length-start)
        mix[start:start+first] += stereo[:first]
        if first < len(signal):
            mix[:len(signal)-first] += stereo[first:]
    roots = [45, 41, 48, 43]
    chords = [(57, 60, 64), (53, 57, 60), (55, 60, 64), (55, 59, 62)]
    phrase = [0, 7, 12, 7, 3, 7, 10, 7, 0, 12, 7, 3, 10, 7, 12, 15]
    for bar in range(16):
        chord = chords[bar // 4]
        base = bar * 4 * beat
        for n, note in enumerate(chord):
            pad = synth(note, 4*beat+.22, rate)
            add(pad, base, .085, [-.65, 0, .65][n])
        for step in range(8):
            add(synth(roots[bar//4], .26, rate, True), base+step*beat/2, .22)
            note = chord[step % 3] + 12
            pluck = synth(note, .24, rate, True)
            add(pluck, base+step*beat/2, .065, .6 if step % 2 else -.6)
            add(pluck, base+step*beat/2+beat*.75, .022, -.6 if step % 2 else .6)
        if bar >= 8:
            for step in range(4):
                note = 69 + phrase[((bar-8)*4+step) % len(phrase)]
                add(synth(note, .42, rate), base+step*beat, .07, .15)
        for b in range(4):
            t = np.arange(int(.32*rate))/rate
            kick = np.sin(2*np.pi*(45*t+85*.026*(1-np.exp(-t/.026)))) * np.exp(-t*16)
            add(kick, base+b*beat, .6)
            if b % 2:
                t = np.arange(int(.2*rate))/rate
                noise = RNG.normal(0, 1, len(t)); noise = np.diff(noise, prepend=0)
                snare = noise*.13*np.exp(-t*25) + np.sin(2*np.pi*185*t)*.2*np.exp(-t*32)
                add(snare, base+b*beat, .7)
        for step in range(8):
            t = np.arange(int(.07*rate))/rate
            noise = RNG.normal(0, 1, len(t)); noise = np.diff(noise, prepend=0)
            add(noise*np.exp(-t*70), base+step*beat/2, .027, -.3 if step%2 else .3)
    # Circular delay preserves reverb tails at the loop boundary.
    mix += np.roll(mix, int(beat*.75*rate), axis=0)[:, ::-1] * .12
    # Remove DC and gently limit the master bus before PCM conversion.
    mix -= np.mean(mix, axis=0)
    mix = np.tanh(mix * 1.15)
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp)/'afterlight.wav'
        wav(source, mix, rate)
        encoders = subprocess.run(['ffmpeg', '-hide_banner', '-encoders'], capture_output=True, text=True, check=True).stdout
        codec = ['-c:a', 'libvorbis'] if 'libvorbis' in encoders else ['-c:a', 'vorbis', '-strict', '-2']
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(source), *codec,
                        '-q:a', '4', '-metadata', 'title=Afterlight Circuit',
                        '-metadata', 'artist=Neon Rush original procedural score',
                        '-metadata', 'copyright=CC0-1.0', str(OUT/'afterlight.ogg')], check=True)

def effects():
    rate = 22050
    def time(duration): return np.arange(int(duration*rate))/rate
    t = time(.7)
    noise = RNG.normal(0, 1, len(t)); high = np.diff(noise, prepend=0)
    crash = high*.15*np.exp(-t*13)
    for i in range(26):
        onset = RNG.uniform(0, .28); local = np.maximum(t-onset, 0)
        crash += (t>=onset)*np.sin(2*np.pi*RNG.uniform(1600, 9300)*local)*np.exp(-local*RNG.uniform(15, 45))*.07
    crash *= envelope(t, .7, .001, .08)
    wav(OUT/'crash.wav', crash, rate)
    t = time(.18)
    zap = np.sin(2*np.pi*(1600*t-1800*t*t)+2*np.sin(2*np.pi*65*t))*np.exp(-t*22)
    wav(OUT/'energy.wav', zap*envelope(t,.18,.002,.03), rate)
    t = time(.24)
    noise = RNG.normal(0,1,len(t)); smooth = np.convolve(noise, np.ones(9)/9, mode='same')
    whoosh = (smooth+.1*np.sin(2*np.pi*(320*t-350*t*t))) * np.sin(np.pi*t/.24)**2
    wav(OUT/'whoosh.wav', whoosh, rate)
    power = np.zeros(int(.6*rate))
    for i, note in enumerate([69, 76, 81, 88]):
        sound = synth(note,.27,rate); start=int(i*.09*rate)
        power[start:start+len(sound)] += sound*.5
    wav(OUT/'power_up.wav', power, rate)
    over = np.zeros(int(1.3*rate))
    for i, note in enumerate([69, 64, 60, 57]):
        sound=synth(note,.55,rate); start=int(i*.2*rate)
        over[start:start+len(sound)] += sound
    wav(OUT/'game_over.wav', over, rate)
    t=time(.065)
    click=np.sin(2*np.pi*(1100*t-1700*t*t))*np.exp(-t*65)*envelope(t,.065,.001,.015)
    wav(OUT/'click.wav', click, rate)

if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    music()
    effects()
    print('Generated original music and six effects in', OUT)
