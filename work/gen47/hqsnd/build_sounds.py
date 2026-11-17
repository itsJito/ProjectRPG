"""(0.4.7) The weather beds and the bird calls cut from real recordings (replacing weatherbeds.py and birdcalls.py, which
synthesised them). Sources (all downloaded into this folder; see sounds/CREDITS.txt for each one's licence):
  BigSoundBank (bigsoundbank.com, CC0) by number: bsb_NNNN.flac/.mp3
  Wikimedia Commons: c_*.ogg/.mp3
Each bed is a seamless loop: the steadiest stretch of the recording (the least swing in loudness, no thunder), its end
crossfaded (equal power) into its start; layered beds are mixed before the loop is closed. Each call is cut round its
loudest burst with short fades. Everything is written as mono 44.1 kHz Ogg Vorbis into the game's sounds folder."""
import os
import numpy as np
import soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
SOUNDS = os.path.join(HERE, '..', '..', '..', 'rpggame', 'src', 'main', 'resources', 'sounds')
SR = 44100


def load(name):
    x, sr = sf.read(os.path.join(HERE, name), always_2d=True)
    m = x.mean(1)
    if sr != SR:
        # resample by linear interpolation onto the output rate (the sources are 44.1 or 48 kHz)
        t = np.arange(int(len(m) * SR / sr)) * (sr / SR)
        m = np.interp(t, np.arange(len(m)), m)
    return m - m.mean()


def band(x, lo=0.0, hi=None):
    """A gentle band-pass over the whole clip (frequency-domain, soft edges)."""
    s = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if lo > 0: g *= 1 / (1 + (lo / np.maximum(f, 1)) ** 2)
    if hi: g *= 1 / (1 + (f / hi) ** 2)
    return np.fft.irfft(s * g, len(x))


def steadiest(x, seconds, avoid_low=False):
    """The start of the stretch of the clip with the least swing in loudness (and, asked, the least thunder)."""
    n = int(seconds * SR); win = SR // 4
    k = len(x) // win
    env = np.sqrt((x[:k * win].reshape(k, win) ** 2).mean(1)) + 1e-9
    db = 20 * np.log10(env)
    low = np.zeros(k)
    if avoid_low:
        lp = band(x, 0, 160)
        low = np.sqrt((lp[:k * win].reshape(k, win) ** 2).mean(1)) / env
    span = n // win
    best, at = 1e9, 0
    for i in range(0, max(1, k - span - 8)):
        seg = db[i:i + span]
        score = seg.std() + (seg.max() - np.median(seg)) * 0.3 + (low[i:i + span].max() * 40 if avoid_low else 0)
        if score < best: best, at = score, i
    return at * win


def loop(x, seconds, fade=1.5):
    """A seamless loop of 'seconds' from the start of x (which must run on 'fade' seconds past it)."""
    n, f = int(seconds * SR), int(fade * SR)
    body = x[:n].copy(); tail = x[n:n + f]
    t = np.linspace(0, np.pi / 2, f)
    body[:f] = body[:f] * np.sin(t) + tail * np.cos(t)
    return body


def stretch(name, seconds, start=None, avoid_low=False, **filt):
    x = load(name)
    if filt: x = band(x, **filt)
    s = steadiest(x, seconds + 2, avoid_low) if start is None else int(start * SR)
    return x[s:s + int((seconds + 2) * SR)]


def norm(x, peak=0.85):
    return x / (np.abs(x).max() + 1e-9) * peak


def level(x, rms_db=-18.0):
    """A bed brought to an even loudness (its average, not its loudest drop), its rare peaks rounded off softly."""
    x = x / (np.sqrt((x ** 2).mean()) + 1e-9) * 10 ** (rms_db / 20)
    return np.tanh(x * 1.1) / 1.1 * 0.85   # headroom: the encoder overshoots sharp drops


