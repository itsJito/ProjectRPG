import os
R = os.path.join('src','main','java','rpg')
def sub(p, old, new):
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + p + ': ' + old[:70])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
sub(os.path.join(R,'Rpg.java'), '    public Quests quests;', '    public Quests quests;\n    /** (0.4.8) The journal: every quest as the player sees it, the active one, the journal settings. */\n    public rpg.quest.QuestLog questLog;')
sub(os.path.join(R,'Rpg.java'), '        quests = new Quests(this);', '        quests = new Quests(this);\n        questLog = new rpg.quest.QuestLog(this);')
sub(os.path.join(R,'SaveSystem.java'), '        root.put("quests", g.quests.save());', '        root.put("quests", g.quests.save());\n        root.put("questLog", g.questLog.save());')
sub(os.path.join(R,'SaveSystem.java'), '        g.quests.load(Json.obj(root, "quests"));', '        g.quests.load(Json.obj(root, "quests"));\n        g.questLog.load(Json.obj(root, "questLog"));')
print('wired')
