E = r'C:\Users\John\Documents\ProjectRPG\theastrattoengine'

def patch(p, pairs):
    s = open(E + p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:70], s.count(a))
        s = s.replace(a, b)
    open(E + p, 'w', encoding='utf-8').write(s)

patch(r'\native\include\astratto\astratto.h', [
    ('''    ASTRATTO_RCMD_WATER_FIELD = 21,   /* astratto_rcmd_water_field + n*n RGBA8: the water's motion round the eye, kept until replaced (API 2.12; was 2.10's per-frame rects) */''',
     '''    ASTRATTO_RCMD_WATER_FIELD = 21,   /* astratto_rcmd_water_field + n*n RGBA8: the water's motion round the eye, kept until replaced (API 2.12; was 2.10's per-frame rects) */
    ASTRATTO_RCMD_PRECIP = 22,        /* astratto_rcmd_precip + count * astratto_precip_layer: this frame's precipitation (API 2.13) */'''),
    ('''typedef struct astratto_rcmd_emitter {''',
     '''/* (2.13) precipitation: the wind and the clock, then each layer - kind (0 rain, 1 snow, 2 hail, 3 dust, 4 splash),
   instances, fall (m/s), size, streak length, tile width, tile height, ground band low and high, wind share, turbulence,
   ground (1: kept in the band over the roof), seed salt, r, g, b, alpha, and the light's scale */
typedef struct astratto_rcmd_precip { astratto_record head; uint32_t count, reserved; float wind_x, wind_z, time, light; } astratto_rcmd_precip;
typedef struct astratto_precip_layer { float kind, instances, fall, size, length, tile, tall, low, high, wind, turbulence, ground, salt, r, g, b, alpha, reserved[3]; } astratto_precip_layer;
typedef struct astratto_rcmd_emitter {'''),
])

patch(r'\native\src\render\renderer.h', [
    ('''            bloomDown_, bloomUp_, lum_, exposure_, tonemap_, copy_, ui_, portrait_, probeCs_, lens_, rain_, focus_;''',
     '''            bloomDown_, bloomUp_, lum_, exposure_, tonemap_, copy_, ui_, portrait_, probeCs_, lens_, rain_, focus_, precip_;'''),
    ('''    void rainPass();''',
     '''    void rainPass();
    /** (2.13) This frame's precipitation layers (20 floats each), its wind and clock, drawn after the temporal pass. */
    void precipPass();
    std::vector<float> precipLayers_;
    float precipWind_[2] = {0, 0}, precipTime_ = 0, precipLight_ = 1;'''),
])

