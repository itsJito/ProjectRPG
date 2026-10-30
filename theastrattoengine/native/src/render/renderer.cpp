// Astratto Engine - the renderer (2.0). See renderer.h. The passes below follow the Java renderer they replace
// (absentialis.render.HybridRenderer, WorldRenderer, ProbeField, VoxelVolume, astratto.render.ParticleBatch and Batch2D)
// call for call; where a comment says why something is done, it is that renderer's reason, kept.
#include "render/renderer.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "core/engine.h"
#include "world/voxel_world.h"

namespace astratto {

using namespace gl;

namespace {

float sstep(float a, float b, float x) { float t = std::max(0.0f, std::min(1.0f, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
float lin(float c) { return static_cast<float>(std::pow(std::max(0.0f, c), 2.2)); }

const float HALTON_X[8] = {0.5f, 0.25f, 0.75f, 0.125f, 0.625f, 0.375f, 0.875f, 0.0625f};
const float HALTON_Y[8] = {0.3333f, 0.6667f, 0.1111f, 0.4444f, 0.7778f, 0.2222f, 0.5556f, 0.8889f};

/** java.util.Random, exactly: the probes' ray rotations come from the same sequence as the Java renderer's. */
struct JavaRandom {
    uint64_t seed;
    explicit JavaRandom(uint64_t s) : seed((s ^ 0x5DEECE66DULL) & ((1ULL << 48) - 1)) {}
    int next(int bits) { seed = (seed * 0x5DEECE66DULL + 0xBULL) & ((1ULL << 48) - 1); return static_cast<int>(static_cast<int64_t>(seed) >> (48 - bits)); }
    double nextDouble() { return static_cast<double>((static_cast<int64_t>(next(26)) << 27) + next(27)) * (1.0 / static_cast<double>(1ULL << 53)); }
};

/** Resolves #include "x" lines against the sources given by name (Java hands them over resolved already). */
std::string resolveIncludes(const std::string& src, const std::unordered_map<std::string, std::string>& all, int depth) {
    if (depth > 8 || src.find("#include \"") == std::string::npos) return src;
    std::string out;
    size_t at = 0;
    while (at < src.size()) {
        size_t end = src.find('\n', at);
        size_t next = end == std::string::npos ? src.size() : end + 1;
        std::string line = src.substr(at, (end == std::string::npos ? src.size() : end) - at);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.rfind("#include \"", 0) == 0 && line.size() > 11 && line.back() == '"') {
            auto it = all.find(line.substr(10, line.size() - 11));
            if (it != all.end()) { out += resolveIncludes(it->second, all, depth + 1); at = next; continue; }
        }
        out.append(src, at, next - at);
        at = next;
    }
    return out;
}

/** The world's size, for the shaders that trace through the voxel volume and the probe grid: defined after #version. */
std::string withWorld(const std::string& src, int sx, int sy, int sz, float ground) {
    size_t nl = src.find('\n');
    if (src.rfind("#version", 0) != 0 || nl == std::string::npos) return src;
    char defs[192];
    std::snprintf(defs, sizeof defs, "#define WORLD_X %d\n#define WORLD_Y %d\n#define WORLD_Z %d\n#define GROUND_Y %.4f\n", sx, sy, sz, static_cast<double>(ground));
    return src.substr(0, nl + 1) + defs + src.substr(nl + 1);
}

}  // namespace

// ------------------------------------------------------------------ the GL function table
bool gl::Api::load(void* (*loader)(const char*), const char** missing) {
#define ASTRATTO_GL_LOAD(ret, name, params) \
    name = reinterpret_cast<ret (ASTRATTO_GLAPI*) params>(loader("gl" #name)); \
    if (name == nullptr) { if (missing) *missing = "gl" #name; return false; }
    ASTRATTO_GL_FUNCTIONS(ASTRATTO_GL_LOAD)
#undef ASTRATTO_GL_LOAD
    return true;
}

// ------------------------------------------------------------------ programs
namespace {
GLuint compileStage(const Api& g, GLenum type, const std::string& src, std::string& error) {
    GLuint s = g.CreateShader(type);
    const char* p = src.c_str();
    g.ShaderSource(s, 1, &p, nullptr);
    g.CompileShader(s);
    GLint ok = 0;
    g.GetShaderiv(s, COMPILE_STATUS, &ok);
    if (!ok) {
        GLint n = 0;
        g.GetShaderiv(s, INFO_LOG_LENGTH, &n);
        std::string log(static_cast<size_t>(std::max(1, n)), '\0');
        g.GetShaderInfoLog(s, n, nullptr, &log[0]);
        error = "shader compile: " + log;
        g.DeleteShader(s);
        return 0;
    }
    return s;
}
bool linkProgram(const Api& g, GLuint p, std::string& error) {
    g.LinkProgram(p);
    GLint ok = 0;
    g.GetProgramiv(p, LINK_STATUS, &ok);
    if (ok) return true;
    GLint n = 0;
    g.GetProgramiv(p, INFO_LOG_LENGTH, &n);
    std::string log(static_cast<size_t>(std::max(1, n)), '\0');
    g.GetProgramInfoLog(p, n, nullptr, &log[0]);
    error = "link: " + log;
    return false;
}
}  // namespace

bool Program::build(const Api& g, const std::string& vs, const std::string& fs, std::string& error) {
    g_ = &g;
    GLuint v = compileStage(g, VERTEX_SHADER, vs, error);
    if (!v) return false;
    GLuint f = compileStage(g, FRAGMENT_SHADER, fs, error);
    if (!f) { g.DeleteShader(v); return false; }
    id_ = g.CreateProgram();
    g.AttachShader(id_, v);
    g.AttachShader(id_, f);
    bool ok = linkProgram(g, id_, error);
    g.DeleteShader(v);
    g.DeleteShader(f);
    locs_.clear();
    return ok;
}

bool Program::build(const Api& g, const std::string& cs, std::string& error) {
    g_ = &g;
    GLuint c = compileStage(g, COMPUTE_SHADER, cs, error);
    if (!c) return false;
    id_ = g.CreateProgram();
    g.AttachShader(id_, c);
    bool ok = linkProgram(g, id_, error);
    g.DeleteShader(c);
    locs_.clear();
    return ok;
}

void Program::destroy(const Api& g) { if (id_) g.DeleteProgram(id_); id_ = 0; locs_.clear(); }
void Program::use() const { g_->UseProgram(id_); }

gl::GLint Program::loc(const char* name) {
    auto it = locs_.find(name);
    if (it != locs_.end()) return it->second;
    GLint l = g_->GetUniformLocation(id_, name);
    locs_.emplace(name, l);
    return l;
}

void Program::tex(const char* n, int unit, GLenum target, GLuint t) {
    g_->ActiveTexture(TEXTURE0 + static_cast<GLenum>(unit));
    g_->BindTexture(target, t);
    g_->Uniform1i(loc(n), unit);
}

// ------------------------------------------------------------------ making it
Renderer::~Renderer() {
    if (g_.DeleteTextures == nullptr) return;
    if (timersMade_) g_.DeleteQueries(TIMER_FRAMES * TIMER_STAMPS, &timerQ_[0][0]);
    for (auto& f : fences_) if (f != nullptr) g_.DeleteSync(f);
    freeWorld();
    disposeTargets();
    for (auto& kv : textures_) g_.DeleteTextures(1, &kv.second.id);
    for (auto& kv : meshes_) { g_.DeleteBuffers(1, &kv.second.vbo); g_.DeleteVertexArrays(1, &kv.second.vao); }
    for (auto& gm : graveyard_) { g_.DeleteBuffers(1, &gm.second.vbo); g_.DeleteVertexArrays(1, &gm.second.vao); }
    for (auto& kv : layerMeshes_) freeLayer(kv.second);
    if (decalVbo_) g_.DeleteBuffers(1, &decalVbo_);
    if (decalVao_) g_.DeleteVertexArrays(1, &decalVao_);
    Program* all[] = {&gWorld_, &gModel_, &sWorld_, &sModel_, &lighting_, &cull_, &gtao_, &aoBlur_, &ssr_, &vol_, &fogApply_, &fWorld_, &fModel_,
                      &particle_, &chemistryParticles_, &waterWaves_, &taa_, &bloomDown_, &bloomUp_, &lum_, &exposure_, &tonemap_, &copy_, &ui_, &portrait_, &probeCs_, &folG_, &folS_, &propG_, &propS_, &decal_, &lens_, &leafBake_};
    for (Program* p : all) p->destroy(g_);
    if (leafTex_) g_.DeleteTextures(1, &leafTex_);
    GLuint t[] = {lumTex_, expoTex_[0], expoTex_[1], layerMat_, white_, shadowTex_, paletteTex_, water_[0], water_[1], waterMask_};
    for (GLuint x : t) if (x) g_.DeleteTextures(1, &x);
    GLuint f[] = {lumFbo_, expoFbo_[0], expoFbo_[1]};
    for (GLuint x : f) if (x) g_.DeleteFramebuffers(1, &x);
    if (shadowTex_) g_.DeleteFramebuffers(4, shadowFbo_);
    GLuint b[] = {lightBuf_, tileBuf_, ebo_, particleVbo_, quadVbo_, quadEbo_, chemistryVbo_, chemistryState_, chemistrySources_};
    for (GLuint x : b) if (x) g_.DeleteBuffers(1, &x);
    GLuint a[] = {fsVao_, particleVao_, quadVao_, chemistryVao_};
    for (GLuint x : a) if (x) g_.DeleteVertexArrays(1, &x);
}

void Renderer::preset(int level) {
    presetLevel_ = std::max(0, std::min(3, level));
    switch (presetLevel_) {
        case 0: q_.fogDiv = 4; q_.shadowRes = 1024; q_.cascades = 3; q_.shadowedLights = 0; q_.probeBudget = 3; q_.probeRays = 16; q_.volumetricSteps = 8; q_.maxLights = 48; q_.shadowDistance = 100; break;
        case 1: q_.fogDiv = 4; q_.shadowRes = 2048; q_.cascades = 3; q_.shadowedLights = 1; q_.probeBudget = 5; q_.probeRays = 24; q_.volumetricSteps = 8; q_.maxLights = 80; q_.shadowDistance = 140; break;
        case 2: q_.fogDiv = 4; q_.shadowRes = 2048; q_.cascades = 4; q_.shadowedLights = 2; q_.probeBudget = 8; q_.probeRays = 32; q_.volumetricSteps = 14; q_.maxLights = 128; q_.shadowDistance = 150; break;
        default: q_.fogDiv = 2; q_.shadowRes = 4096; q_.cascades = 4; q_.shadowedLights = 6; q_.probeBudget = 20; q_.probeRays = 64; q_.volumetricSteps = 24; q_.maxLights = 224; q_.shadowDistance = 240; break;
    }
}

bool Renderer::create(const astratto_renderer_desc& d, const std::unordered_map<std::string, std::string>& sources) {
    if (d.loader == nullptr) { error_ = "no GL loader"; return false; }
    const char* missing = nullptr;
    if (!g_.load(reinterpret_cast<void* (*)(const char*)>(d.loader), &missing)) { error_ = std::string("the GL context has no ") + (missing ? missing : "?") + " (OpenGL 4.5 is needed)"; return false; }
    GLint major = 0, minor = 0;
    g_.GetIntegerv(MAJOR_VERSION, &major);
    g_.GetIntegerv(MINOR_VERSION, &minor);
    if (major < 4 || (major == 4 && minor < 5)) { error_ = "the current GL context is " + std::to_string(major) + "." + std::to_string(minor) + ": OpenGL 4.5 is needed"; return false; }
    sources_ = sources;
    rng_ = JavaRandom(7).seed;
    preset(static_cast<int>(d.quality));
    q_.renderScale = d.render_scale > 0 ? d.render_scale : 1.0f;
    // World-dependent shaders compile once, for the actual world on its first frame. UI-only frames need none of them.
    sizeX_ = 0;
    auto src = [&](const char* n) -> std::string {
        auto it = sources_.find(n);
        return it == sources_.end() ? std::string() : withWorld(resolveIncludes(it->second, sources_, 0), 64, 64, 64, groundY_);
    };
    struct P { Program* p; const char* vs; const char* fs; };
    const P list[] = {{&gWorld_, "world.vert", "world_gbuffer.frag"}, {&gModel_, "model.vert", "model_gbuffer.frag"}, {&sWorld_, "world.vert", "world_shadow.frag"},
                      {&sModel_, "model.vert", "model_shadow.frag"}, {&lighting_, "fullscreen.vert", "lighting.frag"}, {&gtao_, "fullscreen.vert", "gtao.frag"},
                      {&aoBlur_, "fullscreen.vert", "ao_blur.frag"}, {&ssr_, "fullscreen.vert", "ssr.frag"}, {&vol_, "fullscreen.vert", "volumetric.frag"},
                      {&fogApply_, "fullscreen.vert", "fog_apply.frag"}, {&fWorld_, "world.vert", "world_forward.frag"}, {&fModel_, "model.vert", "model_forward.frag"},
                      {&particle_, "particle.vert", "particle.frag"}, {&taa_, "fullscreen.vert", "taa.frag"}, {&bloomDown_, "fullscreen.vert", "bloom_down.frag"},
                      {&bloomUp_, "fullscreen.vert", "bloom_up.frag"}, {&lum_, "fullscreen.vert", "luminance.frag"}, {&exposure_, "fullscreen.vert", "exposure.frag"},
                      {&tonemap_, "fullscreen.vert", "tonemap.frag"}, {&copy_, "fullscreen.vert", "copy.frag"}, {&ui_, "ui2d.vert", "ui2d.frag"}, {&portrait_, "model.vert", "portrait.frag"},
                      {&folG_, "foliage.vert", "foliage_gbuffer.frag"}, {&folS_, "foliage.vert", "foliage_shadow.frag"},
                      {&propG_, "props.vert", "props_gbuffer.frag"}, {&propS_, "props.vert", "props_shadow.frag"}, {&decal_, "decal.vert", "decal.frag"},
                      {&lens_, "fullscreen.vert", "lens.frag"}, {&leafBake_, "fullscreen.vert", "leaf_bake.frag"}};
    for (const P& p : list) {
        if (p.p == &gWorld_ || p.p == &lighting_ || p.p == &vol_ || p.p == &fogApply_ || p.p == &fWorld_ || p.p == &fModel_) continue;
        std::string vs = src(p.vs), fs = src(p.fs);
        if (vs.empty() || fs.empty()) { error_ = std::string("no shader source handed over for ") + (vs.empty() ? p.vs : p.fs); return false; }
        if (!p.p->build(g_, vs, fs, error_)) { error_ = std::string(p.vs) + " + " + p.fs + ": " + error_; return false; }
    }
    const P comp[] = {{&cull_, "light_cull.comp", nullptr}, {&chemistryParticles_, "chemistry_particles.comp", nullptr}, {&waterWaves_, "water_waves.comp", nullptr}};
    for (const P& p : comp) {
        std::string cs = src(p.vs);
        if (cs.empty()) { error_ = std::string("no shader source handed over for ") + p.vs; return false; }
        if (!p.p->build(g_, cs, error_)) { error_ = std::string(p.vs) + ": " + error_; return false; }
    }
    g_.GenVertexArrays(1, &fsVao_);
    bakeLeaves();
    g_.GenBuffers(1, &lightBuf_);
    g_.GenBuffers(1, &tileBuf_);
    // the quad index buffer every column draws with
    {
        std::vector<uint32_t> ib(static_cast<size_t>(MAX_QUADS) * 6);
        for (uint32_t qd = 0; qd < static_cast<uint32_t>(MAX_QUADS); qd++) { uint32_t b = qd * 4; uint32_t* o = &ib[qd * 6]; o[0] = b; o[1] = b + 1; o[2] = b + 2; o[3] = b + 2; o[4] = b + 3; o[5] = b; }
        g_.GenBuffers(1, &ebo_);
        g_.BindBuffer(ELEMENT_ARRAY_BUFFER, ebo_);
        g_.BufferData(ELEMENT_ARRAY_BUFFER, static_cast<GLsizeiptr>(ib.size() * 4), ib.data(), STATIC_DRAW);
    }
    // particles: one instanced stream
    g_.GenVertexArrays(1, &particleVao_);
    g_.GenBuffers(1, &particleVbo_);
    g_.BindVertexArray(particleVao_);
    g_.BindBuffer(ARRAY_BUFFER, particleVbo_);
    particleCap_ = 4096;
    g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(particleCap_ * 48), nullptr, STREAM_DRAW);
    for (GLuint i = 0; i < 3; i++) {
        g_.VertexAttribPointer(i, 4, FLOAT, FALSE_, 48, reinterpret_cast<const void*>(static_cast<uintptr_t>(i * 16)));
        g_.EnableVertexAttribArray(i);
        g_.VertexAttribDivisor(i, 1);
    }
    // 2D quads
    water_[0]=tex2D(RGBA16F,256,256,LINEAR,1);water_[1]=tex2D(RGBA16F,256,256,LINEAR,1);waterMask_=tex2D(RGBA16F,256,256,NEAREST,1);
    for(GLuint texture:{water_[0],water_[1],waterMask_})g_.ClearTexImage(texture,0,RGBA,FLOAT,nullptr);
    // Chemical motion stays on the GPU; the CPU uploads only a bounded set of emitters.
    g_.GenVertexArrays(1, &chemistryVao_); g_.GenBuffers(1, &chemistryVbo_); g_.GenBuffers(1, &chemistryState_); g_.GenBuffers(1, &chemistrySources_);
    std::vector<float> empty(4096 * 16, 0);
    g_.BindVertexArray(chemistryVao_); g_.BindBuffer(ARRAY_BUFFER, chemistryVbo_);
    g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(4096*48), empty.data(), DYNAMIC_COPY);
    for (GLuint i = 0; i < 3; ++i) { g_.VertexAttribPointer(i,4,FLOAT,FALSE_,48,reinterpret_cast<const void*>(static_cast<uintptr_t>(i*16))); g_.EnableVertexAttribArray(i); g_.VertexAttribDivisor(i,1); }
    g_.BindBuffer(SHADER_STORAGE_BUFFER, chemistryState_); g_.BufferData(SHADER_STORAGE_BUFFER, static_cast<GLsizeiptr>(4096*64), empty.data(), DYNAMIC_COPY);
    g_.BindBuffer(SHADER_STORAGE_BUFFER, chemistrySources_); g_.BufferData(SHADER_STORAGE_BUFFER, 64*80, nullptr, STREAM_DRAW);
    g_.GenVertexArrays(1, &quadVao_);
    g_.GenBuffers(1, &quadVbo_);
    g_.GenBuffers(1, &quadEbo_);
    g_.BindVertexArray(quadVao_);
    g_.BindBuffer(ARRAY_BUFFER, quadVbo_);
    quadCap_ = 8192;
    g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(quadCap_ * 4 * 36), nullptr, DYNAMIC_DRAW);
    g_.VertexAttribPointer(0, 2, FLOAT, FALSE_, 36, reinterpret_cast<const void*>(0));
    g_.VertexAttribPointer(1, 2, FLOAT, FALSE_, 36, reinterpret_cast<const void*>(8));
    g_.VertexAttribPointer(2, 4, FLOAT, FALSE_, 36, reinterpret_cast<const void*>(16));
    g_.VertexAttribPointer(3, 1, FLOAT, FALSE_, 36, reinterpret_cast<const void*>(32));
    for (GLuint i = 0; i < 4; i++) g_.EnableVertexAttribArray(i);
    g_.BindBuffer(ELEMENT_ARRAY_BUFFER, ebo_);
    g_.BindVertexArray(0);
    // a white texel: what an untextured quad samples
    g_.GenTextures(1, &white_);
    g_.BindTexture(TEXTURE_2D, white_);
    const uint8_t px[4] = {255, 255, 255, 255};
    g_.TexImage2D(TEXTURE_2D, 0, static_cast<GLint>(RGBA8), 1, 1, 0, RGBA, UNSIGNED_BYTE, px);
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, static_cast<GLint>(NEAREST));
    // exposure and luminance targets do not depend on the window
    lumTex_ = tex2D(R16F, 256, 256, LINEAR_MIPMAP_NEAREST, 9);
    lumFbo_ = fbo(lumTex_, 0);
    for (int i = 0; i < 2; i++) { expoTex_[i] = tex2D(R16F, 1, 1, NEAREST, 1); expoFbo_[i] = fbo(expoTex_[i], 0); }
    resize(static_cast<int>(d.width), static_cast<int>(d.height));
    lights_.reserve(MAX_LIGHTS * 12);
    return error_.empty();
}

// ------------------------------------------------------------------ resources
GLuint Renderer::tex2D(GLenum format, int w, int h, GLenum filter, int levels) {
    GLuint t = 0;
    g_.GenTextures(1, &t);
    g_.BindTexture(TEXTURE_2D, t);
    g_.TexStorage2D(TEXTURE_2D, levels, format, std::max(1, w), std::max(1, h));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, static_cast<GLint>(filter));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, static_cast<GLint>(filter == NEAREST ? NEAREST : LINEAR));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
    return t;
}