def write(rel, x, peak=0.85, bed=False):
    path = os.path.join(SOUNDS, rel)
    y = (level(x) if bed else norm(x, peak)).astype(np.float32)
    # written a block at a time: libsndfile's Vorbis writer falls over on one large write
    with sf.SoundFile(path, 'w', SR, 1, format='OGG', subtype='VORBIS') as out:
        for i in range(0, len(y), 4096): out.write(y[i:i + 4096])
    print(rel, f'{len(x) / SR:.1f}s', os.path.getsize(path) // 1024, 'KB')


def mix(*parts):
    n = min(len(p) for p, _ in parts)
    return sum(norm(p[:n], 1) * g for p, g in parts)


def calls(name, count, length, gap=0.35, pre=0.08, lo=0, hi=None):
    """The loudest separate bursts of a recording, each cut 'length' s from a little before its onset, faded."""
    x = load(name)
    if lo or hi: x = band(x, lo, hi)
    win = SR // 50
    k = len(x) // win
    env = np.sqrt((x[:k * win].reshape(k, win) ** 2).mean(1))
    order = np.argsort(env)[::-1]
    taken, out = [], []
    for i in order:
        t = i * win
        if any(abs(t - u) < (length + gap) * SR for u in taken): continue
        # back to the onset: where it rises from below a third of this peak
        j = i
        while j > 0 and env[j - 1] > env[i] / 3 and (i - j) * win < 0.6 * SR: j -= 1
        s = max(0, j * win - int(pre * SR)); e = min(len(x), s + int(length * SR))
        c = x[s:e].copy()
        f1, f2 = int(0.01 * SR), int(0.12 * SR)
        c[:f1] *= np.linspace(0, 1, f1); c[-f2:] *= np.linspace(1, 0, f2)
        taken.append(t); out.append(c)
        if len(out) == count: break
    return out


if __name__ == '__main__':
    E = 'elements/'
    # rain: a light patter on stone; a heavy downpour (summer rain on a terrace with a storm's thunder-free rain under it)
    write(E + 'rain_light.ogg', loop(stretch('bsb_1289.flac', 20), 20), bed=True)
    heavy = mix((stretch('bsb_1019.flac', 20), 1.0), (stretch('bsb_0740.flac', 20, avoid_low=True), 0.55))
    write(E + 'rain_heavy.ogg', loop(heavy, 20), bed=True)
    # wind: a steady breeze; a gale of strong wind with the whistle of it round corners
    write(E + 'wind_soft.ogg', loop(stretch('bsb_0595.flac', 20), 20), bed=True)
    gale = mix((stretch('bsb_0153.mp3', 20), 1.0), (stretch('bsb_0147.mp3', 20), 0.5))
    write(E + 'wind_gale.ogg', loop(gale, 20), bed=True)
    # a blizzard: strong wind and its higher whistle; a sandstorm: strong wind, dried and brightened (the grains' hiss)
    bliz = mix((stretch('bsb_0152.mp3', 20), 1.0), (stretch('bsb_0155.mp3', 20), 0.45))
    write(E + 'blizzard.ogg', loop(bliz, 20), bed=True)
    sand = mix((stretch('bsb_0154.mp3', 20, lo=900), 1.0), (stretch('bsb_0595.flac', 20, hi=500), 0.45))
    write(E + 'sand.ogg', loop(sand, 20), bed=True)
    # hail on the roofs and the ground
    write(E + 'hail.ogg', loop(stretch('c_Hail_in_Melbourne_19-June-2023.ogg', 14), 14), bed=True)
    # the birds: carrion crows, the rock dove, the turkey vulture
    crows = calls('bsb_3463.flac', 1, 1.2) + calls('bsb_3465.flac', 1, 1.4) + calls('bsb_3464.flac', 1, 1.4)
    for i, c in enumerate(crows, 1): write(f'animal/crow_caw_{i}.ogg', c, 0.9)
    coos = calls('c_Columba_livia_-_Rock_Dove_XC541143.mp3', 1, 3.4, lo=150, hi=2500) + calls('c_Columba_livia_-_Rock_Dove_XC539061.mp3', 1, 3.4, lo=150, hi=2500)
    for i, c in enumerate(coos, 1): write(f'animal/pigeon_coo_{i}.ogg', c, 0.8)
    hiss = calls('c_Turkey_Vulture.ogg', 2, 1.6, gap=1.0)
    for i, c in enumerate(hiss, 1): write(f'animal/vulture_hiss_{i}.ogg', c, 0.85)
