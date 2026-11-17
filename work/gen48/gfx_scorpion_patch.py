import os
HERE = os.path.dirname(os.path.abspath(__file__))
p = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg', 'render', 'Gfx.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:90])
    s = s.replace(old, new, 1)
sub('''                case GIANT_SPIDER -> spider(mo, f, st, dt);
                default -> dragon(mo, f, st, dt);''', '''                case GIANT_SPIDER, SMALL_SCORPION, LARGE_SCORPION -> spider(mo, f, st, dt);
                default -> dragon(mo, f, st, dt);''')
# the scorpion's body sits low and level, the cephalothorax hardly reared; its pincers and tail drawn after the legs
sub('''        mf.rotateX(rear);
        mpart(mf, f.beastHead);
        ma.set(mf).translate(0, 1, 0.5f).rotateX(-0.3f + 0.05f * (float) Math.sin(t * 0.9) - rear * 0.5f);
        mpart(ma, f.body);''', '''        boolean scorp = f.clawA != null;
        mf.rotateX(scorp ? rear * 0.25f : rear);
        mpart(mf, f.beastHead);
        ma.set(mf).translate(0, scorp ? 0 : 1, 0.5f).rotateX(scorp ? 0.02f * (float) Math.sin(t * 0.9) : -0.3f + 0.05f * (float) Math.sin(t * 0.9) - rear * 0.5f);
        mpart(ma, f.body);''')
sub('''            mb.set(ma).translate(0, 0, f.legALen).rotateX(r0 + r1);
            mpart(mb, f.legB);
        }
    }''', '''            mb.set(ma).translate(0, 0, f.legALen).rotateX(r0 + r1);
            mpart(mb, f.legB);
        }
        if (f.clawA != null) scorpionParts(mo, f, st, t, dead, curl, twitch, rearing, progress, recovery);
    }

    /**
     * (0.4.8, gamefixes: scorpions with two attacks of their own) a scorpion's pincers and tail, on the spider's body and
     * legs. The pincers are held out before it, opening and closing as it goes, raised and spread in its wind-up and
     * snapped shut forward in a pincer strike; the tail of five segments arches up over its back, swaying, curled higher
     * and drawn back in a sting's wind-up, then whipped forward over its head to drive the sting down. Dead, the pincers
     * fall slack and the tail uncurls along the ground.
     */
    private void scorpionParts(rpg.monster.Monster mo, MonsterModels.Fig f, MonsterPose st, double t, boolean dead, float curl, float twitch, boolean rearing, float progress, float recovery) {
        boolean sting = "sting".equals(mo.windup) || "sting".equals(mo.lastStrike);
        boolean pinch = !sting && (rearing || recovery > 0);
        float wind = rearing ? smooth(progress) : 0;
        float snap = recovery;                         // 1 at the strike, easing to 0
        float open = dead ? 0.1f : 0.25f + 0.2f * (float) Math.sin(t * 2.3) + (pinch ? 0.55f * wind - 0.6f * snap : 0);
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1 : -1;
            float reach = pinch ? 0.55f * wind - 0.9f * snap : 0;
            float yaw = sgn * (0.55f + 0.15f * (float) Math.sin(t * 0.7 + side) + (pinch ? 0.35f * wind - 0.45f * snap : 0)) + (dead ? sgn * 0.6f : 0);
            float pitch = dead ? -0.1f : 0.25f + 0.25f * wind - 0.15f * snap;
            ma.set(mf).translate(sgn * f.clawAt[0], f.clawAt[1], f.clawAt[2]).rotateY((float) Math.PI - yaw).rotateX(-pitch);
            mpart(ma, f.clawA);
            // the elbow folds the claw back in toward the face, opening out as it strikes
            mb.set(ma).translate(0, 0, 8).rotateY(sgn * (1.25f - reach * 0.9f)).rotateX(pitch * 0.6f);
            mb.rotateZ(sgn * open * 0.15f);
            mpart(mb, f.clawB);
        }
        // the tail: each segment pitched up a little more, the whole swaying side to side; in a sting's wind-up curled
        // tighter and drawn back, at the strike whipped forward over the head
        float sway = dead ? 0 : 0.18f * (float) Math.sin(t * 1.4) + 0.05f * twitch * (float) Math.sin(clock * 13);
        float arch = dead ? -0.05f * (1 - curl) : 0.42f + 0.04f * (float) Math.sin(t * 1.9) + (sting ? 0.12f * wind - 0.18f * snap : 0);
        float whip = sting ? -0.55f * snap + 0.2f * wind : 0;
        ma.set(mf).translate(f.tailAt[0], f.tailAt[1], f.tailAt[2]).rotateY(sway).rotateX(-(dead ? 0.1f : 0.6f) - whip);
        for (int i = 0; i < f.tailSegs; i++) {
            mpart(ma, f.tailSeg);
            ma.translate(0, 0, f.tailLen).rotateX(-arch).rotateY(sway * 0.3f);
            ma.scale(1.06f, 1.06f, 1.06f);
        }
        ma.rotateX(-0.6f - (sting ? 0.8f * snap : 0));
        mpart(ma, f.sting);
    }''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('scorpion draw')