GLuint Renderer::fbo(GLuint color, GLuint depth) {
    GLuint f = 0;
    g_.GenFramebuffers(1, &f);
    g_.BindFramebuffer(FRAMEBUFFER, f);
    g_.FramebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, color, 0);
    if (depth) g_.FramebufferTexture2D(FRAMEBUFFER, DEPTH_ATTACHMENT, TEXTURE_2D, depth, 0);
    if (g_.CheckFramebufferStatus(FRAMEBUFFER) != FRAMEBUFFER_COMPLETE) error_ = "incomplete framebuffer";
    return f;
}

void Renderer::resize(int w, int h) {
    w = std::max(16, w); h = std::max(16, h);
    if (w == winW_ && h == winH_ && appliedScale_ == q_.renderScale && appliedFogDiv_ == q_.fogDiv) return;
    if (gFbo_) disposeTargets();
    winW_ = w; winH_ = h; appliedScale_ = q_.renderScale; appliedFogDiv_ = q_.fogDiv;
    rw_ = std::max(16, static_cast<int>(std::lround(w * q_.renderScale))); rh_ = std::max(16, static_cast<int>(std::lround(h * q_.renderScale)));
    gAlbedo_ = tex2D(RGBA8, rw_, rh_, NEAREST, 1);
    gNormal_ = tex2D(RGB10_A2, rw_, rh_, NEAREST, 1);
    gMat_ = tex2D(RGBA8, rw_, rh_, NEAREST, 1);
    gDepth_ = tex2D(DEPTH_COMPONENT32F, rw_, rh_, NEAREST, 1);
    g_.GenFramebuffers(1, &gFbo_);
    g_.BindFramebuffer(FRAMEBUFFER, gFbo_);
    g_.FramebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, gAlbedo_, 0);
    g_.FramebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT1, TEXTURE_2D, gNormal_, 0);
    g_.FramebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT2, TEXTURE_2D, gMat_, 0);
    g_.FramebufferTexture2D(FRAMEBUFFER, DEPTH_ATTACHMENT, TEXTURE_2D, gDepth_, 0);
    const GLenum bufs[3] = {COLOR_ATTACHMENT0, COLOR_ATTACHMENT1, COLOR_ATTACHMENT2};
    g_.DrawBuffers(3, bufs);
    if (g_.CheckFramebufferStatus(FRAMEBUFFER) != FRAMEBUFFER_COMPLETE) error_ = "G-buffer incomplete";
    hdrA_ = tex2D(RGBA16F, rw_, rh_, LINEAR, 1); hdrAFbo_ = fbo(hdrA_, 0);
    hdrB_ = tex2D(RGBA16F, rw_, rh_, LINEAR, 1); hdrBFbo_ = fbo(hdrB_, gDepth_); hdrBColorFbo_ = fbo(hdrB_, 0);
    // the history (and everything after the TAA) at the output resolution: with a render scale below 1 the TAA upscales
    ow_ = w; oh_ = h;
    // (packed floats, 4 bytes a pixel: the TAA, bloom and tone map are bandwidth-bound at the output resolution, and
    // nothing after the TAA needs an alpha channel)
    for (int i = 0; i < 2; i++) { hist_[i] = tex2D(R11F_G11F_B10F, ow_, oh_, LINEAR, 1); histFbo_[i] = fbo(hist_[i], 0); }
    lensTex_ = tex2D(R11F_G11F_B10F, ow_, oh_, LINEAR, 1); lensFbo_ = fbo(lensTex_, 0);
    aoRaw_ = tex2D(R8, rw_ / 2, rh_ / 2, LINEAR, 1); aoRawFbo_ = fbo(aoRaw_, 0);
    ao_ = tex2D(R8, rw_, rh_, LINEAR, 1); aoFbo_ = fbo(ao_, 0);
    ssrTex_ = tex2D(RGBA16F, rw_ / 2, rh_ / 2, LINEAR, 1); ssrFbo_ = fbo(ssrTex_, 0);
    fogW_ = std::max(8, rw_ / q_.fogDiv); fogH_ = std::max(8, rh_ / q_.fogDiv);
    fogTex_ = tex2D(RGBA16F, fogW_, fogH_, LINEAR, 1); fogFbo_ = fbo(fogTex_, 0);
    int bw = ow_ / 2, bh = oh_ / 2;
    for (int i = 0; i < BLOOM_LEVELS; i++) {
        bloomW_[i] = std::max(2, bw); bloomH_[i] = std::max(2, bh);
        bloomTex_[i] = tex2D(R11F_G11F_B10F, bloomW_[i], bloomH_[i], LINEAR, 1);
        bloomFbo_[i] = fbo(bloomTex_[i], 0);
        bw /= 2; bh /= 2;
    }
    tilesX_ = (rw_ + TILE - 1) / TILE; tilesY_ = (rh_ + TILE - 1) / TILE;
    g_.BindBuffer(SHADER_STORAGE_BUFFER, tileBuf_);
    g_.BufferData(SHADER_STORAGE_BUFFER, static_cast<GLsizeiptr>(tilesX_) * tilesY_ * 32 * 4, nullptr, DYNAMIC_COPY);
    g_.BindBuffer(SHADER_STORAGE_BUFFER, 0);
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    historyValid_ = false;
}

void Renderer::disposeTargets() {
    if (!gFbo_) return;
    GLuint f[] = {gFbo_, hdrAFbo_, hdrBFbo_, hdrBColorFbo_, histFbo_[0], histFbo_[1], aoRawFbo_, aoFbo_, ssrFbo_, fogFbo_, lensFbo_};
    g_.DeleteFramebuffers(11, f);
    GLuint t[] = {gAlbedo_, gNormal_, gMat_, gDepth_, hdrA_, hdrB_, hist_[0], hist_[1], aoRaw_, ao_, ssrTex_, fogTex_, lensTex_};
    g_.DeleteTextures(13, t);
    g_.DeleteFramebuffers(BLOOM_LEVELS, bloomFbo_);
    g_.DeleteTextures(BLOOM_LEVELS, bloomTex_);
    gFbo_ = 0;
}

void Renderer::ensureShadowMaps() {
    if (shadowTex_ && shadowRes_ == q_.shadowRes && shadowLayers_ == q_.cascades) return;
    if (shadowTex_) { g_.DeleteTextures(1, &shadowTex_); g_.DeleteFramebuffers(4, shadowFbo_); }
    shadowRes_ = q_.shadowRes; shadowLayers_ = q_.cascades;
    g_.GenTextures(1, &shadowTex_);
    g_.BindTexture(TEXTURE_2D_ARRAY, shadowTex_);
    g_.TexStorage3D(TEXTURE_2D_ARRAY, 1, DEPTH_COMPONENT24, shadowRes_, shadowRes_, 4);
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_MIN_FILTER, static_cast<GLint>(LINEAR));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_MAG_FILTER, static_cast<GLint>(LINEAR));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_COMPARE_MODE, static_cast<GLint>(COMPARE_REF_TO_TEXTURE));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_COMPARE_FUNC, static_cast<GLint>(LEQUAL));
    for (int i = 0; i < 4; i++) {
        g_.GenFramebuffers(1, &shadowFbo_[i]);
        g_.BindFramebuffer(FRAMEBUFFER, shadowFbo_[i]);
        g_.FramebufferTextureLayer(FRAMEBUFFER, DEPTH_ATTACHMENT, shadowTex_, 0, i);
        g_.DrawBuffer(NONE);
        g_.ReadBuffer(NONE);
        g_.ClearDepth(1.0);
        g_.Clear(DEPTH_BUFFER_BIT);
    }
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    cascadeAge_ = -1;      // every cascade is drawn on the next frame
}

int32_t Renderer::texture(const astratto_texture_desc& d, uint32_t* out) {
    if (d.width == 0 || d.height == 0 || (d.kind == ASTRATTO_TEX_ARRAY && d.layers == 0)) { error_ = "a texture of no size"; return ASTRATTO_ERR_ARGUMENT; }
    const uint64_t need = static_cast<uint64_t>(d.width) * d.height * (d.kind == ASTRATTO_TEX_ARRAY ? d.layers : 1) * 4;
    if (d.pixels != nullptr && d.bytes < need) { error_ = "fewer pixels than the texture's size"; return ASTRATTO_ERR_ARGUMENT; }
    Texture t;
    t.target = d.kind == ASTRATTO_TEX_ARRAY ? TEXTURE_2D_ARRAY : TEXTURE_2D;
    t.layers = d.kind == ASTRATTO_TEX_ARRAY ? d.layers : 1;
    t.width = d.width; t.height = d.height;
    g_.GenTextures(1, &t.id);
    g_.BindTexture(t.target, t.id);
    g_.PixelStorei(UNPACK_ALIGNMENT, 1);
    if (t.target == TEXTURE_2D_ARRAY) g_.TexImage3D(t.target, 0, static_cast<GLint>(RGBA8), static_cast<GLsizei>(d.width), static_cast<GLsizei>(d.height), static_cast<GLsizei>(d.layers), 0, RGBA, UNSIGNED_BYTE, d.pixels);
    else g_.TexImage2D(t.target, 0, static_cast<GLint>(RGBA8), static_cast<GLsizei>(d.width), static_cast<GLsizei>(d.height), 0, RGBA, UNSIGNED_BYTE, d.pixels);
    const bool mip = (d.flags & ASTRATTO_TEX_MIPMAP) != 0, nearest = (d.flags & ASTRATTO_TEX_NEAREST) != 0, mipLinear = (d.flags & ASTRATTO_TEX_MIP_LINEAR) != 0;
    const bool minNearest = nearest && (d.flags & ASTRATTO_TEX_MIN_LINEAR) == 0;
    const GLenum minFilter = !mip ? (minNearest ? NEAREST : LINEAR)
                                  : minNearest ? (mipLinear ? NEAREST_MIPMAP_LINEAR : NEAREST_MIPMAP_NEAREST) : (mipLinear ? LINEAR_MIPMAP_LINEAR : LINEAR_MIPMAP_NEAREST);
    g_.TexParameteri(t.target, TEXTURE_MIN_FILTER, static_cast<GLint>(minFilter));
    g_.TexParameteri(t.target, TEXTURE_MAG_FILTER, static_cast<GLint>(nearest ? NEAREST : LINEAR));
    const uint32_t maxLevel = (d.flags >> 8) & 15u;
    if (maxLevel > 0) g_.TexParameteri(t.target, TEXTURE_MAX_LEVEL, static_cast<GLint>(maxLevel - 1));
    if (mip) g_.GenerateMipmap(t.target);
    if (d.flags & ASTRATTO_TEX_ANISOTROPIC) {
        GLfloat most = 0;
        g_.GetFloatv(MAX_TEXTURE_MAX_ANISOTROPY, &most);
        if (g_.GetError() == 0 && most >= 1) g_.TexParameterf(t.target, TEXTURE_MAX_ANISOTROPY, std::min(8.0f, most));
    }
    const GLint wrap = static_cast<GLint>((d.flags & ASTRATTO_TEX_REPEAT) ? REPEAT : CLAMP_TO_EDGE);
    g_.TexParameteri(t.target, TEXTURE_WRAP_S, wrap);
    g_.TexParameteri(t.target, TEXTURE_WRAP_T, wrap);
    const uint32_t id = nextTexture_++;
    textures_[id] = t;
    *out = id;
    return ASTRATTO_OK;
}

int32_t Renderer::textureUpdate(uint32_t id, const uint8_t* pixels, uint64_t bytes) {
    auto it = textures_.find(id);
    if (it == textures_.end() || it->second.target != TEXTURE_2D) { error_ = "no such 2D texture"; return ASTRATTO_ERR_ARGUMENT; }
    const GLsizei w = static_cast<GLsizei>(it->second.width), h = static_cast<GLsizei>(it->second.height);
    g_.BindTexture(TEXTURE_2D, it->second.id);
    if (bytes < static_cast<uint64_t>(w) * h * 4) { error_ = "fewer pixels than the texture's size"; return ASTRATTO_ERR_ARGUMENT; }
    g_.PixelStorei(UNPACK_ALIGNMENT, 1);
    g_.TexSubImage2D(TEXTURE_2D, 0, 0, 0, w, h, RGBA, UNSIGNED_BYTE, pixels);
    return ASTRATTO_OK;
}

int32_t Renderer::mesh(const float* v, uint32_t count, uint32_t* out) {
    if (v == nullptr && count > 0) { error_ = "no vertices"; return ASTRATTO_ERR_ARGUMENT; }
    ModelMesh m;
    m.count = count;
    if (count > 0) {
        float lo[3] = {v[0], v[1], v[2]}, hi[3] = {v[0], v[1], v[2]};
        for (uint32_t i = 1; i < count; i++) for (int axis = 0; axis < 3; axis++) {
            lo[axis] = std::min(lo[axis], v[i * 10 + axis]); hi[axis] = std::max(hi[axis], v[i * 10 + axis]);
        }
        for (int axis = 0; axis < 3; axis++) { m.center[axis] = (lo[axis] + hi[axis]) * 0.5f; m.extent[axis] = (hi[axis] - lo[axis]) * 0.5f; }
    }
    // see-through: any voxel under 250 of 255 opaque (as the Java model builder decides it)
    for (uint32_t i = 0; i < count; i++) if (v[i * 10 + 6] < 249.5f / 255.0f) { m.translucent = true; break; }
    g_.GenVertexArrays(1, &m.vao);
    g_.GenBuffers(1, &m.vbo);
    g_.BindVertexArray(m.vao);
    g_.BindBuffer(ARRAY_BUFFER, m.vbo);
    g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(std::max<uint32_t>(1, count)) * 40, v, STATIC_DRAW);
    g_.VertexAttribPointer(0, 3, FLOAT, FALSE_, 40, reinterpret_cast<const void*>(0));
    g_.VertexAttribPointer(1, 4, FLOAT, FALSE_, 40, reinterpret_cast<const void*>(12));
    g_.VertexAttribPointer(2, 3, FLOAT, FALSE_, 40, reinterpret_cast<const void*>(28));
    g_.EnableVertexAttribArray(0);
    g_.EnableVertexAttribArray(1);
    g_.EnableVertexAttribArray(2);
    g_.BindVertexArray(0);
    const uint32_t id = nextMesh_++;
    meshes_[id] = m;
    *out = id;
    return ASTRATTO_OK;
}

int32_t Renderer::release(uint32_t kind, uint32_t id) {
    if (kind == ASTRATTO_RES_TEXTURE) {
        auto it = textures_.find(id);
        if (it == textures_.end()) return ASTRATTO_ERR_HANDLE;
        g_.DeleteTextures(1, &it->second.id);
        textures_.erase(it);
        return ASTRATTO_OK;
    }
    if (kind == ASTRATTO_RES_MESH) {
        auto lt = layerMeshes_.find(id);
        if (lt != layerMeshes_.end()) { freeLayer(lt->second); layerMeshes_.erase(lt); return ASTRATTO_OK; }   // drawn only inside a frame call: no frame holds it
        auto it = meshes_.find(id);
        if (it == meshes_.end()) return ASTRATTO_ERR_HANDLE;
        // deleted a couple of frames later: a frame already described may still name it
        graveyard_.push_back({frameNo_, it->second});
        meshes_.erase(it);
        return ASTRATTO_OK;
    }
    return ASTRATTO_ERR_ARGUMENT;
}

