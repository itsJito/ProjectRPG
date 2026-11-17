import os
ENG = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'theastrattoengine')
def sub(rel, old, new):
    p = os.path.join(ENG, rel)
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + rel + ': ' + old[:90])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))

A = 'java/src/main/java/astratto/decal/DecalAtlas.java'
sub(A, '''            WATER_STAIN = 10, GRIME = 11, MUD = 12, PAPER = 13, WALL_CRACK = 14, KINDS = 15;
    public static final int[] VARIANTS = {4, 4, 4, 3, 3, 3, 2, 2, 4, 2, 3, 3, 2, 3, 3};''',
'''            WATER_STAIN = 10, GRIME = 11, MUD = 12, PAPER = 13, WALL_CRACK = 14,
            // 2.16: prints left where a foot or a hoof came down (pressed into snow, wet on soaked ground)
            FOOTPRINT = 15, HOOFPRINT = 16, KINDS = 17;
    public static final int[] VARIANTS = {4, 4, 4, 3, 3, 3, 2, 2, 4, 2, 3, 3, 2, 3, 3, 2, 2};''')
sub(A, '''            default -> { }
        }
        return px;
    }''', '''            case FOOTPRINT -> {
                // a shod sole along the picture's height, toe up: a broad forefoot and a narrower heel, the waist between them
                // shallow, its edge soft, a few cross lines of tread; the second variant a little longer and narrower
                float len = variant == 0 ? 0.80f : 0.86f, wide = variant == 0 ? 0.30f : 0.27f;
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
                    float u = (x + 0.5f) / N - 0.5f, v = 0.5f - (y + 0.5f) / N;            // v: + toward the toe
                    float along = v / (len * 0.5f);
                    if (Math.abs(along) > 1) continue;
                    // the sole's half-width along its length: forefoot, waist, heel
                    float half = wide * 0.5f * (along > 0.15f ? 1.0f - 0.35f * (float) Math.pow((along - 0.15f) / 0.85f, 2.2) : along > -0.35f ? 0.78f + 0.22f * (along + 0.35f) / 0.5f : 0.86f - 0.3f * (float) Math.pow((-0.35f - along) / 0.65f, 2));
                    float d = Math.abs(u) / Math.max(1e-3f, half);
                    if (d > 1.15f) continue;
                    float n = vnoise(r, grid, gs, x * 0.35f, y * 0.35f);
                    float a = Math.min(1, (1.15f - d) / 0.35f) * (0.78f + 0.22f * n);
                    boolean waist = along > -0.35f && along < 0.15f;
                    if (waist) a *= 0.55f;                                                   // the arch presses less
                    boolean tread = !waist && Math.floorMod((int) (v * N), 4) == 0;
                    px[x + y * N] = argb(Math.min(1, a), tread ? 0xC8C8C8 : 0xFFFFFF);
                }
            }
            case HOOFPRINT -> {
                // a horse's shoe: a round print, open at the heel, the frog a shallow wedge in the middle
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
                    float u = (x + 0.5f) / N - 0.5f, v = 0.5f - (y + 0.5f) / N;
                    float d = (float) Math.hypot(u / 0.30f, (v - 0.03f) / 0.34f);
                    if (d > 1.1f) continue;
                    if (v < -0.18f && Math.abs(u) < 0.12f) continue;                         // the open heel
                    float n = vnoise(r, grid, gs, x * 0.4f, y * 0.4f);
                    float rim = d > 0.68f ? 1 : 0.45f;
                    if (Math.abs(u) < 0.06f && v < 0.1f && v > -0.2f) rim = 0.75f;          // the frog
                    px[x + y * N] = argb(Math.min(1, (1.1f - d) / 0.25f) * rim * (0.8f + 0.2f * n), 0xFFFFFF);
                }
            }
            default -> { }
        }
        return px;
    }''')