patch(r'\native\src\render\renderer.cpp', [
    ('''{&rain_, "fullscreen.vert", "rain.frag"},''', '''{&rain_, "fullscreen.vert", "rain.frag"}, {&precip_, "precip.vert", "precip.frag"},'''),
    ('''particles_.clear(); emitters_.clear();''', '''particles_.clear(); emitters_.clear(); precipLayers_.clear();'''),
    ('''            case ASTRATTO_RCMD_EMITTER: {''',
     '''            case ASTRATTO_RCMD_PRECIP: {
                astratto_rcmd_precip c;
                if (!readRecord(r, head.bytes, c) || c.count > 8 || head.bytes < sizeof c + static_cast<uint64_t>(c.count) * sizeof(astratto_precip_layer)) { error_ = "invalid precipitation record"; return false; }
                if (!std::isfinite(c.wind_x + c.wind_z + c.time + c.light)) { error_ = "invalid precipitation"; return false; }
                precipWind_[0] = c.wind_x; precipWind_[1] = c.wind_z; precipTime_ = std::fmod(c.time, 4096.0f); precipLight_ = c.light;
                const float* f = reinterpret_cast<const float*>(r + sizeof c);
                for (uint32_t i = 0; i < c.count; i++) {
                    bool ok = true;
                    for (int k = 0; k < 17; k++) ok = ok && std::isfinite(f[i * 20 + k]);
                    if (ok && f[i * 20 + 1] >= 1 && f[i * 20 + 1] <= 65536 && f[i * 20 + 5] > 1) precipLayers_.insert(precipLayers_.end(), f + i * 20, f + i * 20 + 20);
                }
                break;
            }
            case ASTRATTO_RCMD_EMITTER: {'''),
    ('''        rainPass();
    }
    mark(1);''',
     '''        if (precipLayers_.empty()) rainPass();
    }
    if (!precipLayers_.empty() && roofTex_ != 0 && sizeX_ > 0 && lensFbo_ != 0) {
        if (sceneTex != lensTex_) {
            g_.BindFramebuffer(FRAMEBUFFER, lensFbo_);
            g_.Viewport(0, 0, ow_, oh_);
            copy_.use();
            copy_.tex("uTex", 0, TEXTURE_2D, sceneTex);
            fullscreen();
            sceneTex = lensTex_;
        }
        precipPass();
    }
    mark(1);'''),
    ('''// ------------------------------------------------------------------ TAA, bloom, exposure, tone map''',
     '''// (2.13) the precipitation (see precip.vert): every layer one instanced draw of quads, over the resolved image
void Renderer::precipPass() {
    g_.BindFramebuffer(FRAMEBUFFER, lensFbo_);
    g_.Viewport(0, 0, ow_, oh_);
    g_.Disable(DEPTH_TEST);
    g_.Enable(BLEND);
    g_.BlendFunc(ONE, ONE_MINUS_SRC_ALPHA);
    precip_.use();
    precip_.mat4("uPV", pv_.m);
    const float* v = cam_.view.m;
    precip_.f3("uRight", v[0], v[4], v[8]);
    precip_.f3("uUp", v[1], v[5], v[9]);
    precip_.f3("uCam", cam_.x, cam_.y, cam_.z);
    precip_.f2("uWind", precipWind_[0], precipWind_[1]);
    precip_.f2("uWorld", static_cast<float>(sizeX_), static_cast<float>(sizeZ_));
    precip_.f1("uTime", precipTime_);
    precip_.tex("uRoof", 0, TEXTURE_2D, roofTex_);
    precip_.tex("uDepth", 1, TEXTURE_2D, gDepth_);
    precip_.f2("uScreen", static_cast<float>(ow_), static_cast<float>(oh_));
    precip_.f1("uNear", cam_.nearP);
    precip_.f1("uFar", cam_.farP);
    // the light on it: the sky's ambient and a little of the sun's, the fog's colour mixed in (it is seen through it)
    const float L = precipLight_;
    precip_.f3("uLight", (ambient_[0] * 1.4f + sunCol_[0] * 0.12f + fogCol_[0] * 0.3f) * L, (ambient_[1] * 1.4f + sunCol_[1] * 0.12f + fogCol_[1] * 0.3f) * L,
               (ambient_[2] * 1.4f + sunCol_[2] * 0.12f + fogCol_[2] * 0.3f) * L);
    g_.BindVertexArray(fsVao_);
    for (size_t i = 0; i + 20 <= precipLayers_.size(); i += 20) {
        const float* f = precipLayers_.data() + i;
        precip_.f4("uLayer", f[0], f[2], f[3], f[4]);
        precip_.f4("uBox", f[5], f[6], f[7], f[8]);
        precip_.f4("uMotion", f[9], f[10], f[11], f[12]);
        precip_.f4("uColor", f[13], f[14], f[15], f[16]);
        g_.DrawArraysInstanced(TRIANGLE_STRIP, 0, 4, static_cast<GLsizei>(f[1]));
    }
    g_.BindVertexArray(0);
    g_.Disable(BLEND);
}

// ------------------------------------------------------------------ TAA, bloom, exposure, tone map'''),
])

patch(r'\java\src\main\java\astratto\bridge\AstrattoNative.java', [
    ('''RCMD_RIPPLE = 19, RCMD_WATER_FIELD = 21;''', '''RCMD_RIPPLE = 19, RCMD_WATER_FIELD = 21, RCMD_PRECIP = 22;'''),
])
patch(r'\java\src\main\java\astratto\bridge\RenderCommands.java', [
    ('''    /** Kind EMITTER_ATTACHED_FIRE retains the emitter translation for the life of each flame. */''',
     '''    /**
     * (2.13) This frame's precipitation: the wind (m/s), the weather's clock, the light's scale, and its layers - each
     * 20 floats: kind (0 rain, 1 snow, 2 hail, 3 dust, 4 splash), instances, fall, size, streak length, tile width, tile
     * height, ground band low, high, wind share, turbulence, ground (1/0), seed salt, r, g, b, alpha, then three spare.
     */
    public void precip(float windX, float windZ, float time, float light, float[] layers, int count) {
        head(AstrattoNative.RCMD_PRECIP, 8 + 8 + 16 + count * 80); buf.putInt(count).putInt(0);
        buf.putFloat(windX).putFloat(windZ).putFloat(time).putFloat(light);
        for (int i = 0; i < count * 20; i++) buf.putFloat(layers[i]);
    }
    /** Kind EMITTER_ATTACHED_FIRE retains the emitter translation for the life of each flame. */'''),
])
patch(r'\java\src\main\java\astratto\render\Shaders.java', [
    ('''"rain.frag", "focus.frag"};''', '''"rain.frag", "focus.frag", "precip.vert", "precip.frag"};'''),
])
print('ok')