int32_t Renderer::worldLook(const astratto_world_look& look) {
    if (look.table == nullptr || look.table->block_count > 256 || look.palette == nullptr) { error_ = "a world look needs a render table and a palette"; return ASTRATTO_ERR_ARGUMENT; }
    const astratto_render_table& t = *look.table;
    lookBlocks_.assign(t.blocks, t.blocks + t.block_count);
    lookShapes_.assign(t.shapes, t.shapes + t.shape_count);
    lookBoxes_.assign(t.boxes, t.boxes + static_cast<size_t>(t.box_count) * 8);
    lookTable_ = t;
    lookTable_.blocks = lookBlocks_.data();
    lookTable_.shapes = lookShapes_.data();
    lookTable_.boxes = lookBoxes_.data();
    meshers_.clear();
    atlasTex_ = look.atlas; noiseTex_ = look.noise; layers_ = std::max<uint32_t>(1, look.layers);
    palette_.assign(look.palette, look.palette + 256 * 8);
    std::memset(opaqueMask_, 0, sizeof opaqueMask_);
    std::memset(particleCollision_,0,sizeof particleCollision_);
    for(uint32_t id=0;id<t.block_count;id++)if(t.blocks[id].flags&(ASTRATTO_RB_SOLID|ASTRATTO_RB_LIQUID))particleCollision_[id>>5]|=1u<<(id&31);
    for (uint32_t id = 0; id < t.block_count; id++) if (t.blocks[id].flags & ASTRATTO_RB_OCCLUDES) opaqueMask_[id >> 5] |= 1u << (id & 31);
    const float ground = look.ground_y;
    // per atlas layer: roughness, metalness, emission
    if (layerMat_) g_.DeleteTextures(1, &layerMat_);
    std::vector<uint8_t> lm(static_cast<size_t>(layers_) * 4, 0);
    for (uint32_t i = 0; i < layers_; i++) { lm[i * 4] = 215; lm[i * 4 + 3] = 255; }
    if (look.layer_materials) std::memcpy(lm.data(), look.layer_materials, lm.size());
    g_.GenTextures(1, &layerMat_);
    g_.BindTexture(TEXTURE_2D, layerMat_);
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.PixelStorei(UNPACK_ALIGNMENT, 1);
    g_.TexImage2D(TEXTURE_2D, 0, static_cast<GLint>(RGBA8), static_cast<GLsizei>(layers_), 1, 0, RGBA, UNSIGNED_BYTE, lm.data());
    meshFlags_ = look.mesh_flags;
    const bool regroup = hasLook_ && ground != groundY_;
    groundY_ = ground;
    hasLook_ = true;
    if (paletteTex_) buildPalette();
    // what is drawn must be meshed again with the new look (and the programs built again for a new street level)
    if (world_ != nullptr) {
        if (regroup) sizeX_ = -1;
        for (int c = 0; c < CX_ * CZ_; c++) if (!queued_[c]) { queued_[c] = 1; pending_.push_back(c); }
    }
    return ASTRATTO_OK;
}

// ------------------------------------------------------------------ the world on the GPU
void Renderer::freeWorld() {
    for (size_t c = 0; c < vaoO_.size(); c++) {
        if (vaoO_[c]) { g_.DeleteVertexArrays(1, &vaoO_[c]); g_.DeleteBuffers(1, &vboO_[c]); }
        if (vaoT_[c]) { g_.DeleteVertexArrays(1, &vaoT_[c]); g_.DeleteBuffers(1, &vboT_[c]); }
    }
    vaoO_.clear(); vboO_.clear(); vaoT_.clear(); vboT_.clear(); quadsO_.clear(); quadsT_.clear();
    pending_.clear(); queued_.clear(); voxelDirty_.clear(); voxelQueued_.clear();
    GLuint t[] = {voxTex_, probeR_, probeG_, probeB_, probeVis_, probeMeta_};
    for (GLuint x : t) if (x) g_.DeleteTextures(1, &x);
    voxTex_ = probeR_ = probeG_ = probeB_ = probeVis_ = probeMeta_ = 0;
    world_ = nullptr;
}

bool Renderer::buildPrograms(int sx, int sy, int sz) {
    auto src = [&](const char* n) -> std::string {
        auto it = sources_.find(n);
        return it == sources_.end() ? std::string() : withWorld(resolveIncludes(it->second, sources_, 0), sx, sy, sz, groundY_);
    };
    struct P { Program* p; const char* vs; const char* fs; };
    // only the programs that trace the voxel volume or read the probe grid, or measure from street level, depend on the world
    const P list[] = {{&gWorld_, "world.vert", "world_gbuffer.frag"}, {&lighting_, "fullscreen.vert", "lighting.frag"}, {&vol_, "fullscreen.vert", "volumetric.frag"},
                      {&fogApply_, "fullscreen.vert", "fog_apply.frag"}, {&fWorld_, "world.vert", "world_forward.frag"}, {&fModel_, "model.vert", "model_forward.frag"}};
    for (const P& p : list) {
        p.p->destroy(g_);
        if (!p.p->build(g_, src(p.vs), src(p.fs), error_)) { error_ = std::string(p.vs) + " + " + p.fs + ": " + error_; return false; }
    }
    probeCs_.destroy(g_);
    if (!probeCs_.build(g_, src("probes.comp"), error_)) { error_ = "probes.comp: " + error_; return false; }
    return true;
}

