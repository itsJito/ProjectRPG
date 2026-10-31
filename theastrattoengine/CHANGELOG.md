# Changelog

## Alpha 0.2.7 — Shadow (API 2.7)

- Added ladder capability flags and vertical agent steering, including top-rung exits
- Added expiring learned obstruction costs and adaptive search budgets
- Added brief distance-priority chokepoint yields with a cooldown
- Added stepped and ladder render shapes; pinned grounded hedge vertices
- Preserved raw-query base costs and legacy search ordering; agent penalties deliberately alter learned routes

## HOODIE - frame time (API 2.5)

This HOODIE pass is about frame time. On the reference machine (a six-thread AMD FX-6300 with a GTX 1050 Ti), the
starter game's devmap averaged 73 FPS at 1920 x 1080, with a GPU frame of about 12 ms. After this pass it averages about
155 at 1080p and about 140 at the monitor's own 2560 x 1440. The engine changes:

### Added

- **GPU timing.** Each frame is timed on the GPU by part (uploads, shadows, G-buffer, AO, SSR, lighting, fog, forward,
  resolve, 2D) with timestamp queries. They're read back a few frames late, so nothing ever waits for them.
  `astratto_render_io` grows `gpu_ms` and `pass_gpu_ms[ASTRATTO_GPU_PASSES]`; they're written only when the caller's
  `struct_size` covers them, so a 2.4 caller keeps working. Java: `NativeRenderer.Stats.gpuMs` and `passGpuMs`.
- **Temporal upscaling.** With a render scale below 1, the TAA reconstructs at the output resolution. The history
  lives at the window's size, and each output pixel takes the nearest jittered sample, weighted by how squarely it hit
  the pixel. History, lens, bloom, exposure and tone map all work at the output size. At 0.75 the image is very hard to
  tell from native; at 0.5 it is soft but clean.
- **Frame pacing.** The renderer waits on a fence before it queues more than three frames, so the driver never builds a
  deep queue and then stalls a present. Two frames was tried and starved the GPU: 99 FPS against 137.
- **Baked leaf cards.** The procedural leaf cut (eleven pointed ovals of hashes and trigonometry per fragment, in the
  G-buffer and in every shadow cascade) is baked once at start-up into a 64 x 64 x 16 texture array
  (`leaf_bake.frag`). Shadows dropped from 1.9 to 0.8 ms and the G-buffer from 2.0 to 0.7 ms.
- `ASTRATTO_PROFILE_RESOLVE` times the resolve's steps (TAA, lens, bloom, exposure, tone map), draining the GPU between
  them. It's for measuring only.
- Java `Window`: `setFullscreen` (the monitor at its own mode), `refreshRate`, and adaptive vsync where the driver offers
  `EXT_swap_control_tear` (a late frame is shown at once instead of waiting a whole refresh).

### Changed

- The volumetric haze is marched at a quarter of the render resolution below the Ultra preset (`fogDiv`). The
  depth-aware upsample and the TAA keep it clean: 2.5 ms became 1.0.
- PCSS skips filtering deep in the umbra, and uses 8 taps when the penumbra is narrow. The tap loops are fixed so they
  unroll.
- History and lens targets are packed floats (R11G11B10F), halving the resolve's bandwidth.
- The first bloom downsample reads the exposure once per pixel, not once per tap. The TAA reads the normal buffer only
  where the hands could be. Exposure is measured every other frame.
- API 2.5.

## HOODIE - Sydney 3 and the 2.4 renderer (API 2.4)

This HOODIE pass rebuilds Sydney as a biomechanical character and gives the renderer foliage, soft shadows, smoke and
reflections. The interface grows to 2.4. Nothing in 2.3 changed meaning, but the ragdoll snapshot is longer (624 bytes),
so a game must be rebuilt against the new header.

### Added

