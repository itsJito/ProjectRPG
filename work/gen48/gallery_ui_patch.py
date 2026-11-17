import os
HERE = os.path.dirname(os.path.abspath(__file__))
p = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg', 'Gallery.java')
s = open(p, encoding='utf-8').read()
anchor = '        // RPG_REVIEW_AT="x,y,z[,dist];...": four views of each point'
assert anchor in s
block = '''        // (0.4.8) RPG_REVIEW_ONLY=p34ui: the journal (empty, a quest chosen and set active, the settings), the world map whole,
        // zoomed in on the player with markers dropped and the active quest starred, and a skill's unlock list
        if (want.test("p34ui")) {
            stand.accept(start); g.controller.snapTo(map.spawnYaw); g.state.minutes = 12 * 60; frames(30);
            g.questLog.showUnstarted = false;
            hud.go(Hud.Scene.JOURNAL); reviewUi("p34-journal-default"); hud.closeAll();
            g.questLog.showUnstarted = true;
            hud.go(Hud.Scene.JOURNAL); reviewUi("p34-journal-unstarted"); hud.closeAll();
            g.quests.start();
            g.questLog.active = "courier";
            hud.journalSelect("courier");
            hud.go(Hud.Scene.JOURNAL); reviewUi("p34-journal-quest"); hud.closeAll();
            hud.journalSettings(true);
            hud.go(Hud.Scene.JOURNAL); reviewUi("p34-journal-settings"); hud.closeAll();
            hud.journalSettings(false);
            g.questLog.mapMarks.clear();
            g.questLog.mapMarks.add(new float[]{g.player.x + 40, g.player.z - 25, 0}); g.questLog.mapMarks.add(new float[]{g.player.x - 60, g.player.z + 30, 1}); g.questLog.nextMark = 2;
            hud.flag = new float[]{g.player.x + 20, g.player.z + 45};
            hud.go(Hud.Scene.MAP); reviewUi("p34-map-whole");
            for (float z : new float[]{3, 8}) { hud.mapView(g.player.x, g.player.z, z); reviewUi("p34-map-zoom" + (int) z); }
            hud.closeAll();
            hud.go(Hud.Scene.MAP); hud.mapView(g.player.x, g.player.z, 1); hud.closeAll();
            frames(10); reviewUi("p34-minimap-active");
            for (rpg.skill.Skill sk : new rpg.skill.Skill[]{rpg.skill.Skill.SMITHING, rpg.skill.Skill.ATTACK, rpg.skill.Skill.FISHING}) {
                hud.guide = sk; hud.go(Hud.Scene.SKILLS); reviewUi("p34-skills-" + sk.name().toLowerCase()); hud.closeAll();
            }
        }
'''
s = s.replace(anchor, block + anchor, 1)
open(p, 'w', encoding='utf-8', newline='').write(s)
print('gallery ok')