void Renderer::bindWorld(Engine* e, astratto_handle h) {
    VoxelWorld* w = (e != nullptr && h != ASTRATTO_NO_HANDLE) ? e->worlds().get(h) : nullptr;
    if (w == world_ && (w == nullptr || (w->sizeX() == sizeX_ && w->sizeY() == sizeY_ && w->sizeZ() == sizeZ_)) && h == worldHandle_) return;
    const bool sameSize = w != nullptr && w->sizeX() == sizeX_ && w->sizeY() == sizeY_ && w->sizeZ() == sizeZ_;
    freeWorld();
    worldHandle_ = w ? h : ASTRATTO_NO_HANDLE;
    if (w == nullptr) return;
    if (!sameSize && !buildPrograms(w->sizeX(), w->sizeY(), w->sizeZ())) return;
    world_ = w;
    waterVersions_.clear(); waterAccumulator_=waterClock_=0;
    for(GLuint texture:{water_[0],water_[1]})g_.ClearTexImage(texture,0,RGBA,FLOAT,nullptr);
    sizeX_ = w->sizeX(); sizeY_ = w->sizeY(); sizeZ_ = w->sizeZ();
    CX_ = w->columnsX(); CZ_ = w->columnsZ();
    const size_t n = static_cast<size_t>(CX_) * CZ_;
    vaoO_.assign(n, 0); vboO_.assign(n, 0); vaoT_.assign(n, 0); vboT_.assign(n, 0); quadsO_.assign(n, 0); quadsT_.assign(n, 0);
    queued_.assign(n, 1); voxelQueued_.assign(n, 1);
    pending_.clear(); voxelDirty_.clear();
    for (size_t c = 0; c < n; c++) { pending_.push_back(static_cast<int>(c)); voxelDirty_.push_back(static_cast<int>(c)); }
    std::vector<int> taken;
    w->takeDirty(taken);           // everything is queued anyway
    // the voxel volume: (y, z, x), the world array's own order, so a column uploads without repacking
    g_.GenTextures(1, &voxTex_);
    g_.BindTexture(TEXTURE_3D, voxTex_);
    g_.TexParameteri(TEXTURE_3D, TEXTURE_MIN_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_3D, TEXTURE_MAG_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_3D, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_3D, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_3D, TEXTURE_WRAP_R, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.PixelStorei(UNPACK_ALIGNMENT, 1);
    g_.TexImage3D(TEXTURE_3D, 0, static_cast<GLint>(R8UI), sizeY_, sizeZ_, sizeX_, 0, RED_INTEGER, UNSIGNED_BYTE, nullptr);
    voxFilled_ = false;
    column_.assign(static_cast<size_t>(16) * 16 * sizeY_, 0);
    if (!paletteTex_) buildPalette();
    // the probes: one every four blocks, covering the whole world; the textures are also the cache
    PX_ = std::max(1, sizeX_ / SPACING); PY_ = std::max(1, sizeY_ / SPACING); PZ_ = std::max(1, sizeZ_ / SPACING);
    auto volume = [&](GLenum format, GLenum filter) {
        GLuint t = 0;
        g_.GenTextures(1, &t);
        g_.BindTexture(TEXTURE_3D, t);
        g_.TexStorage3D(TEXTURE_3D, 1, format, PX_, PY_, PZ_);
        g_.TexParameteri(TEXTURE_3D, TEXTURE_MIN_FILTER, static_cast<GLint>(filter));
        g_.TexParameteri(TEXTURE_3D, TEXTURE_MAG_FILTER, static_cast<GLint>(filter));
        g_.TexParameteri(TEXTURE_3D, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
        g_.TexParameteri(TEXTURE_3D, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
        g_.TexParameteri(TEXTURE_3D, TEXTURE_WRAP_R, static_cast<GLint>(CLAMP_TO_EDGE));
        // storage starts undefined: cleared, so unlit probes read as black and invalid
        g_.ClearTexImage(t, 0, RGBA, FLOAT, nullptr);
        return t;
    };
    probeR_ = volume(RGBA16F, LINEAR); probeG_ = volume(RGBA16F, LINEAR); probeB_ = volume(RGBA16F, LINEAR);
    probeVis_ = volume(RGBA16F, NEAREST); probeMeta_ = volume(RGBA8, NEAREST);
    passes_.assign(n, 0); signature_.assign(n, 0); lastFrame_.assign(n, 0); everLit_.assign(n, 0); score_.assign(n, 0);
    currentSignature_++;
    meshed_.clear();
    historyValid_ = false;
}

void Renderer::buildPalette() {
    // albedo and emitted light per block id (the game's, from the textures its blocks are drawn with), for what a probe ray hits
    std::vector<float> fb(256 * 2 * 4, 0.0f);
    for (int id = 0; id < 256; id++) {
        const float* p = palette_.size() >= 256 * 8 ? &palette_[static_cast<size_t>(id) * 8] : nullptr;
        fb[id * 4] = p ? p[0] : 0.5f; fb[id * 4 + 1] = p ? p[1] : 0.5f; fb[id * 4 + 2] = p ? p[2] : 0.5f; fb[id * 4 + 3] = 1;
        float* e = &fb[(256 + id) * 4];
        e[0] = p ? p[4] : 0; e[1] = p ? p[5] : 0; e[2] = p ? p[6] : 0; e[3] = 0;
    }
    if (!paletteTex_) g_.GenTextures(1, &paletteTex_);
    g_.BindTexture(TEXTURE_2D, paletteTex_);
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, static_cast<GLint>(NEAREST));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_2D, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexImage2D(TEXTURE_2D, 0, static_cast<GLint>(RGBA16F), 256, 2, 0, RGBA, FLOAT, fb.data());
}

void Renderer::meshColumns(Engine* e, VoxelWorld& w, int budget) {
    std::vector<int> taken;
    w.takeDirty(taken);
    for (int c : taken) {
        if (c < 0 || c >= CX_ * CZ_) continue;
        if (!queued_[c]) { queued_[c] = 1; pending_.push_back(c); }
        if (!voxelQueued_[c]) { voxelQueued_[c] = 1; voxelDirty_.push_back(c); }
    }
    if (pending_.empty() || !hasLook_) return;
    // nearest first: what is in front of the camera is there soonest
    const float cx = cam_.x, cz = cam_.z;
    std::sort(pending_.begin(), pending_.end(), [&](int a, int b) {
        float ax = (a / CZ_) * 16 + 8 - cx, az = (a % CZ_) * 16 + 8 - cz, bx = (b / CZ_) * 16 + 8 - cx, bz = (b % CZ_) * 16 + 8 - cz;
        return ax * ax + az * az > bx * bx + bz * bz;      // the nearest at the back, taken from there
    });
    const size_t n = std::min(pending_.size(), static_cast<size_t>(std::max(1, budget)));
    std::vector<int> batch(pending_.end() - static_cast<std::ptrdiff_t>(n), pending_.end());
    pending_.resize(pending_.size() - n);
    JobPool& pool = e->pool();
    while (meshers_.size() < pool.workers()) meshers_.emplace_back(new ChunkMesher(lookTable_));
    if (meshed_.size() < n) meshed_.resize(n);
    std::vector<std::vector<uint8_t>> cells(pool.workers()), light(pool.workers());
    const uint32_t flags = meshFlags_;
    pool.parallelFor(n, [&](size_t i, unsigned worker) {
        const int c = batch[i];
        copyColumn(w, c / CZ_, c % CZ_, cells[worker], light[worker]);
        astratto_mesh_io io{};
        io.struct_size = sizeof io; io.flags = flags; io.size_y = static_cast<uint32_t>(w.sizeY());
        io.origin_x = (c / CZ_) * 16; io.origin_z = (c % CZ_) * 16; io.world_x = w.sizeX(); io.world_z = w.sizeZ();
        io.blocks = cells[worker].data(); io.light = light[worker].data();
        ChunkMesher& m = *meshers_[worker];
        meshed_[i].ok = m.mesh(io);
        meshed_[i].o.assign(m.opaque().begin(), m.opaque().end());
        meshed_[i].t.assign(m.translucent().begin(), m.translucent().end());
    });
    for (size_t i = 0; i < n; i++) {
        const int c = batch[i];
        queued_[c] = 0;
        if (!meshed_[i].ok) continue;
        uploadColumn(c, meshed_[i].o, true);
        uploadColumn(c, meshed_[i].t, false);
        columnsMeshed_++;
    }
}

void Renderer::uploadColumn(int col, const std::vector<uint8_t>& data, bool opaquePass) {
    std::vector<GLuint>& vaos = opaquePass ? vaoO_ : vaoT_;
    std::vector<GLuint>& vbos = opaquePass ? vboO_ : vboT_;
    std::vector<int>& quads = opaquePass ? quadsO_ : quadsT_;
    const int qn = static_cast<int>(data.size() / (12 * 4));
    if (qn == 0) { quads[col] = 0; return; }
    if (vaos[col] == 0) {
        g_.GenVertexArrays(1, &vaos[col]);
        g_.GenBuffers(1, &vbos[col]);
        g_.BindVertexArray(vaos[col]);
        g_.BindBuffer(ARRAY_BUFFER, vbos[col]);
        g_.VertexAttribPointer(0, 4, UNSIGNED_BYTE, FALSE_, 12, reinterpret_cast<const void*>(0));
        g_.VertexAttribPointer(1, 4, UNSIGNED_BYTE, FALSE_, 12, reinterpret_cast<const void*>(4));
        g_.VertexAttribPointer(2, 4, UNSIGNED_BYTE, TRUE_, 12, reinterpret_cast<const void*>(8));
        g_.EnableVertexAttribArray(0);
        g_.EnableVertexAttribArray(1);
        g_.EnableVertexAttribArray(2);
        g_.BindBuffer(ELEMENT_ARRAY_BUFFER, ebo_);
    }
    g_.BindVertexArray(vaos[col]);
    g_.BindBuffer(ARRAY_BUFFER, vbos[col]);
    g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(data.size()), data.data(), STATIC_DRAW);
    g_.BindVertexArray(0);
    quads[col] = std::min(qn, MAX_QUADS);
}

int Renderer::drawOpaque(Program& sh, const Frustum& fr, float cx, float cz, float maxDist, bool nearFirst) {
    const float vd2 = maxDist * maxDist;
    order_.clear();
    for (int x = 0; x < CX_; x++) for (int z = 0; z < CZ_; z++) {
        const int c = x * CZ_ + z;
        if (quadsO_[c] == 0) continue;
        const float x0 = static_cast<float>(x * 16), z0 = static_cast<float>(z * 16);
        const float ddx = std::max(0.0f, std::max(x0 - cx, cx - x0 - 16)), ddz = std::max(0.0f, std::max(z0 - cz, cz - z0 - 16));
        const float d2 = ddx * ddx + ddz * ddz;
        if (d2 > vd2) continue;
        if (!fr.aabb(x0, 0, z0, x0 + 16, static_cast<float>(sizeY_), z0 + 16)) continue;
        order_.push_back((static_cast<uint64_t>(static_cast<int64_t>(d2)) << 20) | static_cast<uint64_t>(c));
    }
    if (nearFirst) std::sort(order_.begin(), order_.end());
    for (uint64_t o : order_) {
        const int c = static_cast<int>(o & 0xFFFFF);
        sh.f3("uOrigin", static_cast<float>((c / CZ_) * 16), 0, static_cast<float>((c % CZ_) * 16));
        g_.BindVertexArray(vaoO_[c]);
        g_.DrawElements(TRIANGLES, quadsO_[c] * 6, UNSIGNED_INT, nullptr);
        quadsDrawn_ += static_cast<uint32_t>(quadsO_[c]);
    }
    g_.BindVertexArray(0);
    return static_cast<int>(order_.size());
}

void Renderer::drawTranslucent(Program& sh, const Frustum& fr, float cx, float cz, float maxDist) {
    const float vd2 = maxDist * maxDist;
    std::vector<std::pair<int, int>> list;
    for (int x = 0; x < CX_; x++) for (int z = 0; z < CZ_; z++) {
        const int c = x * CZ_ + z;
        if (quadsT_[c] == 0) continue;
        const float x0 = static_cast<float>(x * 16), z0 = static_cast<float>(z * 16);
        const float ddx = std::max(0.0f, std::max(x0 - cx, cx - x0 - 16)), ddz = std::max(0.0f, std::max(z0 - cz, cz - z0 - 16));
        const float d2 = ddx * ddx + ddz * ddz;
        if (d2 > vd2 || !fr.aabb(x0, 0, z0, x0 + 16, static_cast<float>(sizeY_), z0 + 16)) continue;
        list.push_back({c, static_cast<int>(d2)});
    }
    std::stable_sort(list.begin(), list.end(), [](const std::pair<int, int>& a, const std::pair<int, int>& b) { return a.second > b.second; });
    for (const auto& t : list) {
        const int c = t.first;
        sh.f3("uOrigin", static_cast<float>((c / CZ_) * 16), 0, static_cast<float>((c % CZ_) * 16));
        g_.BindVertexArray(vaoT_[c]);
        g_.DrawElements(TRIANGLES, quadsT_[c] * 6, UNSIGNED_INT, nullptr);
    }
    g_.BindVertexArray(0);
}

void Renderer::voxelSync(VoxelWorld& w, int maxColumns) {
    if (voxelDirty_.empty()) return;
    g_.BindTexture(TEXTURE_3D, voxTex_);
    g_.PixelStorei(UNPACK_ALIGNMENT, 1);
    int n = 0;
    size_t at = 0;
    const uint8_t* cells = w.blocks();
    while (at < voxelDirty_.size() && (maxColumns <= 0 || n < maxColumns)) {
        const int c = voxelDirty_[at++];
        voxelQueued_[c] = 0;
        const int cx = c / CZ_, cz = c % CZ_;
        const int wx = std::min(16, sizeX_ - cx * 16), wz = std::min(16, sizeZ_ - cz * 16);
        size_t k = 0;
        for (int x = cx * 16; x < cx * 16 + wx; x++) {
            std::memcpy(column_.data() + k, cells + w.index(x, 0, cz * 16), static_cast<size_t>(wz) * sizeY_);
            k += static_cast<size_t>(wz) * sizeY_;
        }
        g_.TexSubImage3D(TEXTURE_3D, 0, 0, cz * 16, cx * 16, sizeY_, wz, wx, RED_INTEGER, UNSIGNED_BYTE, column_.data());
        probesInvalidate(cx, cz, voxFilled_);
        n++;
    }
    voxelDirty_.erase(voxelDirty_.begin(), voxelDirty_.begin() + static_cast<std::ptrdiff_t>(at));
    if (voxelDirty_.empty()) voxFilled_ = true;
}

// ------------------------------------------------------------------ probes (ProbeField)
void Renderer::probesInvalidate(int cx, int cz, bool spread) {
    const int r = spread ? 2 : 0;
    for (int x = std::max(0, cx - r); x <= std::min(CX_ - 1, cx + r); x++)
        for (int z = std::max(0, cz - r); z <= std::min(CZ_ - 1, cz + r); z++) {
            const int c = x * CZ_ + z;
            passes_[c] = std::min(passes_[c], x == cx && z == cz ? 0 : 2);
        }
}

float Renderer::probesSettledNear(float x, float z, float radius) const {
    int n = 0, ok = 0;
    for (int cx = 0; cx < CX_; cx++) for (int cz = 0; cz < CZ_; cz++) {
        const float dx = cx * 16 + 8 - x, dz = cz * 16 + 8 - z;
        if (dx * dx + dz * dz > radius * radius) continue;
        n++;
        const int c = cx * CZ_ + cz;
        if (signature_[c] == currentSignature_ && passes_[c] >= SETTLED - 2) ok++;
    }
    return n == 0 ? 1.0f : static_cast<float>(ok) / static_cast<float>(n);
}

void Renderer::probesUpdate(int budget, int rays, float camX, float camZ) {
    probeFrame_++;
    budget = std::min(MAX_BATCH, budget);
    probesUpdated_ = 0;
    if (budget <= 0 || CX_ == 0) return;
    int uns = 0;
    for (int cx = 0; cx < CX_; cx++) for (int cz = 0; cz < CZ_; cz++) {
        const int c = cx * CZ_ + cz;
        const float dx = cx * 16 + 8 - camX, dz = cz * 16 + 8 - camZ;
        const float dist = static_cast<float>(std::sqrt(static_cast<double>(dx * dx + dz * dz)));
        const float nearK = 1.0f / (1.0f + dist / 48.0f);
        const bool stale = signature_[c] != currentSignature_;
        const float age = static_cast<float>(probeFrame_ - lastFrame_[c]) / 60.0f;
        float s;
        if (!everLit_[c]) s = dist < 220 ? 40 + nearK * 60 : 12 + nearK * 20;
        else if (passes_[c] < SETTLED && !stale) { s = 30 + nearK * 80 + static_cast<float>(SETTLED - passes_[c]) * 4; uns++; }
        else if (stale && dist < 170) { s = 10 + nearK * 70; uns++; }
        else if (stale) s = std::min(8.0f, age / 4.0f);
        else s = std::min(6.0f, age / 45.0f) + nearK * 0.25f;
        score_[c] = s;
    }
    probesUnsettled_ = uns;
    int n = 0;
    for (int k = 0; k < budget; k++) {
        int best = -1;
        float bs = 0.6f;
        for (int c = 0; c < static_cast<int>(score_.size()); c++) if (score_[c] > bs) { bs = score_[c]; best = c; }
        if (best < 0) break;
        // below this score the work is background: it gets a quarter of the budget, so a settled scene costs little
        if (bs < 25.0f && n >= std::max(1, budget / 4)) break;
        score_[best] = -1;
        batch_[n * 2] = best / CZ_; batch_[n * 2 + 1] = best % CZ_;
        const bool stale = signature_[best] != currentSignature_;
        // first light replaces whatever was there; after that new samples are folded in so noise averages out
        // (a stale probe - the sky moved on - is folded toward the new light, not swapped for one noisy pass of it)
        blend_[n] = !everLit_[best] ? 1.0f : passes_[best] < 2 ? 0.6f : stale ? 0.4f : passes_[best] < SETTLED ? 0.35f : 0.2f;
        if (stale) { signature_[best] = currentSignature_; passes_[best] = std::min(passes_[best], SETTLED - 3); }
        passes_[best]++;
        everLit_[best] = 1;
        lastFrame_[best] = probeFrame_;
        n++;
    }
    if (n == 0) return;
    // a fresh random rotation of the ray set each frame: successive passes sample different directions
    {
        JavaRandom r(0); r.seed = rng_;
        const double u1 = r.nextDouble(), u2 = r.nextDouble() * 2 * 3.141592653589793, u3 = r.nextDouble() * 2 * 3.141592653589793;
        rng_ = r.seed;
        const double a = std::sqrt(1 - u1), b = std::sqrt(u1);
        const float x = static_cast<float>(a * std::sin(u2)), y = static_cast<float>(a * std::cos(u2)), z = static_cast<float>(b * std::sin(u3)), w = static_cast<float>(b * std::cos(u3));
        rot_[0] = 1 - 2 * (y * y + z * z); rot_[1] = 2 * (x * y + z * w); rot_[2] = 2 * (x * z - y * w);
        rot_[3] = 2 * (x * y - z * w); rot_[4] = 1 - 2 * (x * x + z * z); rot_[5] = 2 * (y * z + x * w);
        rot_[6] = 2 * (x * z + y * w); rot_[7] = 2 * (y * z - x * w); rot_[8] = 1 - 2 * (x * x + y * y);
    }
    Program& c = probeCs_;
    c.use();
    c.tex("uVox", 0, TEXTURE_3D, voxTex_);
    c.tex("uPalette", 1, TEXTURE_2D, paletteTex_);
    c.tex("uProbeR", 2, TEXTURE_3D, probeR_);
    c.tex("uProbeG", 3, TEXTURE_3D, probeG_);
    c.tex("uProbeB", 4, TEXTURE_3D, probeB_);
    c.uiv("uOpaque", opaqueMask_, 8);
    c.iv2("uRegions", batch_, n);
    c.fv("uRegionBlend", blend_, n);
    c.i1("uRegionCount", n);
    c.i1("uRays", rays);
    c.mat3("uRot", rot_);
    c.f3("uSunDir", sunDir_[0], sunDir_[1], sunDir_[2]);
    c.f3("uSunCol", sunCol_[0], sunCol_[1], sunCol_[2]);
    c.f3("uSkyZenith", zenith_[0], zenith_[1], zenith_[2]);
    c.f3("uSkyHorizon", horizon_[0], horizon_[1], horizon_[2]);
    g_.BindImageTexture(0, probeR_, 0, TRUE_, 0, READ_WRITE, RGBA16F);
    g_.BindImageTexture(1, probeG_, 0, TRUE_, 0, READ_WRITE, RGBA16F);
    g_.BindImageTexture(2, probeB_, 0, TRUE_, 0, READ_WRITE, RGBA16F);
    g_.BindImageTexture(3, probeVis_, 0, TRUE_, 0, READ_WRITE, RGBA16F);
    g_.BindImageTexture(4, probeMeta_, 0, TRUE_, 0, READ_WRITE, RGBA8);
    const int regionProbes = 4 * PY_ * 4;
    g_.DispatchCompute(static_cast<GLuint>((n * regionProbes + 63) / 64), 1, 1);
    g_.MemoryBarrier(SHADER_IMAGE_ACCESS_BARRIER_BIT | TEXTURE_FETCH_BARRIER_BIT);
    probesUpdated_ = n;
}

// ------------------------------------------------------------------ environment
void Renderer::environment() {
    const Env& e = env_;
    const float sl = static_cast<float>(std::sqrt(static_cast<double>(e.sun[0] * e.sun[0] + e.sun[1] * e.sun[1] + e.sun[2] * e.sun[2])));
    const float sx = e.sun[0] / sl, sy = e.sun[1] / sl, sz = e.sun[2] / sl;
    const float cloud = e.overcast >= 0 ? e.overcast : e.rain;
    const float day = e.daylight, clear = 1 - cloud * 0.75f;
    // (0.2.1, gamefixes: overly harsh, bright sunlight) the sun a little softer and warmer; the sky and bounce carry more
    const float sunK = sstep(-0.04f, 0.14f, sy) * 2.75f * clear, warm = std::max(0.0f, std::min(1.0f, sy * 2.5f));
    const float moonK = sstep(0.0f, 0.25f, -sy) * 0.085f * clear;
    // 2.3: the moon is always opposite the true sun (the light's direction is the moon's by night, so the moon's disc
    // was drawn opposite that - under the horizon - and never seen)
    moonDir_[0] = -sx; moonDir_[1] = -sy; moonDir_[2] = -sz;
    if (sunK >= moonK) {
        sunDir_[0] = sx; sunDir_[1] = sy; sunDir_[2] = sz;
        sunCol_[0] = sunK; sunCol_[1] = sunK * (0.53f + 0.37f * warm); sunCol_[2] = sunK * (0.27f + 0.47f * warm);
    } else {
        sunDir_[0] = -sx; sunDir_[1] = -sy; sunDir_[2] = -sz;
        sunCol_[0] = moonK * 0.55f; sunCol_[1] = moonK * 0.68f; sunCol_[2] = moonK;
    }
    const float nightR = 0.010f, nightG = 0.014f, nightB = 0.030f;
    zenith_[0] = nightR + (0.22f - nightR) * day; zenith_[1] = nightG + (0.40f - nightG) * day; zenith_[2] = nightB + (0.72f - nightB) * day;
    // the horizon takes the fog's colour (already dimmed for night by the caller)
    horizon_[0] = lin(e.fog[0]) * 1.25f + 0.004f; horizon_[1] = lin(e.fog[1]) * 1.25f + 0.005f; horizon_[2] = lin(e.fog[2]) * 1.25f + 0.008f;
    const float grey = (zenith_[0] + zenith_[1] + zenith_[2]) / 3.0f * 0.8f;
    for (int i = 0; i < 3; i++) zenith_[i] += (grey - zenith_[i]) * cloud * 0.85f;
    const float fm = std::max(1e-3f, std::max(e.fog[0], std::max(e.fog[1], e.fog[2])));
    fogCol_[0] = 0.55f + 0.45f * e.fog[0] / fm; fogCol_[1] = 0.55f + 0.45f * e.fog[1] / fm; fogCol_[2] = 0.55f + 0.45f * e.fog[2] / fm;
    for (int i = 0; i < 3; i++) ambient_[i] = horizon_[i] * 0.65f + zenith_[i] * 0.35f;
    // the probes were lit for a sun position: after it has moved a few degrees they are out of date
    const int bucket = static_cast<int>(std::lround(static_cast<float>(std::atan2(static_cast<double>(e.sun[1]), static_cast<double>(e.sun[0]))) * 14.0f)) * 8
                       + static_cast<int>(std::lround(cloud * 4)) + static_cast<int>(day * 8) * 1000;
    if (bucket != lastSunBucket_) { if (lastSunBucket_ != INT32_MIN) currentSignature_++; lastSunBucket_ = bucket; }
}

void Renderer::skyUniforms(Program& s) {
    s.f3("uSkyZenith", zenith_[0], zenith_[1], zenith_[2]);
    s.f3("uSkyHorizon", horizon_[0], horizon_[1], horizon_[2]);
    s.f3("uSunDir", sunDir_[0], sunDir_[1], sunDir_[2]);
    s.f3("uSunCol", sunCol_[0], sunCol_[1], sunCol_[2]);
    s.f3("uMoonDir", moonDir_[0], moonDir_[1], moonDir_[2]);
    s.f1("uDay", env_.daylight);
    s.f1("uRain", env_.overcast >= 0 ? env_.overcast : env_.rain);
    s.f1("uTime", env_.time);
    s.f1("uLightning", env_.lightning);
}

void Renderer::probeUniforms(Program& s, int unit) {
    s.tex("uProbeR", unit, TEXTURE_3D, probeR_);
    s.tex("uProbeG", unit + 1, TEXTURE_3D, probeG_);
    s.tex("uProbeB", unit + 2, TEXTURE_3D, probeB_);
}

void Renderer::bankUniforms(Program& s) {
    s.f4("uWall", env_.wallStart, env_.wallEnd, env_.skyHorizon, env_.skyZenith);
    // the bank is the horizon's colour with half its tint washed out: fog, not coloured smoke
    const float l = horizon_[0] * 0.3f + horizon_[1] * 0.55f + horizon_[2] * 0.15f;
    s.f3("uWallCol", (horizon_[0] + l) * 0.5f, (horizon_[1] + l) * 0.5f, (horizon_[2] + l) * 0.5f);
    if (std::isnan(env_.fogOrigin[0])) s.f3("uFogOrigin", cam_.x, cam_.y, cam_.z); else s.f3("uFogOrigin", env_.fogOrigin[0], env_.fogOrigin[1], env_.fogOrigin[2]);
}

// ------------------------------------------------------------------ the frame
bool Renderer::parse(const uint8_t* cmds, uint32_t bytes) {
    lights_.clear(); lightCount_ = 0; bodyCount_ = 0; models_.clear(); particles_.clear(); emitters_.clear(); quads_.clear(); portraits_.clear();
    hasCamera_ = false; hasClear_ = false; grading_ = Grading();
    doFoliage_ = doProps_ = doDecals_ = false;
    decalVerts_.clear();
    smoke_.clear(); ripples_.clear();
    astratto_handle world = worldHandle_;
    bool worldSaid = false;
    uint32_t at = 0;
    while (at < bytes) {
        astratto_record head;
        if (bytes - at < sizeof head) { error_ = "render stream ends inside a record header"; return false; }
        std::memcpy(&head, cmds + at, sizeof head);
        if (head.bytes < sizeof head || (head.bytes & 3u) != 0 || head.bytes > bytes - at) { error_ = "render record with a bad size"; return false; }
        const uint8_t* r = cmds + at;
        switch (head.type) {
            case ASTRATTO_RCMD_CAMERA: {
                astratto_rcmd_camera c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short camera record"; return false; }
                cam_.view.set(c.view); cam_.proj.set(c.proj); cam_.vmProj.set(c.viewmodel_proj);
                cam_.x = c.x; cam_.y = c.y; cam_.z = c.z; cam_.fx = c.fx; cam_.fy = c.fy; cam_.fz = c.fz;
                cam_.fovY = c.fov_y; cam_.aspect = c.aspect; cam_.nearP = c.near_plane; cam_.farP = c.far_plane;
                cam_.viewDistance = c.view_distance > 0 ? c.view_distance : 260; cam_.dt = c.dt; cam_.reset = c.reset != 0; cam_.prepare = c.prepare != 0;
                hasCamera_ = true;
                break;
            }
            case ASTRATTO_RCMD_ENV: {
                astratto_rcmd_env c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short environment record"; return false; }
                std::memcpy(env_.sun, c.sun, sizeof env_.sun);
                env_.daylight = c.daylight; env_.time = c.time; env_.rain = c.rain; env_.wet = c.wet; env_.snow = c.snow; env_.overcast = c.overcast; env_.lightning = c.lightning;
                std::memcpy(env_.fog, c.fog, sizeof env_.fog);
                env_.fogDensity = c.fog_density; env_.wallStart = c.fog_wall_start; env_.wallEnd = c.fog_wall_end; env_.skyHorizon = c.fog_sky_horizon; env_.skyZenith = c.fog_sky_zenith;
                std::memcpy(env_.tint, c.tint, sizeof env_.tint);
                env_.ambientFloor = c.ambient_floor;
                std::memcpy(env_.fogOrigin, c.fog_origin, sizeof env_.fogOrigin);
                env_.bloom = c.bloom;
                break;
            }
            case ASTRATTO_RCMD_LIGHT: {
                astratto_rcmd_light c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short light record"; return false; }
                if (lightCount_ >= std::min(MAX_LIGHTS, q_.maxLights)) break;
                const float v[12] = {c.x, c.y, c.z, c.radius, c.r, c.g, c.b, static_cast<float>(c.flags), c.dx, c.dy, c.dz, c.cos_outer};
                lights_.insert(lights_.end(), v, v + 12);
                lightCount_++;
                break;
            }
            case ASTRATTO_RCMD_MODEL: {
                astratto_rcmd_model c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short model record"; return false; }
                auto it = meshes_.find(c.mesh);
                if (it == meshes_.end() || it->second.count == 0) break;
                Model m;
                m.mesh = c.mesh;
                std::memcpy(m.m, c.matrix, sizeof m.m);
                m.par[0] = c.r; m.par[1] = c.g; m.par[2] = c.b; m.par[3] = c.a; m.par[4] = c.emissive; m.par[5] = c.light; m.par[6] = c.roughness; m.par[7] = c.metallic;
                uint32_t f = c.flags;
                // anything see-through cannot live in a G-buffer; anything purely self-lit and see-through adds its light
                if (c.a < 0.98f || it->second.translucent) { f |= ASTRATTO_MODEL_FORWARD | ASTRATTO_MODEL_NO_SHADOW; if (c.emissive >= 0.99f && c.a < 0.98f) f |= ASTRATTO_MODEL_ADDITIVE; }
                if (c.emissive > 0.5f) f |= ASTRATTO_MODEL_NO_SHADOW;
                m.flags = f;
                models_.push_back(m);
                break;
            }
            case ASTRATTO_RCMD_RIPPLE: {
                astratto_rcmd_ripple c;
                if(!readRecord(r,head.bytes,c)) { error_="short ripple record";return false; }
                if(!std::isfinite(c.x+c.z+c.radius+c.strength) || c.radius<=0 || c.radius>8 || std::abs(c.strength)>30) { error_="invalid water ripple";return false; }
                if(ripples_.size()<32*4)ripples_.insert(ripples_.end(),{c.x,c.z,c.radius,c.strength});
                break;
            }
            case ASTRATTO_RCMD_EMITTER: {
                astratto_rcmd_emitter c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short emitter record"; return false; }
                bool valid = true;
                for (int i = 0; i < 4; ++i) valid = valid && std::isfinite(c.position[i]) && std::isfinite(c.color[i]) && std::isfinite(c.motion[i]) && std::isfinite(c.params[i]);
                if (!valid || c.position[3] < 0 || c.position[3] > 3 || c.motion[3] <= 0 || c.motion[3] > 8 || c.params[0] < 0 || c.params[0] > 200) { error_ = "invalid element emitter"; return false; }
                if (emitters_.size() < 64*20) {
                    emitters_.insert(emitters_.end(),c.position,c.position+4); emitters_.insert(emitters_.end(),c.color,c.color+4);
                    emitters_.insert(emitters_.end(),c.motion,c.motion+4); emitters_.insert(emitters_.end(),c.params,c.params+4);
                    // Four exact 16-bit float words retain the full source ID without NaN encodings or hash collisions.
                    for (unsigned shift=0;shift<64;shift+=16) emitters_.push_back(static_cast<float>((c.id>>shift)&65535u));
                }
                break;
            }
            case ASTRATTO_RCMD_PARTICLES: {
                astratto_rcmd_particles c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short particles record"; return false; }
                if (head.bytes < sizeof c + static_cast<uint64_t>(c.count) * 48) { error_ = "particles record shorter than its count"; return false; }
                const size_t at0 = particles_.size();
                particles_.resize(at0 + static_cast<size_t>(c.count) * 12);
                std::memcpy(particles_.data() + at0, r + sizeof c, static_cast<size_t>(c.count) * 48);
                break;
            }
            case ASTRATTO_RCMD_QUADS: {
                astratto_rcmd_quads c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short quads record"; return false; }
                if (head.bytes < sizeof c + static_cast<uint64_t>(c.count) * 4 * 36) { error_ = "quads record shorter than its count"; return false; }
                Quads qd;
                qd.texture = c.texture;
                qd.v.resize(static_cast<size_t>(c.count) * 36);
                std::memcpy(qd.v.data(), r + sizeof c, qd.v.size() * 4);
                if (!quads_.empty() && quads_.back().portrait < 0 && quads_.back().texture == qd.texture) quads_.back().v.insert(quads_.back().v.end(), qd.v.begin(), qd.v.end());
                else quads_.push_back(std::move(qd));
                break;
            }
            case ASTRATTO_RCMD_PORTRAIT: {
                astratto_rcmd_portrait c;
                if (!readRecord(r, head.bytes, c) || c.count > 32 || head.bytes < sizeof c + static_cast<uint64_t>(c.count) * sizeof(astratto_portrait_part)) {
                    error_ = "invalid portrait record"; return false;
                }
                if (c.count == 0 || !std::isfinite(c.x) || !std::isfinite(c.y) || !std::isfinite(c.w) || !std::isfinite(c.h)
                        || std::abs(c.x) > 32768 || std::abs(c.y) > 32768 || c.w < 1 || c.h < 1 || c.w > 8192 || c.h > 8192) break;
                Portrait portrait{c.x, c.y, c.w, c.h, {}};
                portrait.parts.resize(c.count);
                std::memcpy(portrait.parts.data(), r + sizeof c, c.count * sizeof(astratto_portrait_part));
                Quads entry; entry.portrait = static_cast<int>(portraits_.size());
                portraits_.push_back(std::move(portrait)); quads_.push_back(std::move(entry));
                break;
            }
            case ASTRATTO_RCMD_GRADING: {
                astratto_rcmd_grading c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short grading record"; return false; }
                grading_.death = c.death; grading_.hurt = c.hurt; grading_.infection = c.infection; grading_.flash = c.flash; grading_.bile = c.bile;
                break;
            }
            case ASTRATTO_RCMD_SHADOW_BODY: {
                astratto_rcmd_body c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short body record"; return false; }
                if (bodyCount_ >= MAX_BODIES) break;
                float* o = &bodies_[bodyCount_ * 5];
                o[0] = c.x; o[1] = c.y; o[2] = c.z; o[3] = c.radius; o[4] = c.height;
                bodyCount_++;
                break;
            }
            case ASTRATTO_RCMD_WORLD: {
                astratto_rcmd_world c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short world record"; return false; }
                world = c.world; worldSaid = true;
                break;
            }
            case ASTRATTO_RCMD_CLEAR: {
                astratto_rcmd_clear c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short clear record"; return false; }
                clear_[0] = c.r; clear_[1] = c.g; clear_[2] = c.b; clear_[3] = c.a;
                hasClear_ = true;
                break;
            }
            case ASTRATTO_RCMD_QUALITY: {
                astratto_rcmd_quality c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short quality record"; return false; }
                if (static_cast<int>(c.preset) != presetLevel_) preset(static_cast<int>(c.preset));
                q_.gtao = (c.passes & ASTRATTO_PASS_GTAO) != 0; q_.ssr = (c.passes & ASTRATTO_PASS_SSR) != 0; q_.volumetrics = (c.passes & ASTRATTO_PASS_VOLUMETRICS) != 0;
                q_.taa = (c.passes & ASTRATTO_PASS_TAA) != 0; q_.bloom = (c.passes & ASTRATTO_PASS_BLOOM) != 0;
                q_.motionBlur = (c.passes & ASTRATTO_PASS_MOTION_BLUR) != 0; q_.dof = (c.passes & ASTRATTO_PASS_DEPTH_OF_FIELD) != 0;
                q_.renderScale = c.render_scale > 0 ? c.render_scale : 1.0f;
                break;
            }
            case ASTRATTO_RCMD_FOLIAGE: {
                if (!readRecord(r, head.bytes, foliage_)) { error_ = "short foliage record"; return false; }
                doFoliage_ = true;
                break;
            }
            case ASTRATTO_RCMD_PROPS: {
                if (!readRecord(r, head.bytes, props_)) { error_ = "short props record"; return false; }
                doProps_ = true;
                break;
            }
            case ASTRATTO_RCMD_DECALS: {
                if (!readRecord(r, head.bytes, decals_)) { error_ = "short decals record"; return false; }
                if (head.bytes < sizeof decals_ + static_cast<uint64_t>(decals_.vertex_count) * 40) { error_ = "decals record shorter than its count"; return false; }
                const size_t at0 = decalVerts_.size();
                decalVerts_.resize(at0 + static_cast<size_t>(decals_.vertex_count) * 10);
                std::memcpy(decalVerts_.data() + at0, r + sizeof decals_, static_cast<size_t>(decals_.vertex_count) * 40);
                doDecals_ = true;
                break;
            }
            case ASTRATTO_RCMD_LENS: {
                if (!readRecord(r, head.bytes, lens_cfg_)) { error_ = "short lens record"; return false; }
                break;
            }
            case ASTRATTO_RCMD_SMOKE: {
                astratto_rcmd_smoke c;
                if (!readRecord(r, head.bytes, c)) { error_ = "short smoke record"; return false; }
                if (head.bytes < sizeof c + static_cast<uint64_t>(c.count) * 48) { error_ = "smoke record shorter than its count"; return false; }
                const size_t n = std::min<size_t>(32 - smoke_.size() / 12, c.count);
                const size_t at0 = smoke_.size();
                smoke_.resize(at0 + n * 12);
                std::memcpy(smoke_.data() + at0, r + sizeof c, n * 48);
                break;
            }
            default: break;     // a command of a newer version: stepped over by its own size
        }
        at += head.bytes;
    }
    pendingWorld_ = worldSaid ? world : worldHandle_;
    return true;
}

int32_t Renderer::frame(Engine* engine, astratto_render_io& io) {
    const auto t0 = std::chrono::steady_clock::now();
    if (io.command_bytes > 0 && io.commands == nullptr) { error_ = "no render commands"; return ASTRATTO_ERR_ARGUMENT; }
    if (!parse(io.commands, io.command_bytes)) return ASTRATTO_ERR_STREAM;
    error_.clear();
    columnsMeshed_ = columnsDrawn_ = quadsDrawn_ = modelsDrawn_ = 0;
    // meshes released a few frames ago are let go now
    while (!graveyard_.empty() && graveyard_.front().first + 3 < frameNo_) {
        g_.DeleteBuffers(1, &graveyard_.front().second.vbo); g_.DeleteVertexArrays(1, &graveyard_.front().second.vao);
        graveyard_.erase(graveyard_.begin());
    }
    const int windowW = static_cast<int>(std::max<uint32_t>(16, io.width)), windowH = static_cast<int>(std::max<uint32_t>(16, io.height));
    bindWorld(engine, pendingWorld_);
    if (!error_.empty()) return ASTRATTO_ERR_INTERNAL;
    VoxelWorld* w = world_;
    const bool scene = hasCamera_ && w != nullptr && hasLook_ && atlasTex_ != 0 && textures_.count(atlasTex_) != 0;
    frameNo_++;
    // frame pacing: before queueing a new frame, wait until the GPU has finished the one MAX_FRAMES_AHEAD frames back
    {
        gl::GLsync& f = fences_[frameNo_ % (MAX_FRAMES_AHEAD + 1)];
        if (f != nullptr) { g_.ClientWaitSync(f, gl::SYNC_FLUSH_COMMANDS_BIT, 100000000ull); g_.DeleteSync(f); f = nullptr; }
    }
    readTimers();
    stamp(0);
    if (scene) {
        resize(windowW, windowH);
        ensureShadowMaps();
        // ---- world state on the GPU, then the probes that depend on it (a loading screen: as fast as they go)
        if (cam_.prepare) {
            meshColumns(engine, *w, 1 << 20);
            voxelSync(*w, 400);
            environment();
            if (voxelDirty_.empty()) for (int done = 0; done < 192; done += MAX_BATCH) probesUpdate(std::min(MAX_BATCH, 192 - done), q_.probeRays, cam_.x, cam_.z);
        } else {
            // (0.2.1, gamefixes: stuttering on the larger map) in play a frame meshes a few columns, nearest first -
            // a burst of edits (a fire, a fall of blocks) is spread over frames instead of stalling one
            meshColumns(engine, *w, pending_.size() > 600 ? 192 : pending_.size() > 96 ? 32 : 12);
            voxelSync(*w, 48);
            environment();
            probesUpdate(q_.probeBudget, q_.probeRays, cam_.x, cam_.z);
        }
        // ---- matrices (jittered projection for TAA)
        projJ_.set(cam_.proj);
        jitter_[0] = jitter_[1] = 0;
        if (q_.taa) {
            const int j = static_cast<int>(frameNo_ % 8);
            projJ_.m[8] += (HALTON_X[j] - 0.5f) * 2.0f / static_cast<float>(rw_);
            projJ_.m[9] += (HALTON_Y[j] - 0.5f) * 2.0f / static_cast<float>(rh_);
            // (the image moves by minus that: each pixel's sample lies this many render texels from its centre)
            jitter_[0] = HALTON_X[j] - 0.5f; jitter_[1] = HALTON_Y[j] - 0.5f;
        }
        pv_.set(cam_.proj).mul(cam_.view);
        pvJ_.set(projJ_).mul(cam_.view);
        invPV_.invert(pv_);
        invPVJ_.invert(pvJ_);
        invProj_.invert(projJ_);
        invView_.invert(cam_.view);
        if (!historyValid_ || cam_.reset) prevPV_.set(pv_);
        frustum_.update(pvJ_);
        // (stamp i + 1 closes part i of ASTRATTO_GPU_*, in the order they run)
        stamp(ASTRATTO_GPU_UPLOADS + 1);
        shadowPass(w);
        stamp(ASTRATTO_GPU_SHADOWS + 1);
        gbufferPass(w);
        stamp(ASTRATTO_GPU_GBUFFER + 1);
        cullLights();
        if (q_.gtao) aoPass();
        stamp(ASTRATTO_GPU_AO + 1);
        if (q_.ssr && historyValid_) ssrPass();
        stamp(ASTRATTO_GPU_SSR + 1);
        lightingPass();
        stamp(ASTRATTO_GPU_LIGHTING + 1);
        fogPass();
        stamp(ASTRATTO_GPU_FOG + 1);
        forwardPass(w);
        stamp(ASTRATTO_GPU_FORWARD + 1);
        resolve(windowW, windowH);
        stamp(ASTRATTO_GPU_RESOLVE + 1);
        prevPV_.set(pv_);
        historyValid_ = true;
    } else {
        g_.BindFramebuffer(FRAMEBUFFER, 0);
        g_.Viewport(0, 0, windowW, windowH);
        g_.ClearColor(clear_[0], clear_[1], clear_[2], clear_[3]);
        g_.Clear(COLOR_BUFFER_BIT | DEPTH_BUFFER_BIT);
        if (w != nullptr) meshColumns(engine, *w, 512);       // a world with no camera yet (loading): meshed ahead
    }
    quadsPass(windowW, windowH);
    stamp(ASTRATTO_GPU_2D + 1);
    fences_[frameNo_ % (MAX_FRAMES_AHEAD + 1)] = g_.FenceSync(gl::SYNC_GPU_COMMANDS_COMPLETE, 0);
    // leave the state a caller's own 2D layer would expect
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    g_.Viewport(0, 0, windowW, windowH);
    g_.DepthRange(0, 1);
    g_.DepthMask(TRUE_);
    g_.Disable(BLEND);
    g_.Enable(DEPTH_TEST);
    g_.Enable(CULL_FACE);
    g_.ActiveTexture(TEXTURE0);
    g_.UseProgram(0);
    io.columns_meshed = columnsMeshed_; io.columns_drawn = columnsDrawn_; io.quads_drawn = quadsDrawn_; io.models_drawn = modelsDrawn_;
    io.lights = static_cast<uint32_t>(lightCount_); io.probes_updated = static_cast<uint32_t>(probesUpdated_); io.probes_unsettled = static_cast<uint32_t>(probesUnsettled_);
    io.columns_pending = static_cast<uint32_t>(pending_.size());
    io.settled = w != nullptr ? probesSettledNear(cam_.x, cam_.z, 96) : 0.0f;
    io.cpu_ms = std::chrono::duration<float, std::milli>(std::chrono::steady_clock::now() - t0).count();
    if (io.struct_size >= sizeof(astratto_render_io)) {
        io.gpu_ms = gpuMs_;
        for (int i = 0; i < ASTRATTO_GPU_PASSES; i++) io.pass_gpu_ms[i] = passGpuMs_[i];
    }
    return error_.empty() ? ASTRATTO_OK : ASTRATTO_ERR_INTERNAL;
}

// ------------------------------------------------------------------ the leaf bake (2.5)
void Renderer::bakeLeaves() {
    // sixteen cards of leaves, cut once by the procedure (leaf_bake.frag) instead of per fragment in every pass
    constexpr int S = 64, N = 16, LEVELS = 7;
    g_.GenTextures(1, &leafTex_);
    g_.BindTexture(TEXTURE_2D_ARRAY, leafTex_);
    g_.TexStorage3D(TEXTURE_2D_ARRAY, LEVELS, RG8, S, S, N);
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_MIN_FILTER, static_cast<GLint>(LINEAR_MIPMAP_LINEAR));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_MAG_FILTER, static_cast<GLint>(LINEAR));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
    g_.TexParameteri(TEXTURE_2D_ARRAY, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
    GLuint fb = 0;
    g_.GenFramebuffers(1, &fb);
    g_.BindFramebuffer(FRAMEBUFFER, fb);
    g_.Viewport(0, 0, S, S);
    g_.Disable(DEPTH_TEST);
    g_.Disable(BLEND);
    leafBake_.use();
    for (int i = 0; i < N; i++) {
        g_.FramebufferTextureLayer(FRAMEBUFFER, COLOR_ATTACHMENT0, leafTex_, 0, i);
        leafBake_.i1("uLayer", i);
        fullscreen();
    }
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    g_.DeleteFramebuffers(1, &fb);
    g_.BindTexture(TEXTURE_2D_ARRAY, leafTex_);
    g_.GenerateMipmap(TEXTURE_2D_ARRAY);
    g_.BindTexture(TEXTURE_2D_ARRAY, 0);
    g_.Enable(DEPTH_TEST);
}

