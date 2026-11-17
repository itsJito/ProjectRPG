"""(0.4.8, gamefixes: rain sounded like a small puddle being filled) The rain rebuilt from real recordings.

The 0.4.7 beds were close-miked drops on a slab and in a puddle: every drop near and loud, nothing of the thousands
further off. Now the weather's rain is three things, all recorded:
  - beds of rain falling over a whole landscape (a wash, not drops): light rain in a wood (Bourne Woods, Commons,
    CC BY-SA 4.0, Robert EA Harvey) under rain on leaves (Gravity Sound, Commons, CC BY 4.0); a downpour of that rain on
    leaves with NASA's heavy rain loop (Commons, public domain) and the 0.4.7 summer rain on a terrace beneath (BigSoundBank
    1019, CC0);
  - a bed of rain on the roof over your head for indoors (rain under an umbrella, BigSoundBank 2679, with rain on a
    tarpaulin, 1292; CC0);
  - single drops, cut one by one from recordings of drops (BigSoundBank 1384-1386 drops of water, 1289 rain on concrete,
    1290 rain on a puddle, 1292 rain on a tarpaulin; CC0), which the game scatters round the listener on whatever each
    lands on - stone, water, leaves, a roof (rpg.audio.RainDrops).
Inputs live in this folder and work/gen47/hqsnd. Writes mono 44.1 kHz Ogg Vorbis into the game's sounds folder."""
import os
import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
OLD = os.path.join(HERE, '..', '..', 'gen47', 'hqsnd')
SOUNDS = os.path.join(HERE, '..', '..', '..', 'rpggame', 'src', 'main', 'resources', 'sounds')
SR = 44100


def load(name):
    path = os.path.join(HERE, name)
    if not os.path.exists(path): path = os.path.join(OLD, name)
    x, sr = sf.read(path, always_2d=True)
    m = x.mean(1)
    if sr != SR:
        t = np.arange(int(len(m) * SR / sr)) * (sr / SR)
        m = np.interp(t, np.arange(len(m)), m)
    return m - m.mean()


def band(x, lo=0.0, hi=None):
    s = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if lo > 0: g *= 1 / (1 + (lo / np.maximum(f, 1)) ** 4)
    if hi: g *= 1 / (1 + (f / hi) ** 2)
    return np.fft.irfft(s * g, len(x))


def steadiest(x, seconds):
    n = int(seconds * SR); win = SR // 4
    k = len(x) // win
    env = np.sqrt((x[:k * win].reshape(k, win) ** 2).mean(1)) + 1e-9
    db = 20 * np.log10(env)
    span = n // win
    best, at = 1e9, 0
    for i in range(0, max(1, k - span)):
        seg = db[i:i + span]
        score = seg.std() + (seg.max() - np.median(seg)) * 0.5
        if score < best: best, at = score, i
    return at * win


def piece(name, seconds, **filt):
    x = load(name)
    if filt: x = band(x, **filt)
    need = seconds + 2
    if len(x) < need * SR:   # a short loop: repeat it (crossfaded) to length
        reps = int(np.ceil(need * SR / len(x))) + 1
        x = np.concatenate([x] * reps)
    s = steadiest(x, need)
    return x[s:s + int(need * SR)]


def rms(x): return np.sqrt((x ** 2).mean()) + 1e-12


def mix(*parts):
    n = min(len(p) for p, _ in parts)
    out = np.zeros(n)
    for p, g in parts: out += p[:n] / rms(p[:n]) * g
    return out


def loop(x, seconds, fade=2.0):
    n, f = int(seconds * SR), int(fade * SR)
    body = x[:n].copy(); tail = x[n:n + f]
    t = np.linspace(0, np.pi / 2, f)
    body[:f] = body[:f] * np.sin(t) + tail * np.cos(t)
    return body


def level(x, rms_db=-19.0):
    x = x / rms(x) * 10 ** (rms_db / 20)
    return np.tanh(x * 1.1) / 1.1 * 0.85


