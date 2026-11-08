#!/usr/bin/env python3
"""কী-চাপার সাউন্ড থিমগুলো বানায় (app/src/main/res/raw/key_<theme>_<kind>.wav)।
চালাতে: python3 tools/make_sounds.py   (numpy লাগে)"""
import os, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)

def t(ms): return np.arange(int(SR * ms / 1000)) / SR
def env(n, decay): return np.exp(-np.arange(n) / (SR * decay))
def noise(ms): return rng.uniform(-1, 1, int(SR * ms / 1000))
def lp(x, a):  # সরল low-pass
    y = np.zeros_like(x); s = 0.0
    for i, v in enumerate(x): s += a * (v - s); y[i] = s
    return y
def hp(x, a): return x - lp(x, a)
def sine(f, ms, decay, ph=0.0):
    tt = t(ms); return np.sin(2 * np.pi * f * tt + ph) * env(len(tt), decay)
def sweep(f0, f1, ms, decay):
    tt = t(ms); f = f0 * (f1 / f0) ** (tt / tt[-1]); ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * env(len(tt), decay)
def pad(x, ms=0):
    return np.concatenate([np.zeros(int(SR * ms / 1000)), x])
def mix(*xs):
    n = max(len(x) for x in xs); o = np.zeros(n)
    for x in xs: o[:len(x)] += x
    return o
def save(name, x):
    x = np.concatenate([x, np.zeros(200)])
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
    x[:30] *= np.linspace(0, 1, 30); x[-120:] *= np.linspace(1, 0, 120)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype("<i2").tobytes())

def theme_typewriter():
    def clack(f, body):
        return mix(hp(noise(14), .35) * env(int(SR*.014), .002) * .9,
                   sine(f, 40, .006) * .7, sine(f * 2.7, 25, .004) * .35, sine(body, 30, .008) * .5)
    return {
        "standard": clack(2100, 180),
        "space": mix(clack(1500, 120), pad(sine(110, 60, .02) * .8, 2)),
        "delete": clack(2600, 220),
        "enter": mix(clack(1700, 150), pad(mix(sine(2637, 500, .16) * .8, sine(3950, 400, .1) * .25), 25)),
    }

def theme_mechanical():
    def sw(f, low=0):
        down = mix(hp(noise(8), .3) * env(int(SR*.008), .0015), sine(f, 25, .004) * .6)
        up = pad(mix(hp(noise(6), .35) * env(int(SR*.006), .001) * .6, sine(f * 1.3, 18, .003) * .4), 28)
        return mix(down, up, sine(low, 40, .012) * .6 if low else np.zeros(1))
    return {"standard": sw(3000, 150), "space": sw(1800, 90) * 1.0, "delete": sw(3400, 170), "enter": sw(1500, 80)}

def theme_bubble():
    return {
        "standard": sweep(500, 1400, 55, .018),
        "space": sweep(300, 900, 80, .028),
        "delete": sweep(900, 400, 60, .02),
        "enter": mix(sweep(500, 1500, 70, .02), pad(sweep(700, 1900, 90, .03), 55)),
    }

def theme_drop():
    def d(f0, f1, ms, dec):
        base = sweep(f0, f1, ms, dec)
        return mix(base, pad(base * .35, 90), pad(base * .12, 170))
    return {"standard": d(1500, 700, 60, .02), "space": d(900, 450, 80, .03),
            "delete": d(1100, 500, 60, .02), "enter": d(1800, 800, 90, .04)}

def theme_wood():
    def w(f):
        return mix(lp(noise(20), .25) * env(int(SR*.02), .004) * .7, sine(f, 60, .012), sine(f * 2.3, 30, .006) * .3)
    return {"standard": w(520), "space": w(330), "delete": w(620), "enter": mix(w(420), pad(w(300), 70))}

def theme_retro():
    def sq(f, ms, dec=0.03):
        tt = t(ms); return np.sign(np.sin(2 * np.pi * f * tt)) * env(len(tt), dec) * .6
    return {"standard": sq(1800, 35), "space": sq(1100, 45), "delete": sq(700, 45),
            "enter": mix(sq(1400, 40), pad(sq(2100, 70), 45))}

def theme_classic():
    def c(f, dec): return mix(hp(noise(10), .3) * env(int(SR*.01), .002) * .8, sine(f, 40, dec))
    return {"standard": c(1800, .006), "space": c(900, .01), "delete": c(1300, .007),
            "enter": mix(c(1100, .01), pad(sine(1700, 40, .008) * .6, 30))}

THEMES = {"classic": theme_classic, "typewriter": theme_typewriter, "mechanical": theme_mechanical,
          "bubble": theme_bubble, "drop": theme_drop, "wood": theme_wood, "retro": theme_retro}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn in THEMES.items():
        for kind, x in fn().items():
            save(f"key_{name}_{kind}", x)
    print("done")