// ------------------------------------------------------------------ GPU timing
void Renderer::stamp(int i) {
    if (!timersMade_) { g_.GenQueries(TIMER_FRAMES * TIMER_STAMPS, &timerQ_[0][0]); timersMade_ = true; }
    const int f = static_cast<int>(frameNo_ % TIMER_FRAMES);
    g_.QueryCounter(timerQ_[f][i], gl::TIMESTAMP);
    timerUsed_[f][i] = true;
}

void Renderer::readTimers() {
    // the oldest frame of the ring (written TIMER_FRAMES - 1 frames ago): read it if the GPU has finished it, never wait
    if (!timersMade_) return;
    const int f = static_cast<int>((frameNo_ + 1) % TIMER_FRAMES);
    if (!timerUsed_[f][0] || !timerUsed_[f][TIMER_STAMPS - 1]) return;
    gl::GLint ready = 0;
    g_.GetQueryObjectiv(timerQ_[f][TIMER_STAMPS - 1], gl::QUERY_RESULT_AVAILABLE, &ready);
    if (!ready) return;
    gl::GLuint64 t[TIMER_STAMPS] = {};
    for (int i = 0; i < TIMER_STAMPS; i++) if (timerUsed_[f][i]) g_.GetQueryObjectui64v(timerQ_[f][i], gl::QUERY_RESULT, &t[i]);
    // a pass skipped this frame took no time: carry the stamp before it forward
    for (int i = 1; i < TIMER_STAMPS; i++) if (!timerUsed_[f][i]) t[i] = t[i - 1];
    for (int i = 0; i < ASTRATTO_GPU_PASSES; i++) passGpuMs_[i] = static_cast<float>(t[i + 1] >= t[i] ? t[i + 1] - t[i] : 0) * 1e-6f;
    gpuMs_ = static_cast<float>(t[TIMER_STAMPS - 1] - t[0]) * 1e-6f;
    for (int i = 0; i < TIMER_STAMPS; i++) timerUsed_[f][i] = false;
}

void Renderer::fullscreen() { g_.BindVertexArray(fsVao_); g_.DrawArrays(TRIANGLES, 0, 3); }