def write(rel, x):
    path = os.path.join(SOUNDS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    y = x.astype(np.float32)
    # written a block at a time: libsndfile's Vorbis writer overflows its stack on one large write
    with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as out:
        for i in range(0, len(y), 4096): out.write(y[i:i + 4096])
    print(rel, '%.2fs' % (len(x) / SR), os.path.getsize(path) // 1024, 'KB')


def drops(name, count, lo=150, hi=None, length=0.22, min_gap=0.12, pre=0.004):
    """The cleanest single drops in a recording: onsets standing well clear of what is round them, each cut short with a
    quick fade in, its tail faded out, its level evened."""
    x = band(load(name), lo, hi)
    hop = 128
    k = len(x) // hop
    env = np.sqrt((x[:k * hop].reshape(k, hop) ** 2).mean(1)) + 1e-9
    floor = np.convolve(env, np.ones(200) / 200, mode='same')
    cand = []
    for i in range(4, k - int(length * SR / hop) - 2):
        if env[i] < env[i - 1] or env[i] < env[i + 1]: continue
        rise = env[i] / (env[i - 4:i - 1].mean() + 1e-9)
        if rise < 3: continue
        after = env[i + 1:i + int(length * SR / hop)]
        clean = env[i] / (after[len(after) // 2:].mean() + 1e-9)        # the drop dies away inside its cut
        cand.append((rise * min(clean, 20) * env[i] / floor[i], i))
    cand.sort(reverse=True)
    picked = []
    for score, i in cand:
        if all(abs(i - j) * hop > min_gap * SR for j in picked): picked.append(i)
        if len(picked) >= count: break
    out = []
    for i in sorted(picked):
        a = max(0, i * hop - int(pre * SR)); n = int(length * SR)
        seg = x[a:a + n].copy()
        if len(seg) < n: continue
        fi = int(0.002 * SR); seg[:fi] *= np.linspace(0, 1, fi)
        fo = int(n * 0.6); seg[-fo:] *= np.cos(np.linspace(0, np.pi / 2, fo)) ** 2
        seg = seg / (np.abs(seg).max() + 1e-9) * 0.8
        out.append(seg)
    return out


if __name__ == '__main__':
    E = 'elements/'
    light = mix((piece('c_bourne_woods_rain.mp3', 20, lo=90), 1.0), (piece('c_rain_leaves_gravity.wav', 20, lo=90), 0.55))
    write(E + 'rain_light.ogg', level(loop(light, 20)))
    heavy = mix((piece('c_rain_leaves_gravity.wav', 20, lo=70), 1.0), (piece('c_nasa_raindrops_heavy.mp3', 20, lo=70), 0.8),
                (piece('bsb_1019.flac', 20, lo=200), 0.3))
    write(E + 'rain_heavy.ogg', level(loop(heavy, 20), -18.0))
    roof = mix((piece('bsb_2679.flac', 20, lo=60, hi=7000), 1.0), (piece('bsb_1292.flac', 20, lo=60, hi=6000), 0.7))
    write(E + 'rain_roof.ogg', level(loop(roof, 20), -19.0))
    banks = {
        'stone': [('bsb_1289.flac', 6, dict(lo=300, length=0.16))],
        'water': [('bsb_1384.flac', 3, dict(lo=180, length=0.3)), ('bsb_1385.flac', 3, dict(lo=180, length=0.3)), ('bsb_1290.mp3', 3, dict(lo=250, length=0.2))],
        'leaf':  [('c_rain_leaves_gravity.wav', 6, dict(lo=900, length=0.09, min_gap=0.2))],
        'roof':  [('bsb_1292.flac', 4, dict(lo=150, length=0.2)), ('bsb_1294.flac', 3, dict(lo=150, length=0.2))],
    }
    for kind, sources in banks.items():
        n = 0
        for name, count, kw in sources:
            for seg in drops(name, count, **kw):
                n += 1
                write(E + 'drops/drop_%s_%d.ogg' % (kind, n), seg)