- **Sydney 3.** A ragdoll is now 13 rigid segments and 12 joints in a private Impulsis space stepped at 240 Hz, moved
  by 48 Hill-type muscles (force-length, force-velocity, a passive element, first-order activation dynamics).
  - Behaviours set goals.
  - An LQR on the linear inverted pendulum, virtual-model control and capture-point stepping turn the goals into
    torques.
  - Matsuoka oscillators time steps, flailing and writhing.
  - A spinal layer with a 21 ms delayed stretch reflex, gravity compensation and a least-squared-activation muscle
    solver turns torques into excitations.
  - The behaviours: standing balance, stepping with an anticipatory weight shift, bracing and protecting the head,
    grabbing a ledge and hanging two-handed, lying hurt, getting up (roll, all-fours, kneel, stand), and the caller's
    intents.
  - `docs/API.md` describes it in full.
- `CMD_RAGDOLL_INTENT` (brace, protect, reach, walk, relax) and `CMD_RAGDOLL_TRAITS` (strength, agility, toughness,
  endurance).
- `EVT_RAGDOLL_IMPACT`: a part struck the world, or a hand took hold, at speed. Java: `EventSink.ragdollImpact`.
- The ragdoll snapshot adds the activity, grips, effort, balance and each segment's orientation and centre
  (`ASTRATTO_SYDNEY_SEGMENTS`).
- **Impulsis.**
  - Joint drives: per-axis damping toward a target rate, solved as a soft constraint and bounded per step.
  - Support reports per body (contact force, centre of pressure, ground points).
  - Configurable step rate and iterations.
  - Jointed bodies no longer collide with each other.
- **Renderer.**
  - Leaf-card clusters in every leaf cell, with alpha-tested procedural leaves, wind sway and flutter, and
    translucency. `ASTRATTO_MESH_LEAF_CUBES` keeps the old cubes.
  - Percentage-closer soft sun shadows.
  - Smoke volumes (`RCMD_SMOKE`, up to 32), each marched over its own stretch of the ray.
  - Screen-space water reflections over rippled water.
  - A lens pass (`RCMD_LENS`, `PASS_MOTION_BLUR`, `PASS_DEPTH_OF_FIELD`), off by default.
- **Tools.** `native/build.bat lab` builds the Sydney lab (`tests/sydney_lab.cpp`). `tools/sydney_sheet.py` draws
  contact sheets of its dumps, and `tools/sydney_tune.py` searches its parameters against the push sweep.

### Changed

- Sydney's balance and stepping defaults come from three rounds of tuning against pelvis and neck push sweeps
  (combined score 1,070 to about 1,500).
- Sydney's proprioception reads joint angles and rates from the change between steps, not from the solver's velocities
  (which carry Baumgarte correction).
- Sydney's timings scale with the world's gravity (√(g/9.81)).
- The raw shadow depth is bound with its own sampler object on unit 13. Unit 12 stays the exposure's.

### Removed

- The particle-chain Sydney: its 15 points are now derived from the segments, so callers reading points see the same
  layout.

### Tests

- Six new native checks: standing steady, segments in the snapshot, absorbing 40 N·s, stepping for 60 N·s, rising
  within 14 s of a knockdown (with impacts reported), and weak traits falling at 40 N·s. The self-test is 104/104.

## HOODIE - hardening pass on Astratto Engine 2.0 (API 2.1, unchanged)

HOODIE puts stability, measurement and quality gates ahead of new features. The C interface, its structures and its
version are unchanged: a game built against 2.1 runs on this core without a rebuild.

### Fixed

- **Route search heap could outgrow its array.** `Pathfinder` sized its open-list heap to the node cap (6,000), but a
  node is pushed again every time a cheaper way to it is found, so the heap's entry count is not bounded by the node
  count. A search with enough re-pushes would have written past the end of the array. The heap now grows when it has
  to. Routes are the same cell for cell, since growing changes no ordering.
- **A C++ module that released itself crashed the engine.** A module that sent `RELEASE` for its own handle from
  `init`, `update` or `event` had its library unloaded and its host freed while its own code was still running. Releases
  asked while module code is running are now deferred: the module is called no more, then shut down and unloaded once
  the call returns. A release asked from inside a module's own `shutdown` is refused with an error event, not run twice.