// ------------------------------------------------------------------ shadows
void Renderer::shadowPass(VoxelWorld*) {
    const int n = q_.cascades;
    // (0.2.1) no further than the world is drawn: past the fog bank a shadow is never seen, and the cascades' texels
    // are better spent near the camera
    const float nearP = 0.2f, farP = std::max(40.0f, std::min(q_.shadowDistance, cam_.viewDistance * 0.85f));
    // practical split scheme: mostly logarithmic, so the first cascade stays tight around the camera
    for (int i = 0; i < n; i++) {
        const float k = static_cast<float>(i + 1) / static_cast<float>(n);
        const float lg = nearP * static_cast<float>(std::pow(static_cast<double>(farP / nearP), static_cast<double>(k))), uni = nearP + (farP - nearP) * k;
        splits_[i] = lg * 0.82f + uni * 0.18f;
    }
    for (int i = n; i < 4; i++) splits_[i] = splits_[n - 1];
    const bool lit = sunCol_[0] + sunCol_[1] + sunCol_[2] > 1e-4f;
    g_.Enable(DEPTH_TEST);
    g_.DepthMask(TRUE_);
    g_.DepthFunc(LEQUAL);
    g_.Disable(BLEND);
    g_.Enable(POLYGON_OFFSET_FILL);
    g_.PolygonOffset(1.6f, 3.0f);
    g_.Viewport(0, 0, shadowRes_, shadowRes_);
    const float tanY = static_cast<float>(std::tan(static_cast<double>(cam_.fovY * 0.5f))), tanX = tanY * cam_.aspect;
    const float lx = sunDir_[0], ly = sunDir_[1], lz = sunDir_[2];
    const float ax = std::fabs(ly) > 0.95f ? 1.0f : 0.0f, ay = std::fabs(ly) > 0.95f ? 0.0f : 1.0f, az = 0;
    float sxr = ay * lz - az * ly, syr = az * lx - ax * lz, szr = ax * ly - ay * lx;
    const float sl = static_cast<float>(std::sqrt(static_cast<double>(sxr * sxr + syr * syr + szr * szr)));
    sxr /= sl; syr /= sl; szr /= sl;
    const float sux = ly * szr - lz * syr, suy = lz * sxr - lx * szr, suz = lx * syr - ly * sxr;
    Mat4 tmp, ortho;
    Frustum lightFrustum;
    for (int i = 0; i < n; i++) {
        // far cascades change slowly on screen: refresh them on alternate frames
        const bool due = cascadeAge_ < 0 || i == 0 || (i == 1 && (frameNo_ & 1) == 0) || (i == 2 && (frameNo_ & 3) == 1) || (i == 3 && (frameNo_ & 7) == 3);
        if (!due || !lit) continue;
        const float zn = i == 0 ? nearP : splits_[i - 1], zf = splits_[i];
        // bounding sphere of the frustum slice: stable under rotation, so the map does not shimmer when the view turns
        const float zc = (zn + zf) * 0.5f;
        float cx = cam_.x + cam_.fx * zc, cy = cam_.y + cam_.fy * zc, cz = cam_.z + cam_.fz * zc;
        const float hx = zf * tanX, hy = zf * tanY, dz = zf - zc;
        const float radius = static_cast<float>(std::sqrt(static_cast<double>(hx * hx + hy * hy + dz * dz))) * 1.02f;
        const float texel = 2 * radius / static_cast<float>(shadowRes_);
        // the sphere's centre snapped to whole shadow texels in light space
        float px = cx * sxr + cy * syr + cz * szr, py = cx * sux + cy * suy + cz * suz;
        const float pd = cx * lx + cy * ly + cz * lz;
        px = static_cast<float>(std::floor(static_cast<double>(px / texel))) * texel; py = static_cast<float>(std::floor(static_cast<double>(py / texel))) * texel;
        cx = sxr * px + sux * py + lx * pd; cy = syr * px + suy * py + ly * pd; cz = szr * px + suz * py + lz * pd;
        const float back = 220.0f;
        tmp.lookAt(cx + lx * back, cy + ly * back, cz + lz * back, cx, cy, cz, sux, suy, suz);
        shadowVP_[i].ortho(-radius, radius, -radius, radius, 1.0f, back + radius + 60.0f).mul(tmp);
        cascadeTexel_[i] = texel;
        cascadeDepth_[i] = back + radius + 60.0f - 1.0f;
        std::memcpy(shadowVPFlat_ + i * 16, shadowVP_[i].m, 64);
        g_.BindFramebuffer(FRAMEBUFFER, shadowFbo_[i]);
        g_.Clear(DEPTH_BUFFER_BIT);
        lightFrustum.update(shadowVP_[i]);
        g_.Enable(CULL_FACE);
        g_.CullFace(BACK);
        sWorld_.use();
        sWorld_.mat4("uPV", shadowVP_[i].m);
        sWorld_.f1("uTime", env_.time);
        sWorld_.f4("uWind", foliage_.wind_x, foliage_.wind_z, foliage_.time, foliage_.gust);
        sWorld_.tex("uAtlas", 0, TEXTURE_2D_ARRAY, textures_[atlasTex_].id);
        sWorld_.tex("uLeaves", 5, TEXTURE_2D_ARRAY, leafTex_);
        drawOpaque(sWorld_, lightFrustum, cam_.x, cam_.z, zf + 150, false);
        layersShadow(i);
        // models: the first three cascades are enough for anything that moves
        if (i < 3) {
            g_.Disable(CULL_FACE);
            sModel_.use();
            sModel_.mat4("uPV", shadowVP_[i].m);
            const float r2 = (radius + 4) * (radius + 4);
            for (const Model& m : models_) {
                if (m.flags & (ASTRATTO_MODEL_NO_SHADOW | ASTRATTO_MODEL_FORWARD | ASTRATTO_MODEL_VIEWMODEL)) continue;
                const float ex = m.m[12] - cam_.x, ez = m.m[14] - cam_.z;
                if (ex * ex + ez * ez > 90 * 90) continue;       // beyond this a person's shadow is a few pixels
                const float ddx = m.m[12] - cx, ddy = m.m[13] - cy, ddz = m.m[14] - cz;
                const float along = ddx * lx + ddy * ly + ddz * lz;
                if (ddx * ddx + ddy * ddy + ddz * ddz - along * along > r2) continue;
                if (!visibleModel(m, lightFrustum)) continue;
                sModel_.mat4("uModel", m.m);
                sModel_.f1("uDissolve", (m.flags & ASTRATTO_MODEL_DISSOLVE) ? 1.0f-m.par[3] : 0.0f);
                const ModelMesh& mm = meshes_[m.mesh];
                sModel_.f3("uVoxelOrigin", (mm.extent[0]-mm.center[0])*64, (mm.extent[1]-mm.center[1])*64, (mm.extent[2]-mm.center[2])*64);
                g_.BindVertexArray(mm.vao);
                g_.DrawArrays(TRIANGLES, 0, static_cast<GLsizei>(mm.count));
            }
        }
    }
    cascadeAge_ = 0;
    g_.Disable(POLYGON_OFFSET_FILL);
    g_.Enable(CULL_FACE);
}

// ------------------------------------------------------------------ G-buffer
void Renderer::gbufferPass(VoxelWorld* w) {
    g_.BindFramebuffer(FRAMEBUFFER, gFbo_);
    g_.Viewport(0, 0, rw_, rh_);
    g_.DepthRange(0, 1);
    g_.ClearColor(0, 0, 0, 0);
    g_.ClearDepth(1.0);
    g_.Clear(COLOR_BUFFER_BIT | DEPTH_BUFFER_BIT);
    g_.Enable(DEPTH_TEST);
    g_.DepthFunc(LEQUAL);
    g_.DepthMask(TRUE_);
    g_.Disable(BLEND);
    g_.Enable(CULL_FACE);
    g_.CullFace(BACK);
    g_.FrontFace(CCW);
    gWorld_.use();
    gWorld_.mat4("uPV", pvJ_.m);
    gWorld_.f1("uTime", env_.time);
    gWorld_.f4("uWind", foliage_.wind_x, foliage_.wind_z, foliage_.time, foliage_.gust);
    gWorld_.f1("uRain", env_.wet >= 0 ? env_.wet : env_.rain);
    gWorld_.f4("uWeather", env_.snow, env_.rain, 0, 0);
    gWorld_.tex("uAtlas", 0, TEXTURE_2D_ARRAY, textures_[atlasTex_].id);
    gWorld_.tex("uNoise", 1, TEXTURE_2D, textures_.count(noiseTex_) ? textures_[noiseTex_].id : white_);
    gWorld_.tex("uLayerMat", 2, TEXTURE_2D, layerMat_);
    gWorld_.tex("uLeaves", 5, TEXTURE_2D_ARRAY, leafTex_);
    gWorld_.f1("uAlphaCut", 0.5f);
    columnsDrawn_ = static_cast<uint32_t>(drawOpaque(gWorld_, frustum_, cam_.x, cam_.z, cam_.viewDistance, true));
    // the solid part of every pane - the casement - goes into the G-buffer too, so that it has depth (see forwardPass)
    gWorld_.f1("uAlphaCut", 0.92f);
    drawTranslucent(gWorld_, frustum_, cam_.x, cam_.z, cam_.viewDistance);
    gWorld_.f1("uAlphaCut", 0.5f);
    // what lies on the world's surfaces and what grows and stands on them, lit with it
    layersGBuffer();
    g_.Disable(CULL_FACE);
    gModel_.use();
    gModel_.mat4("uPV", pvJ_.m);
    drawModels(gModel_, false, w);
    // the first-person weapon and hands: their own projection, squeezed into the front of the depth range so nothing in
    // the world can clip them and they still occlude it
    bool anyViewmodel = false;
    for (const Model& m : models_) if (m.flags & ASTRATTO_MODEL_VIEWMODEL) { anyViewmodel = true; break; }
    if (anyViewmodel) {
        g_.DepthRange(0, 0.02);
        vmPV_.set(cam_.vmProj).mul(cam_.view);
        gModel_.mat4("uPV", vmPV_.m);
        drawModels(gModel_, true, w);
        g_.DepthRange(0, 1);
    }
    g_.Enable(CULL_FACE);
}

bool Renderer::visibleModel(const Model& model, const Frustum& frustum) const {
    auto found = meshes_.find(model.mesh);
    if (found == meshes_.end()) return false;
    const ModelMesh& mesh = found->second;
    float center[3], extent[3];
    for (int axis = 0; axis < 3; axis++) {
        center[axis] = model.m[12 + axis]; extent[axis] = 0;
        for (int k = 0; k < 3; k++) {
            center[axis] += model.m[k * 4 + axis] * mesh.center[k];
            extent[axis] += std::abs(model.m[k * 4 + axis]) * mesh.extent[k];
        }
    }
    // Enclosing the transformed box also handles nonuniform scales, reflections and shear conservatively.
    return frustum.sphere(center[0], center[1], center[2], std::sqrt(extent[0] * extent[0] + extent[1] * extent[1] + extent[2] * extent[2]) + ((model.flags & ASTRATTO_MODEL_FOLIAGE) ? 3.0f : 0.0f));
}

void Renderer::drawModels(Program& s, bool viewmodel, VoxelWorld* w) {
    s.f3("uFoliage",foliage_.wind_x,foliage_.wind_z,env_.time);
    float csky = 1, cblk = 0;
    if (viewmodel && w) {
        const int ix = static_cast<int>(std::floor(cam_.x)), iy = static_cast<int>(std::floor(cam_.y)), iz = static_cast<int>(std::floor(cam_.z));
        csky = static_cast<float>(w->sky(ix, iy, iz)) / 15.0f; cblk = static_cast<float>(w->blockLight(ix, iy, iz)) / 15.0f;
    }
    for (const Model& m : models_) {
        if (((m.flags & ASTRATTO_MODEL_VIEWMODEL) != 0) != viewmodel) continue;
        if (m.flags & (ASTRATTO_MODEL_FORWARD | ASTRATTO_MODEL_SHADOW_ONLY)) continue;
        if (!viewmodel && !visibleModel(m, frustum_)) continue;
        float sky = csky, blk = cblk;
        if (!viewmodel && w) {
            const int ix = static_cast<int>(std::floor(m.m[12])), iy = static_cast<int>(std::floor(m.m[13] + 0.6f)), iz = static_cast<int>(std::floor(m.m[14]));
            sky = static_cast<float>(w->sky(ix, iy, iz)) / 15.0f; blk = static_cast<float>(w->blockLight(ix, iy, iz)) / 15.0f;
        }
        s.mat4("uModel", m.m);
        const bool foliage = (m.flags & ASTRATTO_MODEL_FOLIAGE) != 0;
        s.f1("uSway", foliage ? 1.0f : 0.0f);
        if (foliage) {
            // 2.6: what bends a plant: the bodies standing in it now, and the frame's pushers (the game's trail of where
            // bodies passed a moment ago, shrinking as the stems spring back)
            float benders[16 * 4] = {}; int count = 0;
            const ModelMesh& mesh = meshes_[m.mesh];
            const float cx = m.m[12] + mesh.center[0], cz = m.m[14] + mesh.center[2];
            for (int i = 0; i < bodyCount_ && count < 16; i++) {
                const float* body = bodies_ + i * 5;
                if (std::abs(body[0]-cx)>mesh.extent[0]+1.2f || std::abs(body[2]-cz)>mesh.extent[2]+1.2f) continue;
                std::copy(body, body+4, benders+count*4); ++count;
            }
            for (uint32_t i = 0; i < std::min<uint32_t>(8, foliage_.push_count) && count < 16; i++) {
                const float* p = foliage_.push + i * 4;   // x, z, radius, y
                if (p[2] <= 0 || std::abs(p[0]-cx)>mesh.extent[0]+1.2f || std::abs(p[1]-cz)>mesh.extent[2]+1.2f) continue;
                float* o = benders + count * 4; o[0] = p[0]; o[1] = p[3]; o[2] = p[1]; o[3] = p[2]; ++count;
            }
            s.i1("uBendCount", count);
            for (int i = 0; i < count; i++) { const std::string name = "uBenders[" + std::to_string(i) + "]"; const float* b = benders+i*4; s.f4(name.c_str(), b[0],b[1],b[2],b[3]); }
        }
        s.f4("uTint", m.par[0], m.par[1], m.par[2], m.par[3]);
        s.f1("uEmissive", m.par[4]);
        s.f1("uDissolve", (m.flags & ASTRATTO_MODEL_DISSOLVE) ? 1.0f-m.par[3] : 0.0f);
        s.f1("uFlag", m.par[4] > 0.5f ? 2.0f : (viewmodel ? 1.0f : 0.0f));
        s.f4("uMatParams", m.par[6], m.par[7], sky, blk);
        const ModelMesh& mm = meshes_[m.mesh];
        s.f3("uVoxelOrigin", (mm.extent[0]-mm.center[0])*64, (mm.extent[1]-mm.center[1])*64, (mm.extent[2]-mm.center[2])*64);
        g_.BindVertexArray(mm.vao);
        g_.DrawArrays(TRIANGLES, 0, static_cast<GLsizei>(mm.count));
        modelsDrawn_++;
    }
}

// ------------------------------------------------------------------ light culling, AO, reflections
void Renderer::cullLights() {
    g_.BindBuffer(SHADER_STORAGE_BUFFER, lightBuf_);
    g_.BufferData(SHADER_STORAGE_BUFFER, static_cast<GLsizeiptr>(MAX_LIGHTS) * 48, nullptr, DYNAMIC_DRAW);
    if (lightCount_ > 0) g_.BufferSubData(SHADER_STORAGE_BUFFER, 0, static_cast<GLsizeiptr>(lightCount_) * 48, lights_.data());
    g_.BindBuffer(SHADER_STORAGE_BUFFER, 0);
    g_.BindBufferBase(SHADER_STORAGE_BUFFER, 0, lightBuf_);
    g_.BindBufferBase(SHADER_STORAGE_BUFFER, 1, tileBuf_);
    cull_.use();
    cull_.tex("uDepth", 0, TEXTURE_2D, gDepth_);
    cull_.mat4("uInvProj", invProj_.m);
    cull_.mat4("uView", cam_.view.m);
    cull_.i2("uTiles", tilesX_, tilesY_);
    cull_.i2("uScreen", rw_, rh_);
    cull_.i1("uTileSize", TILE);
    cull_.i1("uLightCount", lightCount_);
    g_.DispatchCompute(static_cast<GLuint>((tilesX_ + 7) / 8), static_cast<GLuint>((tilesY_ + 7) / 8), 1);
    g_.MemoryBarrier(SHADER_STORAGE_BARRIER_BIT);
}

void Renderer::aoPass() {
    g_.Disable(DEPTH_TEST);
    g_.DepthMask(FALSE_);
    g_.BindFramebuffer(FRAMEBUFFER, aoRawFbo_);
    g_.Viewport(0, 0, rw_ / 2, rh_ / 2);
    gtao_.use();
    gtao_.tex("uDepth", 0, TEXTURE_2D, gDepth_);
    gtao_.tex("uNormal", 1, TEXTURE_2D, gNormal_);
    gtao_.mat4("uInvProj", invProj_.m);
    gtao_.mat4("uView", cam_.view.m);
    gtao_.f2("uScreen", static_cast<float>(rw_ / 2), static_cast<float>(rh_ / 2));
    gtao_.f1("uFrame", q_.taa ? static_cast<float>(frameNo_ % 16) : 0.0f);
    gtao_.f1("uRadius", 1.6f);
    gtao_.f1("uProjScale", 0.5f / static_cast<float>(std::tan(static_cast<double>(cam_.fovY * 0.5f))));
    fullscreen();
    g_.BindFramebuffer(FRAMEBUFFER, aoFbo_);
    g_.Viewport(0, 0, rw_, rh_);
    aoBlur_.use();
    aoBlur_.tex("uAO", 0, TEXTURE_2D, aoRaw_);
    aoBlur_.tex("uDepth", 1, TEXTURE_2D, gDepth_);
    aoBlur_.f2("uScreen", static_cast<float>(rw_ / 2), static_cast<float>(rh_ / 2));
    aoBlur_.f1("uNear", cam_.nearP);
    aoBlur_.f1("uFar", cam_.farP);
    fullscreen();
}

void Renderer::ssrPass() {
    g_.Disable(DEPTH_TEST);
    g_.DepthMask(FALSE_);
    g_.BindFramebuffer(FRAMEBUFFER, ssrFbo_);
    g_.Viewport(0, 0, rw_ / 2, rh_ / 2);
    ssr_.use();
    ssr_.tex("uDepth", 0, TEXTURE_2D, gDepth_);
    ssr_.tex("uNormal", 1, TEXTURE_2D, gNormal_);
    ssr_.tex("uMat", 2, TEXTURE_2D, gMat_);
    ssr_.tex("uHistory", 3, TEXTURE_2D, hist_[histIdx_]);
    ssr_.mat4("uProj", projJ_.m);
    ssr_.mat4("uInvProj", invProj_.m);
    ssr_.mat4("uView", cam_.view.m);
    ssr_.mat4("uInvView", invView_.m);
    ssr_.mat4("uPrevPV", prevPV_.m);
    ssr_.f2("uScreen", static_cast<float>(rw_ / 2), static_cast<float>(rh_ / 2));
    ssr_.f1("uFrame", q_.taa ? static_cast<float>(frameNo_ % 16) : 0.0f);
    fullscreen();
}

