import os
HERE = os.path.dirname(os.path.abspath(__file__))
p = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg', 'render', 'Gfx.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:90])
    s = s.replace(old, new, 1)
sub('''        } else if (!engaged && amp < 0.05f && !dormant && !ghost) {
            roll += 0.035f * (float) Math.sin(t * 0.45);
            headZ += 0.18f * (float) Math.sin(t * 0.23 + 1.1);
        }''', '''        } else if (!engaged && amp < 0.05f && !dormant && !ghost) {
            roll += 0.035f * (float) Math.sin(t * 0.45);
            headZ += 0.18f * (float) Math.sin(t * 0.23 + 1.1);
        }
        // (0.4.8, gamefixes: monsters' animation sets polished and expanded - an idle) left to itself it is never a statue:
        // every few seconds one of its kind's idle actions, eased in and out over two to three seconds - looking about,
        // stretching, scratching, crouching to sniff the ground, beating its chest, shifting its weight
        float[] armLift = new float[2], armBend = new float[2];
        float lookAdd = 0;
        if (!engaged && !swinging && !dormant && !ghost && !airborne && amp < 0.08f && hover == 0) {
            int[] clips = idleClips(k);
            double window = 8.5 + (mo.id % 5) * 0.7, span = 2.8;
            long w = (long) Math.floor((t + mo.id % 13) / window);
            float u = (float) ((t + mo.id % 13) - w * window);
            if (clips.length > 0 && u < span) {
                int clip = clips[(int) Math.floorMod(w * 31 + mo.id * 7, clips.length)];
                float e = (float) Math.sin(Math.PI * u / span), q = u / (float) span;
                e = e * e * (3 - 2 * e);
                switch (clip) {
                    case 0 -> { lookAdd += 0.9f * (float) Math.sin(q * Math.PI * 2) * e; headX -= 0.08f * e; }                                  // look about
                    case 1 -> { armLift[0] += 2.3f * e; armLift[1] += 2.3f * e; armBend[0] += 0.3f * e; armBend[1] += 0.3f * e; lean -= 0.18f * e; headX -= 0.35f * e; }   // stretch
                    case 2 -> { armLift[0] += 2.1f * e; armBend[0] += 1.7f * e; headZ += 0.18f * e; headX += 0.08f * (float) Math.sin(t * 11) * e; }                    // scratch
                    case 3 -> { kneeBase += 0.55f * e; thighBase += 0.4f * e; lean += 0.4f * e; headX += 0.4f * e; lookAdd += 0.25f * (float) Math.sin(t * 3.1) * e; } // sniff
                    case 4 -> { float drum = (float) Math.sin(t * 9) * 0.35f * e; armLift[0] += 1.25f * e + drum; armLift[1] += 1.25f * e - drum; armBend[0] += 1.3f * e; armBend[1] += 1.3f * e; spread += 0.18f * e; headX -= 0.3f * e; }   // beat its chest
                    default -> { roll += 0.1f * e * (float) Math.sin(q * Math.PI); kneeBase += 0.12f * e; }                                     // shift its weight
                }
            }
        }''')
sub('''        float look = mo.fightTarget != 0 ? 0 : 0.25f * (float) Math.sin(t * 0.3);''',
    '''        float look = mo.fightTarget != 0 ? 0 : 0.25f * (float) Math.sin(t * 0.3) + lookAdd;''')
sub('''            float el = elbowBase + (run ? 0.6f : 0.15f) * amp;''', '''            float el = elbowBase + (run ? 0.6f : 0.15f) * amp + armBend[side];
            pitch += armLift[side];''')
sub('''    private void monsterPosePoints(rpg.monster.Monster mo, Mat4 torso, Mat4 hips, MonsterModels.Fig f, float[] neck, float[] head, float[][] shoulders, float[][] elbows, float[][] hands, float[][] leg) {''',
'''    /** (0.4.8) A kind's idle actions (0 look about, 1 stretch, 2 scratch, 3 sniff, 4 beat its chest, 5 shift its weight). */
    private static int[] idleClips(rpg.monster.MonsterKind k) {
        return switch (k) {
            case GOBLIN -> new int[]{0, 2, 3, 5};
            case ORC -> new int[]{0, 4, 1, 5};
            case TROLL, ICE_TROLL, RIVER_TROLL -> new int[]{0, 3, 4, 2};
            case IMP -> new int[]{0, 2, 5};
            case ZOMBIE -> new int[]{5, 0};
            case SKELETON -> new int[]{0, 5};
            case WEREWOLF -> new int[]{0, 3, 1};
            case LESSER_DEMON -> new int[]{0, 4, 2, 3};
            case GREATER_DEMON -> new int[]{0, 1, 4};
            default -> new int[]{0, 5};
        };
    }

    private void monsterPosePoints(rpg.monster.Monster mo, Mat4 torso, Mat4 hips, MonsterModels.Fig f, float[] neck, float[] head, float[][] shoulders, float[][] elbows, float[][] hands, float[][] leg) {''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('idle')