- **Exceptions could cross into module code.** The host functions a module calls (`command`, `reserve`, `snapshot`,
  `log`) let engine exceptions (out of memory, say) unwind through the module's C frames. Each now returns a status
  (`ASTRATTO_ERR_MEMORY` / `ASTRATTO_ERR_INTERNAL`) instead.
- **A throw on a worker thread ended the process.** `JobPool::parallelFor` let an exception escape a worker thread
  (`std::terminate`), and one thrown on the calling thread left the workers running on a dead batch. The first
  exception is now kept, the batch is abandoned, every worker leaves it, and only then is the exception rethrown on the
  caller's thread, where the C interface turns it into a status.
- **`astratto_world_set` was unguarded.** The one C entry point without the exception guard can allocate when a
  runtime set relights. It is now guarded like every other entry point; a `try` costs nothing until it throws.
- **Engine shutdown order.** Modules are now shut down while every service is still whole, so a module's `shutdown`
  may still write commands safely.
- **`gradlew` was not executable** in the repository (mode 644), so `./gradlew` failed on a fresh Linux or macOS
  checkout. Both wrappers (engine and starter game) are now mode 755.

### Changed

- **Chunk mesher: about 1.6-1.8x faster, byte for byte the same mesh.** Measured with the new benchmark (256 columns
  of a 256 x 64 x 256 world, 299,543 quads): roughly 320-430 ms before and 195-240 ms after on the same machine,
  depending on load. The output digest is identical (`f4e2219bad497433`), and the starter game's acceptance run draws
  the same 2,214 quads. How:
  - A branch-light first pass marks, for every cell of the column, which of its six faces could show (a cube face whose
    neighbour does not hide it). The full face rules (translucency, cut-outs, leaves, liquids, ambient occlusion, light)
    then run only where a bit is set: about 1,600 faces in 44,000 cells per column, rather than a hard-to-predict
    branch at every cell.
  - Faces whose surroundings lie wholly inside the column and the world read their neighbours by direct index, without
    per-lookup bounds checks.
  - Vertices are appended with `resize` and `memcpy`.
- **Warnings are errors** on Windows (`/W4 /WX`). The existing warnings were fixed: shadowed locals in `ragdoll.cpp`,
  `sydney.cpp` and the self-test, and narrowing in `VoxelWorld::clear`. CMake has `-DASTRATTO_WERROR=ON`.