// ------------------------------------------------------------------ lighting
void Renderer::lightingPass() {
    g_.Disable(DEPTH_TEST);
    g_.DepthMask(FALSE_);
    g_.BindFramebuffer(FRAMEBUFFER, hdrAFbo_);
    g_.Viewport(0, 0, rw_, rh_);
    Program& s = lighting_;
    s.use();
    s.tex("uAlbedo", 0, TEXTURE_2D, gAlbedo_);
    s.tex("uNormal", 1, TEXTURE_2D, gNormal_);
    s.tex("uMat", 2, TEXTURE_2D, gMat_);
    s.tex("uDepth", 3, TEXTURE_2D, gDepth_);
    s.tex("uAO", 4, TEXTURE_2D, ao_);
    s.tex("uSSR", 5, TEXTURE_2D, ssrTex_);
    s.tex("uShadow", 6, TEXTURE_2D_ARRAY, shadowTex_);
    // 2.4: the same maps read as plain depths, for the soft shadows' blocker search (a sampler object without the
    // comparison, on a unit of its own)
    if (rawShadowSampler_ == 0) {
        g_.GenSamplers(1, &rawShadowSampler_);
        g_.SamplerParameteri(rawShadowSampler_, TEXTURE_MIN_FILTER, static_cast<GLint>(NEAREST));
        g_.SamplerParameteri(rawShadowSampler_, TEXTURE_MAG_FILTER, static_cast<GLint>(NEAREST));
        g_.SamplerParameteri(rawShadowSampler_, TEXTURE_WRAP_S, static_cast<GLint>(CLAMP_TO_EDGE));
        g_.SamplerParameteri(rawShadowSampler_, TEXTURE_WRAP_T, static_cast<GLint>(CLAMP_TO_EDGE));
        g_.SamplerParameteri(rawShadowSampler_, TEXTURE_COMPARE_MODE, static_cast<GLint>(NONE));
    }
    s.tex("uShadowRaw", 13, TEXTURE_2D_ARRAY, shadowTex_);
    g_.BindSampler(13, rawShadowSampler_);
    s.f4("uCascadeDepth", cascadeDepth_[0], cascadeDepth_[1], cascadeDepth_[2], cascadeDepth_[3]);
    s.f1("uFrame", q_.taa ? static_cast<float>(frameNo_ & 1023) : 0.0f);
    probeUniforms(s, 7);
    s.tex("uProbeVis", 10, TEXTURE_3D, probeVis_);
    s.tex("uVox", 11, TEXTURE_3D, voxTex_);
    s.uiv("uOpaque", opaqueMask_, 8);
    s.mat4("uInvPV", invPVJ_.m);
    s.mat4v("uShadowVP", shadowVPFlat_, 4);
    s.f4("uSplits", splits_[0], splits_[1], splits_[2], splits_[3]);
    s.f4("uCascadeTexel", cascadeTexel_[0], cascadeTexel_[1], cascadeTexel_[2], cascadeTexel_[3]);
    s.i1("uCascades", q_.cascades);
    s.i1("uTileSize", TILE);
    s.i2("uTiles", tilesX_, tilesY_);
    s.i1("uShadowedLights", q_.shadowedLights);
    s.i1("uBodyCount", bodyCount_);
    if (bodyCount_ > 0) s.fv("uBodies", bodies_, MAX_BODIES * 5);
    s.i1("uUseAO", q_.gtao ? 1 : 0);
    s.i1("uUseSSR", q_.ssr && historyValid_ ? 1 : 0);
    s.f3("uCam", cam_.x, cam_.y, cam_.z);
    s.f3("uCamFwd", cam_.fx, cam_.fy, cam_.fz);
    s.f3("uTint", 0.6f + 0.4f * env_.tint[0], 0.6f + 0.4f * env_.tint[1], 0.6f + 0.4f * env_.tint[2]);
    s.f1("uShadowRes", static_cast<float>(shadowRes_));
    s.f1("uAmbientFloor", env_.ambientFloor);
    s.tex("uExposure", 12, TEXTURE_2D, expoTex_[expoIdx_]);
    skyUniforms(s);
    fullscreen();
    g_.BindSampler(13, 0);
}

void Renderer::fogPass() {
    const float cloudDt = cloudClock_ < 0 ? 0.0f : std::clamp(env_.time - cloudClock_, 0.0f, 0.25f);
    cloudClock_ = env_.time;
    cloudX_ += foliage_.wind_x * cloudDt * 0.08f;
    cloudZ_ += foliage_.wind_z * cloudDt * 0.08f;
    const float density = env_.fogDensity * 0.62f;
    if (q_.volumetrics) {
        g_.BindFramebuffer(FRAMEBUFFER, fogFbo_);
        g_.Viewport(0, 0, fogW_, fogH_);
        vol_.use();
        vol_.tex("uDepth", 0, TEXTURE_2D, gDepth_);
        vol_.tex("uShadow", 1, TEXTURE_2D_ARRAY, shadowTex_);
        vol_.mat4("uInvPV", invPVJ_.m);
        vol_.mat4v("uShadowVP", shadowVPFlat_, 4);
        vol_.f4("uSplits", splits_[0], splits_[1], splits_[2], splits_[3]);
        vol_.i1("uCascades", q_.cascades);
        vol_.i1("uLightCount", lightCount_);
        vol_.i1("uSteps", q_.volumetricSteps);
        vol_.f3("uCam", cam_.x, cam_.y, cam_.z);
        vol_.f3("uCamFwd", cam_.fx, cam_.fy, cam_.fz);
        vol_.f3("uSunDir", sunDir_[0], sunDir_[1], sunDir_[2]);
        vol_.f3("uSunCol", sunCol_[0], sunCol_[1], sunCol_[2]);
        vol_.f3("uFogCol", fogCol_[0], fogCol_[1], fogCol_[2]);
        vol_.f3("uAmbient", ambient_[0], ambient_[1], ambient_[2]);
        vol_.f1("uDensity", density);
        vol_.f1("uFrame", q_.taa ? static_cast<float>(frameNo_ % 16) : 0.0f);
        vol_.f1("uHeightBase", groundY_);
        vol_.f1("uMaxDist", 140.0f);
        vol_.f1("uTime", env_.time);
        vol_.f1("uCloudCover", std::max(0.0f,env_.overcast));
        vol_.f2("uCloudOffset",cloudX_,cloudZ_);
        // the smoke volumes as three vec4 each: (x y z radius) (density r g b) (glow rise swirl seed)
        const int smokeN = static_cast<int>(smoke_.size() / 12);
        vol_.i1("uSmokeN", smokeN);
        if (smokeN > 0) g_.Uniform4fv(vol_.loc("uSmoke"), smokeN * 3, smoke_.data());
        vol_.i1("uSteps", q_.volumetricSteps);
        probeUniforms(vol_, 3);
        fullscreen();
    }
    g_.BindFramebuffer(FRAMEBUFFER, hdrBColorFbo_);
    g_.Viewport(0, 0, rw_, rh_);
    fogApply_.use();
    fogApply_.tex("uColor", 0, TEXTURE_2D, hdrA_);
    fogApply_.tex("uFog", 1, TEXTURE_2D, fogTex_);
    fogApply_.tex("uDepth", 2, TEXTURE_2D, gDepth_);
    fogApply_.f2("uFogSize", static_cast<float>(fogW_), static_cast<float>(fogH_));
    fogApply_.i1("uUseFog", q_.volumetrics ? 1 : 0);
    fogApply_.mat4("uInvPV", invPVJ_.m);
    fogApply_.f3("uCam", cam_.x, cam_.y, cam_.z);
    fogApply_.f3("uFogCol", fogCol_[0], fogCol_[1], fogCol_[2]);
    fogApply_.f3("uAmbient", ambient_[0], ambient_[1], ambient_[2]);
    fogApply_.f1("uDensity", density);
    fogApply_.f1("uHeightBase", groundY_);
    fogApply_.f1("uMaxDist", 140.0f);
    fogApply_.f1("uNear", cam_.nearP);
    fogApply_.f1("uFar", cam_.farP);
    skyUniforms(fogApply_);
    bankUniforms(fogApply_);
    fullscreen();
}

// ------------------------------------------------------------------ forward
void Renderer::advanceWater(VoxelWorld* world) {
    if(!world)return;
    // 1.2 (gamefixes: water did not really ripple for what moved in it): the waves live on a grid of fifth-of-a-metre cells
    // that follows the camera - one grid over the whole map had cells a metre wide, too coarse to carry a body's ring at
    // all. The grid's corner keeps to an 8 m lattice; when the camera takes it to a new corner the wave state is carried
    // across by whole cells (WATER_SHIFT), so rings already spreading are not lost.
    constexpr int N=256;constexpr float CELL=WATER_CELL,SPAN=CELL*N,SNAP=8.0f;
    const float ox=std::floor((cam_.x-SPAN*0.5f)/SNAP)*SNAP,oz=std::floor((cam_.z-SPAN*0.5f)/SNAP)*SNAP;
    const bool moved=!waterPlaced_ || ox!=waterOrigin_[0] || oz!=waterOrigin_[1];
    int shiftX=0,shiftZ=0;
    if(moved && waterPlaced_) { shiftX=static_cast<int>(std::lround((ox-waterOrigin_[0])/CELL));shiftZ=static_cast<int>(std::lround((oz-waterOrigin_[1])/CELL)); }
    const bool fresh=waterVersions_.size()!=static_cast<size_t>(CX_*CZ_);
    if(fresh) waterVersions_.assign(static_cast<size_t>(CX_*CZ_),0);
    // a changed wall or burning log under the grid invalidates it (column versions; never a scan of the whole map)
    bool changed=moved || fresh;
    const int bx0=static_cast<int>(std::floor(ox)),bz0=static_cast<int>(std::floor(oz)),bx1=bx0+static_cast<int>(std::ceil(SPAN)),bz1=bz0+static_cast<int>(std::ceil(SPAN));
    for(int cx=std::max(0,bx0/16);cx<=std::min(CX_-1,bx1/16);cx++)for(int cz=std::max(0,bz0/16);cz<=std::min(CZ_-1,bz1/16);cz++) {
        const size_t column=static_cast<size_t>(cx*CZ_+cz);const uint32_t version=world->columnVersion(cx,cz);
        if(waterVersions_[column]!=version) { waterVersions_[column]=version;changed=true; }
    }
    waterOrigin_[0]=ox;waterOrigin_[1]=oz;waterPlaced_=true;
    if(changed) {
        waterMaskData_.assign(static_cast<size_t>(N*N*4),0.0f);waterCells_=0;
        // each block column under the grid once: its water surface, the sky over it, into the cells it covers
        for(int bx=bx0;bx<bx1;bx++)for(int bz=bz0;bz<bz1;bz++) {
            if(bx<0 || bz<0 || bx>=sizeX_ || bz>=sizeZ_)continue;
            for(int y=sizeY_-1;y>=0;y--)if(world->def(bx,y,bz).flags&ASTRATTO_BLOCK_LIQUID) {
                const float top=static_cast<float>(y+1),sky=static_cast<float>(world->sky(bx,y+1,bz))/15.0f;
                const int x0=std::max(0,static_cast<int>(std::ceil((bx-ox)/CELL-0.5f))),x1=std::min(N,static_cast<int>(std::ceil((bx+1-ox)/CELL-0.5f)));
                const int z0=std::max(0,static_cast<int>(std::ceil((bz-oz)/CELL-0.5f))),z1=std::min(N,static_cast<int>(std::ceil((bz+1-oz)/CELL-0.5f)));
                for(int x=x0;x<x1;x++)for(int z=z0;z<z1;z++) {
                    const size_t at=static_cast<size_t>((z*N+x)*4);
                    waterMaskData_[at]=top;waterMaskData_[at+1]=1;waterMaskData_[at+2]=sky;waterCells_++;
                }
                break;
            }
        }
        g_.BindTexture(TEXTURE_2D,waterMask_);g_.TexSubImage2D(TEXTURE_2D,0,0,0,N,N,RGBA,FLOAT,waterMaskData_.data());
    }
    hasWater_=waterCells_>0;
    if(!hasWater_)return;
    waterAccumulator_+=std::min(0.066f,std::max(0.0f,cam_.dt));
    constexpr float STEP=1.0f/120.0f;
    int steps=std::min(8,static_cast<int>(waterAccumulator_/STEP));
    if((shiftX!=0 || shiftZ!=0) && steps==0) { steps=1;waterAccumulator_+=STEP; }   // the carry rides on a step
    waterAccumulator_-=static_cast<float>(steps)*STEP;
    waterWaves_.use();waterWaves_.tex("uMask",0,TEXTURE_2D,waterMask_);
    waterWaves_.f2("uOrigin",ox,oz);waterWaves_.f1("uCell",CELL);
    waterWaves_.f1("uDt",STEP);waterWaves_.f1("uRain",env_.rain);
    // a ripple's push is given over this frame's steps, so it is as strong at any frame rate
    waterWaves_.f1("uImpulseScale",steps>0?1.0f/static_cast<float>(steps):0.0f);
    waterWaves_.i1("uImpulses",static_cast<int>(ripples_.size()/4));
    if(!ripples_.empty())g_.Uniform4fv(waterWaves_.loc("uImpulse"),static_cast<GLsizei>(ripples_.size()/4),ripples_.data());
    for(int i=0;i<steps;i++) {
        waterClock_+=STEP;waterWaves_.f1("uTime",waterClock_);
        waterWaves_.i2("uShift",i==0?shiftX:0,i==0?shiftZ:0);
        g_.BindImageTexture(0,water_[waterIndex_],0,FALSE_,0,READ_WRITE,RGBA16F);
        g_.BindImageTexture(1,water_[1-waterIndex_],0,FALSE_,0,READ_WRITE,RGBA16F);
        g_.DispatchCompute(32,32,1);g_.MemoryBarrier(SHADER_IMAGE_ACCESS_BARRIER_BIT|TEXTURE_FETCH_BARRIER_BIT);
        waterIndex_=1-waterIndex_;
    }
}