# the decal shader: a print (its tint's alpha below nought) is pressed into the snow, never buried by it
sub('shaders/decal.frag', '''    float a = t.a * vTint.a;
    // what lies on the ground is buried when it snows (a mark on a wall is not)
    if (uSnow > 0.0 && abs(normalize(cross(dFdx(vWorld), dFdy(vWorld))).y) > 0.7) a *= 1.0 - smoothstep(0.15, 0.6, uSnow);''',
'''    // 2.16: a print (its tint's alpha given below nought) is pressed into the snow: never buried by it
    bool print = vTint.a < 0.0;
    float a = t.a * abs(vTint.a);
    // what lies on the ground is buried when it snows (a mark on a wall is not)
    if (!print && uSnow > 0.0 && abs(normalize(cross(dFdx(vWorld), dFdy(vWorld))).y) > 0.7) a *= 1.0 - smoothstep(0.15, 0.6, uSnow);''')
# the world: snow caps the top edges of blocks open to the sky (it rounds over them as it deepens); puddles fill the
# hollows as the ground soaks and shrink as it dries
sub('shaders/world_gbuffer.frag', '''        if (uRain > 0.0 && N.y > 0.5 && sky > 0.8) {
            float puddle = smoothstep(0.42, 0.62, texture(uNoise, vWorld.xz / 23.0).r);''',
'''        if (uRain > 0.0 && N.y > 0.5 && sky > 0.8) {
            // 2.16: the puddles grow from the hollows as the ground soaks and shrink back as it dries
            float pn = texture(uNoise, vWorld.xz / 23.0).r + texture(uNoise, vWorld.xz / 5.5).g * 0.08;
            float puddle = smoothstep(0.66 - 0.26 * uRain, 0.74 - 0.18 * uRain, pn);''')
sub('shaders/world_gbuffer.frag', '''            col = mix(col, snowCol, cover * (0.80 + 0.14 * grain));
            rough = mix(rough, 0.72, cover);
        }''', '''            col = mix(col, snowCol, cover * (0.80 + 0.14 * grain));
            rough = mix(rough, 0.72, cover);
        }
        // 2.16: and it caps the faces' top edges, a ragged band under each open top that deepens with the snow: the cover
        // reads as lying on the blocks, rounding over their edges, not painted on their tops
        if (uWeather.x > 0.05 && abs(N.y) < 0.3 && sky > 0.75) {
            float fy = fract(vWorld.y - 0.001);
            vec2 along = abs(N.x) > 0.5 ? vWorld.zy : vWorld.xy;
            float rag = texture(uNoise, along * vec2(0.45, 0.2) + 0.3).r * 0.5 + texture(uNoise, along * 1.3).g * 0.5;
            float depth = uWeather.x * (0.10 + 0.14 * rag);
            float cap = smoothstep(1.0 - depth - 0.02, 1.0 - depth + 0.03, fy);
            vec3 capCol = mix(vec3(0.74, 0.78, 0.86), vec3(0.92, 0.93, 0.95), fy);
            col = mix(col, capCol, cap * 0.92);
            rough = mix(rough, 0.72, cap);
        }''')
# models: what lies face-up and open to the sky takes the snow too (a barrel's lid, a cart's bed, a shoulder)
sub('shaders/model_gbuffer.frag', '''uniform float uEmissive, uFlag;''', '''uniform float uEmissive, uFlag;
uniform float uSnowCover;    // 2.16: how much snow lies (the world's): settled on what faces up under the open sky''')
sub('shaders/model_gbuffer.frag', '''    oAlbedo = vec4(mix(vec3(0.32,0.13,0.035),vCol.rgb * uTint.rgb,edge), clamp(uEmissive, 0.0, 1.0));''',
'''    vec3 base = vCol.rgb * uTint.rgb;
    if (uSnowCover > 0.0 && uMatParams.z > 0.7 && n.y > 0.45) {
        float lie = smoothstep(0.45, 0.85, n.y) * smoothstep(0.7, 0.95, uMatParams.z) * smoothstep(0.05, 0.6, uSnowCover);
        lie *= 0.75 + 0.25 * fract(sin(dot(floor(vWorld.xz * 12.0), vec2(12.9898, 78.233))) * 43758.5453);
        base = mix(base, vec3(0.88, 0.90, 0.94), lie);
    }
    oAlbedo = vec4(mix(vec3(0.32,0.13,0.035),base,edge), clamp(uEmissive, 0.0, 1.0));''')
print('engine snow')