- **The jar no longer picks up test-only libraries** from `native/bin` (the self-test's module is excluded).
- **Gradle:** the deprecated property syntax (`exceptionFormat 'full'`) that Gradle 10 removes now uses assignment. The
  build reports no deprecations.
- **Docs:** `README.md`, `docs/API.md` (module release rules; new section 8, *Quality gates*) and
  `docs/ARCHITECTURE.md` (check count, platform state) describe all of the above.

### Added

- **Hardening checks** (`native/tests/hardening.cpp`, part of the self-test, which grows from 65 to 75 checks):
  - 64 unbounded route searches over rough ground with doors, stairs, pools, lava and ladders, on four workers.
  - **Determinism:** 120 frames of 24 walking bodies and 192 route searches give identical event streams and body
    snapshots, byte for byte, when run again, and on four worker threads as on one.
  - **Command-stream fuzzing:** 1,500 frames of random records (random kinds, sizes and bodies, real handles mixed in,
    streams cut short, negative time steps). Each record is refused or run, never a crash, and the engine runs a clean
    frame afterwards.
  - **Handle churn:** 10,000 bodies made and released leave none behind. Their slots are reused under new
    generations, and all 10,000 old handles are refused.
  - The C interface refuses null engines, records and out-pointers.
  - Modules releasing themselves from `init`, `update` and `event` (with `native/tests/test_module.cpp`, a module built
    for this test alone).
- **Benchmark with budgets** (`native/tests/bench.cpp`, `native/tests/budgets.txt`). It times lighting a whole world,
  runtime relighting, meshing, 128 route searches (one thread and every worker), 500 walking bodies, the empty tick, and
  mixing 64 positional voices, all on fixed worlds and seeds. A median over its budget fails the run, it writes
  `results.json` for a history of runs, and it prints a digest of the mesher's output so that an optimisation can be
  shown to change nothing.
- **Build modes** (`native/build.bat`):
  - `checked`: the debug runtime, with every standard-container access bounds-checked, heap checks and run-time checks.
    The self-test passes all 75 checks in this mode. Reports go to stderr instead of a dialog.
  - `asan`: AddressSanitizer. It needs Visual Studio's "C++ AddressSanitizer" component and says so plainly when that
    is missing.
  - `bench`: builds, then runs the benchmark against the budgets.
- **CMake:** `ASTRATTO_SANITIZE` (ASan + UBSan with GCC/Clang, ASan with MSVC), `ASTRATTO_WERROR`, and the new
  targets (`astratto_bench`, `astratto_test_module`), registered with CTest.
- **Continuous integration** (`.github/workflows/ci.yml`), on every push and pull request:
  - Windows x64, Linux x64 and macOS arm64: the native core with warnings as errors, self-test, sanitizer self-test,
    benchmark against budgets, and the Java tests with fallbacks off.
  - Windows also runs the bounds-checked self-test.
  - Benchmark results and test reports are kept as artifacts of each run.
  - An advisory job runs the renderer test on Mesa's software OpenGL 4.5 (llvmpipe).
- **`ASTRATTO_HEADLESS=1`:** on a machine with no GPU, the one Java test that needs an OpenGL 4.5 context is reported
  *skipped*, never passed. Anywhere else a missing context is still a failure.

### Removed

- Nothing from the interface or the Java library. The remaining Java GL code (`astratto.render` and the GL paths of
  foliage, decals and props, with `lwjgl-opengl`) is still used by Absentialis's Java renderer. It goes when the game
  stops using that renderer, as `docs/ARCHITECTURE.md` records.

### Measured on this release

| Gate | Result |
|---|---|
| Native self-test, release (`/O2 /W4 /WX`) | 75 / 75 |
| Native self-test, bounds-checked debug runtime | 75 / 75 |
| Java tests (`./gradlew test`, fallbacks off) | 20 / 20 |
| Starter game acceptance (600 frames, off-screen, fallbacks off) | 11 / 11 |
| Benchmark against `budgets.txt` | 8 / 8 within budget |
| Compiler warnings (MSVC) | 0 |
| Gradle deprecations | 0 |

Benchmark medians on a 6-thread desktop (GTX 1050 Ti class): light a 256 x 64 x 256 world ~65-85 ms; one relighting
block set ~1.2-1.6 ms; mesh the whole world ~195-240 ms; 128 route searches ~6-9 ms on one thread and ~5-6 ms on every
worker; a frame of 500 walking bodies ~0.3 ms; empty tick ~1 µs; one second of 64 voices ~45-55 ms.

### Still open (not claimed by this release)

Production readiness is **not** declared. These items stand between this release and that declaration:

- **Linux and macOS:** CI now builds and tests both on every push, but they count as verified only once those jobs have
  passed. Off Windows the audio device is still the null device (audio is mixed on request).
- **Renderer pixel parity** with the reference renderer is not yet gated pixel for pixel. The software-GL CI job is the
  first step toward a gated comparison.
- **Runtime relighting** costs ~1.2-1.6 ms per light-changing block set and dirties up to 16 columns for re-meshing. A
  burst of such edits (an explosion) is the next frame-pacing target. Batching relights within a frame has to be shown
  to give the same light first.
- **Route searches** scale weakly across workers (~1.3x on 5 workers for 128 searches): the next CPU target after
  meshing.
- **Migration:** removing the Java GL code waits on Absentialis moving off its Java renderer.
- **Sign-off:** a vertical slice proving a complete game on Astratto alone, measured CPU/GPU/memory budgets for it,
  and written technical-director approval.