void Renderer::advanceChemicalParticles() {
    chemistryQuiet_ = emitters_.empty() ? chemistryQuiet_ + cam_.dt : 0;
    if (chemistryQuiet_ > 8) chemistryAlive_ = false;
    if (emitters_.empty() && !chemistryAlive_) return;
    if (cam_.reset) {
        std::vector<float> zero(4096*16,0);
        g_.BindBuffer(SHADER_STORAGE_BUFFER,chemistryVbo_);g_.BufferSubData(SHADER_STORAGE_BUFFER,0,4096*48,zero.data());
        g_.BindBuffer(SHADER_STORAGE_BUFFER,chemistryState_);g_.BufferSubData(SHADER_STORAGE_BUFFER,0,4096*64,zero.data());
    }
    const int count = presetLevel_ <= 0 ? 1024 : presetLevel_ == 1 ? 2048 : 4096;
    chemistryParticles_.use();
    chemistryParticles_.i1("uCount",count); chemistryParticles_.i1("uEmitters",static_cast<int>(emitters_.size()/20));
    chemistryParticles_.tex("uVox",0,TEXTURE_3D,voxTex_);
    chemistryParticles_.uiv("uCollision",particleCollision_,8);
    chemistryParticles_.f1("uDt",cam_.dt); chemistryParticles_.f1("uTime",env_.time); chemistryParticles_.f2("uWind",foliage_.wind_x,foliage_.wind_z);
    g_.BindBuffer(SHADER_STORAGE_BUFFER,chemistrySources_);
    if(!emitters_.empty())g_.BufferSubData(SHADER_STORAGE_BUFFER,0,static_cast<GLsizeiptr>(emitters_.size()*sizeof(float)),emitters_.data());
    g_.BindBufferBase(SHADER_STORAGE_BUFFER,6,chemistryVbo_);g_.BindBufferBase(SHADER_STORAGE_BUFFER,7,chemistryState_);g_.BindBufferBase(SHADER_STORAGE_BUFFER,5,chemistrySources_);
    g_.DispatchCompute(static_cast<GLuint>((count+63)/64),1,1);
    g_.MemoryBarrier(SHADER_STORAGE_BARRIER_BIT|VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
    chemistryAlive_=true;
}

void Renderer::forwardPass(VoxelWorld* world) {
    advanceWater(world);
    const float density = env_.fogDensity * 0.62f;
    g_.BindFramebuffer(FRAMEBUFFER, hdrBFbo_);
    g_.Viewport(0, 0, rw_, rh_);
    g_.Enable(DEPTH_TEST);
    g_.DepthFunc(LEQUAL);
    g_.DepthMask(FALSE_);
    g_.Enable(BLEND);
    g_.BlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
    g_.Disable(CULL_FACE);
    // glass and water
    fWorld_.use();
    fWorld_.mat4("uPV", pvJ_.m);
    fWorld_.f1("uTime", env_.time);
    fWorld_.tex("uAtlas", 0, TEXTURE_2D_ARRAY, textures_[atlasTex_].id);
    // 2.4: water reflects the scene that is really there (the lit image before the fog, and its depth)
    fWorld_.tex("uSceneColor", 6, TEXTURE_2D, hdrA_);
    fWorld_.tex("uSceneDepth", 7, TEXTURE_2D, gDepth_);
    fWorld_.mat4("uInvPV", invPVJ_.m);
    fWorld_.i1("uWaterSSR", q_.ssr ? 1 : 0);
    fWorld_.tex("uWaves",8,TEXTURE_2D,water_[waterIndex_]);
    fWorld_.f3("uWaveGrid",waterOrigin_[0],waterOrigin_[1],WATER_CELL);
    fWorld_.tex("uWaterNoise",9,TEXTURE_2D,textures_.count(noiseTex_)?textures_[noiseTex_].id:white_);
    probeUniforms(fWorld_, 1);
    fWorld_.f3("uCam", cam_.x, cam_.y, cam_.z);
    fWorld_.f3("uFogCol", fogCol_[0], fogCol_[1], fogCol_[2]);
    fWorld_.f3("uAmbient", ambient_[0], ambient_[1], ambient_[2]);
    fWorld_.f1("uDensity", density);
    fWorld_.tex("uFogTex", 5, TEXTURE_2D, fogTex_);
    fWorld_.f2("uFogScreen", static_cast<float>(rw_), static_cast<float>(rh_));
    fWorld_.i1("uUseFog", q_.volumetrics ? 1 : 0);
    fWorld_.f1("uFogMax", 140.0f);
    fWorld_.f1("uFogBase", groundY_);
    bankUniforms(fWorld_);
    skyUniforms(fWorld_);
    // glass seen through glass: blended in a way that does not care about order - every pane first takes its share of the
    // light behind it away (multiplying), then every pane adds its own colour (adding)
    g_.BlendFunc(ZERO, ONE_MINUS_SRC_ALPHA);
    drawTranslucent(fWorld_, frustum_, cam_.x, cam_.z, cam_.viewDistance);
    g_.BlendFunc(SRC_ALPHA, ONE);
    drawTranslucent(fWorld_, frustum_, cam_.x, cam_.z, cam_.viewDistance);
    g_.BlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
    // see-through and self-lit models, far to near
    fModel_.use();
    fModel_.mat4("uPV", pvJ_.m);
    probeUniforms(fModel_, 1);
    fModel_.f3("uCam", cam_.x, cam_.y, cam_.z);
    fModel_.f3("uFogCol", fogCol_[0], fogCol_[1], fogCol_[2]);
    fModel_.f3("uAmbient", ambient_[0], ambient_[1], ambient_[2]);
    fModel_.f1("uDensity", density);
    skyUniforms(fModel_);
    bankUniforms(fModel_);
    std::vector<std::pair<float, size_t>> fwd;
    for (size_t k = 0; k < models_.size(); k++) {
        const Model& m = models_[k];
        if (!(m.flags & ASTRATTO_MODEL_FORWARD) || (m.flags & ASTRATTO_MODEL_VIEWMODEL)) continue;
        if (!visibleModel(m, frustum_)) continue;
        const float dx = m.m[12] - cam_.x, dy = m.m[13] - cam_.y, dz = m.m[14] - cam_.z;
        fwd.push_back({dx * dx + dy * dy + dz * dz, k});
    }
    std::stable_sort(fwd.begin(), fwd.end(), [](const std::pair<float, size_t>& a, const std::pair<float, size_t>& b) { return a.first > b.first; });
    for (int pass = 0; pass < 2; pass++) {
        if (pass == 1) g_.BlendFunc(SRC_ALPHA, ONE);
        for (const auto& o : fwd) {
            const Model& m = models_[o.second];
            if (((m.flags & ASTRATTO_MODEL_ADDITIVE) != 0) != (pass == 1)) continue;
            fModel_.mat4("uModel", m.m);
            fModel_.f4("uTint", m.par[0], m.par[1], m.par[2], m.par[3]);
            fModel_.f1("uEmissive", m.par[4]);
            fModel_.f1("uDissolve", (m.flags & ASTRATTO_MODEL_DISSOLVE) ? 1.0f-m.par[3] : 0.0f);
            const ModelMesh& mm = meshes_[m.mesh];
            fModel_.f3("uVoxelOrigin", (mm.extent[0]-mm.center[0])*64, (mm.extent[1]-mm.center[1])*64, (mm.extent[2]-mm.center[2])*64);
            g_.BindVertexArray(mm.vao);
            g_.DrawArrays(TRIANGLES, 0, static_cast<GLsizei>(mm.count));
            modelsDrawn_++;
        }
    }
    // particles: premultiplied, so fire adds and smoke covers in one sorted-enough pass
    const size_t pcount = particles_.size() / 12;
    advanceChemicalParticles();
    if (pcount > 0 || chemistryAlive_) {
        g_.BlendFunc(ONE, ONE_MINUS_SRC_ALPHA);
        particle_.use();
        particle_.mat4("uPV", pvJ_.m);
        const float* v = cam_.view.m;
        particle_.f3("uRight", v[0], v[4], v[8]);
        particle_.f3("uUp", v[1], v[5], v[9]);
        particle_.tex("uNoise", 0, TEXTURE_2D, textures_.count(noiseTex_) ? textures_[noiseTex_].id : white_);
        particle_.tex("uDepth", 1, TEXTURE_2D, gDepth_);
        particle_.f2("uScreen", static_cast<float>(rw_), static_cast<float>(rh_));
        particle_.f1("uTime", env_.time);
        particle_.f1("uNear", cam_.nearP);
        particle_.f1("uFar", cam_.farP);
        // depth is sampled in the shader for soft edges, so it cannot also be the depth attachment being tested
        g_.BindFramebuffer(FRAMEBUFFER, hdrBColorFbo_);
        g_.Disable(DEPTH_TEST);
        if (pcount > 0) {
        g_.BindVertexArray(particleVao_);
        g_.BindBuffer(ARRAY_BUFFER, particleVbo_);
        if (pcount > particleCap_) { while (particleCap_ < pcount) particleCap_ *= 2; }
        g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(particleCap_ * 48), nullptr, STREAM_DRAW);    // orphan, then fill
        g_.BufferSubData(ARRAY_BUFFER, 0, static_cast<GLsizeiptr>(pcount * 48), particles_.data());
        g_.DrawArraysInstanced(TRIANGLE_STRIP, 0, 4, static_cast<GLsizei>(pcount));
        }
        if (chemistryAlive_) {
            g_.BindVertexArray(chemistryVao_);
            g_.DrawArraysInstanced(TRIANGLE_STRIP,0,4,presetLevel_ <= 0 ? 1024 : presetLevel_ == 1 ? 2048 : 4096);
        }
        g_.BindVertexArray(0);
        g_.Enable(DEPTH_TEST);
    }
    g_.Disable(BLEND);
    g_.DepthMask(TRUE_);
}

// ------------------------------------------------------------------ TAA, bloom, exposure, tone map
void Renderer::resolve(int windowW, int windowH) {
    // ASTRATTO_PROFILE_RESOLVE: each step of the resolve timed (the GPU drained between steps: for measuring only)
    static const bool prof = [] {
#ifdef _MSC_VER
        char* v = nullptr; size_t n = 0;
        const bool on = _dupenv_s(&v, &n, "ASTRATTO_PROFILE_RESOLVE") == 0 && v != nullptr;
        std::free(v);
        return on;
#else
        return std::getenv("ASTRATTO_PROFILE_RESOLVE") != nullptr;
#endif
    }();
    static double acc[6] = {}; static int frames = 0;
    auto t = std::chrono::steady_clock::now();
    auto mark = [&](int i) { if (!prof) return; g_.Finish(); auto n = std::chrono::steady_clock::now(); acc[i] += std::chrono::duration<double, std::milli>(n - t).count(); t = n; };
    if (prof) g_.Finish(), t = std::chrono::steady_clock::now();
    g_.Disable(DEPTH_TEST);
    g_.DepthMask(FALSE_);
    const int cur = 1 - histIdx_;
    g_.BindFramebuffer(FRAMEBUFFER, histFbo_[cur]);
    g_.Viewport(0, 0, ow_, oh_);
    if (q_.taa && historyValid_ && !cam_.reset) {
        taa_.use();
        taa_.tex("uColor", 0, TEXTURE_2D, hdrB_);
        taa_.tex("uHistory", 1, TEXTURE_2D, hist_[histIdx_]);
        taa_.tex("uDepth", 2, TEXTURE_2D, gDepth_);
        taa_.tex("uNormal", 3, TEXTURE_2D, gNormal_);
        taa_.mat4("uInvPV", invPV_.m);
        taa_.mat4("uPrevPV", prevPV_.m);
        taa_.f2("uScreen", static_cast<float>(rw_), static_cast<float>(rh_));
        taa_.f2("uOut", static_cast<float>(ow_), static_cast<float>(oh_));
        taa_.f2("uJitter", jitter_[0], jitter_[1]);
        taa_.f1("uBlend", 0.1f);
    } else {
        copy_.use();
        copy_.tex("uTex", 0, TEXTURE_2D, hdrB_);
    }
    fullscreen();
    mark(0);
    histIdx_ = cur;
    GLuint sceneTex = hist_[cur];
    // 2.4: the lens - depth of field and motion blur, when asked for - into the colour target the fog pass is done with
    if (q_.motionBlur || q_.dof) {
        g_.BindFramebuffer(FRAMEBUFFER, lensFbo_);
        g_.Viewport(0, 0, ow_, oh_);
        lens_.use();
        lens_.tex("uColor", 0, TEXTURE_2D, sceneTex);
        lens_.tex("uDepth", 1, TEXTURE_2D, gDepth_);
        lens_.mat4("uInvPV", invPV_.m);
        lens_.mat4("uPrevPV", prevPV_.m);
        lens_.f2("uScreen", static_cast<float>(ow_), static_cast<float>(oh_));
        lens_.f1("uShutter", std::max(0.0f, std::min(1.0f, lens_cfg_.shutter)));
        lens_.f1("uFocus", lens_cfg_.focus);
        lens_.f1("uAperture", std::max(0.0f, lens_cfg_.aperture));
        lens_.f1("uMaxBlur", lens_cfg_.max_blur > 0 ? lens_cfg_.max_blur : 12.0f);
        lens_.f1("uNear", cam_.nearP);
        lens_.f1("uFar", cam_.farP);
        lens_.f1("uFrame", static_cast<float>(frameNo_ & 1023));
        lens_.i1("uMotion", q_.motionBlur && historyValid_ && !cam_.reset ? 1 : 0);
        lens_.i1("uDof", q_.dof ? 1 : 0);
        fullscreen();
        sceneTex = lensTex_;
    }
    mark(1);
    // bloom: threshold on the way down, tent filter on the way back up
    if (q_.bloom) {
        bloomDown_.use();
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            g_.BindFramebuffer(FRAMEBUFFER, bloomFbo_[i]);
            g_.Viewport(0, 0, bloomW_[i], bloomH_[i]);
            bloomDown_.tex("uTex", 0, TEXTURE_2D, i == 0 ? sceneTex : bloomTex_[i - 1]);
            bloomDown_.f2("uTexel", 1.0f / static_cast<float>(i == 0 ? ow_ : bloomW_[i - 1]), 1.0f / static_cast<float>(i == 0 ? oh_ : bloomH_[i - 1]));
            bloomDown_.f1("uThreshold", i == 0 ? 1.35f : 0.0f);
            bloomDown_.tex("uExposure", 1, TEXTURE_2D, expoTex_[expoIdx_]);
            fullscreen();
        }
        g_.Enable(BLEND);
        g_.BlendFunc(ONE, ONE);
        bloomUp_.use();
        for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
            g_.BindFramebuffer(FRAMEBUFFER, bloomFbo_[i - 1]);
            g_.Viewport(0, 0, bloomW_[i - 1], bloomH_[i - 1]);
            bloomUp_.tex("uTex", 0, TEXTURE_2D, bloomTex_[i]);
            bloomUp_.f2("uTexel", 1.0f / static_cast<float>(bloomW_[i]), 1.0f / static_cast<float>(bloomH_[i]));
            fullscreen();
        }
        g_.Disable(BLEND);
    }
    mark(2);
    // average scene luminance -> exposure that adapts over time (2.5: measured every other frame - the eye adapts over
    // seconds - and the adaptation run with the time of both)
    const bool measure = (frameNo_ & 1) == 0 || !historyValid_ || cam_.reset;
    if (measure) {
    g_.BindFramebuffer(FRAMEBUFFER, lumFbo_);
    g_.Viewport(0, 0, 256, 256);
    lum_.use();
    lum_.tex("uTex", 0, TEXTURE_2D, sceneTex);
    fullscreen();
    g_.BindTexture(TEXTURE_2D, lumTex_);
    g_.GenerateMipmap(TEXTURE_2D);
    const int ecur = 1 - expoIdx_;
    g_.BindFramebuffer(FRAMEBUFFER, expoFbo_[ecur]);
    g_.Viewport(0, 0, 1, 1);
    exposure_.use();
    exposure_.tex("uLum", 0, TEXTURE_2D, lumTex_);
    exposure_.tex("uPrev", 1, TEXTURE_2D, expoTex_[expoIdx_]);
    exposure_.f1("uDt", std::max(0.001f, cam_.dt * 2.0f));
    exposure_.f1("uMin", 0.55f);
    // the eye opens a long way into a dark room by day, but night stays night: moonlit ground is dim and lamp pools stand out
    exposure_.f1("uMax", 1.4f + 3.6f * std::max(0.0f, std::min(1.0f, env_.daylight)));
    exposure_.f1("uReset", historyValid_ && !cam_.reset ? 0.0f : 1.0f);
    fullscreen();
    expoIdx_ = ecur;
    }
    mark(3);
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    g_.Viewport(0, 0, windowW, windowH);
    tonemap_.use();
    tonemap_.tex("uColor", 0, TEXTURE_2D, sceneTex);
    tonemap_.tex("uBloom", 1, TEXTURE_2D, bloomTex_[0]);
    tonemap_.tex("uExposure", 2, TEXTURE_2D, expoTex_[expoIdx_]);
    tonemap_.f1("uBloomStrength", q_.bloom ? env_.bloom : 0.0f);
    tonemap_.f1("uTime", env_.time);
    tonemap_.f1("uDeath", grading_.death);
    tonemap_.f1("uGrain", 0.018f);
    tonemap_.f1("uFlash", grading_.flash);
    tonemap_.f1("uVignette", 0.55f);
    tonemap_.f1("uHurt", grading_.hurt);
    tonemap_.f1("uInfection", grading_.infection);
    tonemap_.f1("uBile", grading_.bile);
    tonemap_.f1("uSharpen", q_.taa ? 0.35f : 0.0f);
    tonemap_.f2("uScreen", static_cast<float>(ow_), static_cast<float>(oh_));
    fullscreen();
    mark(4);
    if (prof && ++frames % 120 == 0) {
        std::fprintf(stderr, "[resolve] taa %.2f lens %.2f bloom %.2f exposure %.2f tonemap %.2f ms\n", acc[0] / 120, acc[1] / 120, acc[2] / 120, acc[3] / 120, acc[4] / 120);
        for (double& a : acc) a = 0;
    }
}

// ------------------------------------------------------------------ 2D
void Renderer::quadsPass(int windowW, int windowH) {
    if (quads_.empty()) return;
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    g_.Viewport(0, 0, windowW, windowH);
    g_.Disable(DEPTH_TEST);
    g_.Disable(CULL_FACE);
    g_.Enable(BLEND);
    g_.BlendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
    Mat4 proj;
    proj.ortho(0, static_cast<float>(windowW), static_cast<float>(windowH), 0, -1, 1);
    ui_.use();
    ui_.mat4("uProj", proj.m);
    ui_.i1("uTex", 0);
    g_.BindVertexArray(quadVao_);
    g_.BindBuffer(ARRAY_BUFFER, quadVbo_);
    for (const Quads& qd : quads_) {
        if (qd.portrait >= 0) {
            portraitPass(portraits_[static_cast<size_t>(qd.portrait)], windowH);
            g_.Viewport(0, 0, windowW, windowH);
            g_.Disable(SCISSOR_TEST); g_.Disable(DEPTH_TEST); g_.DepthMask(TRUE_);
            ui_.use(); ui_.mat4("uProj", proj.m); ui_.i1("uTex", 0);
            g_.BindVertexArray(quadVao_); g_.BindBuffer(ARRAY_BUFFER, quadVbo_);
            continue;
        }
        const size_t n = qd.v.size() / 36;
        if (n == 0) continue;
        auto it = textures_.find(qd.texture);
        g_.ActiveTexture(TEXTURE0);
        g_.BindTexture(TEXTURE_2D, it != textures_.end() && it->second.target == TEXTURE_2D ? it->second.id : white_);
        if (n > quadCap_) { while (quadCap_ < n) quadCap_ *= 2; g_.BufferData(ARRAY_BUFFER, static_cast<GLsizeiptr>(quadCap_ * 4 * 36), nullptr, DYNAMIC_DRAW); }
        g_.BufferSubData(ARRAY_BUFFER, 0, static_cast<GLsizeiptr>(n * 4 * 36), qd.v.data());
        g_.DrawElements(TRIANGLES, static_cast<GLsizei>(n * 6), UNSIGNED_INT, nullptr);
    }
    g_.BindVertexArray(0);
    g_.Disable(BLEND);
    g_.Enable(DEPTH_TEST);
}

void Renderer::portraitPass(const Portrait& portrait, int windowH) {
    const int x = static_cast<int>(portrait.x), y = windowH - static_cast<int>(portrait.y + portrait.h);
    const int w = static_cast<int>(portrait.w), h = static_cast<int>(portrait.h);
    g_.Viewport(x, y, w, h); g_.Enable(SCISSOR_TEST); g_.Scissor(x, y, w, h);
    g_.Enable(DEPTH_TEST); g_.DepthMask(TRUE_); g_.ClearDepth(1); g_.Clear(DEPTH_BUFFER_BIT);
    Mat4 projection, view;
    const float halfWidth = 0.45f * portrait.w / portrait.h;
    projection.ortho(-halfWidth, halfWidth, -0.45f, 0.45f, 0.1f, 4);
    view.lookAt(0, 0, -2, 0, 0, 0, 0, 1, 0); projection.mul(view);
    portrait_.use(); portrait_.mat4("uPV", projection.m);
    for (const auto& part : portrait.parts) {
        auto mesh = meshes_.find(part.mesh);
        if (mesh == meshes_.end()) continue;
        portrait_.mat4("uModel", part.matrix); portrait_.f4("uTint", part.r, part.g, part.b, part.a);
        g_.BindVertexArray(mesh->second.vao); g_.DrawArrays(TRIANGLES, 0, static_cast<GLsizei>(mesh->second.count));
    }
}

int32_t Renderer::readPixels(uint32_t x, uint32_t y, uint32_t w, uint32_t h, uint8_t* out) {
    if (out == nullptr || w == 0 || h == 0) return ASTRATTO_ERR_ARGUMENT;
    g_.BindFramebuffer(FRAMEBUFFER, 0);
    g_.ReadBuffer(BACK_LEFT);
    g_.PixelStorei(PACK_ALIGNMENT, 1);
    g_.ReadPixels(static_cast<GLint>(x), static_cast<GLint>(y), static_cast<GLsizei>(w), static_cast<GLsizei>(h), RGBA, UNSIGNED_BYTE, out);
    return g_.GetError() == 0 ? ASTRATTO_OK : ASTRATTO_ERR_INTERNAL;
}

}  // namespace astratto
