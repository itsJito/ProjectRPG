"""(0.4.7) data/encounters.json: the Encounter Director's settings and every encounter's definition."""
import json, collections
OD = collections.OrderedDict


def stage(do=None, say=None, dialog=None, nxt=None, effects=None, end=None):
    s = OD()
    if do: s['do'] = do
    if say: s['say'] = say
    if dialog: s['dialog'] = dialog
    if effects: s['effects'] = effects
    if nxt: s['next'] = [OD([("when", w), ("to", t)]) for w, t in nxt]
    if end: s['end'] = end
    return s


def ch(text, to, effects=None, test=None, otherwise=None, requires=None):
    c = OD([("text", text), ("to", to)])
    if effects: c['effects'] = effects
    if test: c['test'] = test
    if otherwise: c['else'] = otherwise
    if requires: c['requires'] = requires
    return c


def dlg(speaker, text, *choices):
    return OD([("speaker", speaker), ("text", text), ("choices", list(choices))])


def cast(tag, kind, count=(1, 1), **kw):
    c = OD([("tag", tag), ("kind", kind), ("count", list(count))])
    c.update(kw)
    return c


def enc(id, title, type, rarity, weight, cooldown, when, spawn, cast_, start, stages):
    return OD([("id", id), ("title", title), ("type", type), ("rarity", rarity), ("weight", weight), ("cooldown", cooldown), ("repeatable", True),
               ("when", OD(when)), ("spawn", spawn), ("cast", cast_), ("start", start), ("stages", OD(stages))])


E = []
# ------------------------------------------------------------------ the road's outlaws
E.append(enc("highwaymen_toll", "Highwaymen's toll", "human", "common", 3.0, 300,
             [("wilds", True), ("road", 18), ("playerLevel", [3, 99]), ("hours", [5, 23])], "ahead_hidden",
             [cast("robber", "person", (2, 4), role="BANDIT", title="Highwayman", level=[10, 24], wear=["iron_sword", "stone_body"], give={"coins": 12}, drift="camp")],
             "lurk", [
                 ("lurk", stage(do={"robber": "hide"}, nxt=[("near:robber:16", "spring"), ("timer:240", "gone")])),
                 ("spring", stage(do={"robber": "block"}, say={"robber": ["Hold there, traveller. This stretch of the King's Road has a toll.", "That's far enough. The road's ours, and so's the toll."]},
                                  nxt=[("near:robber:6", "demand"), ("player_far:40", "chase"), ("hurt:robber", "fight"), ("timer:30", "demand")])),
                 ("demand", stage(do={"robber": "surround"},
                                  dialog=dlg("robber", "Your purse, or we take it off your body. A tenth of what you carry and you walk on with your teeth.",
                                             ch("Pay the toll ({toll} coins).", "paid", effects=["pay:robber:percent:10:min:15"], requires={"coins": 1}),
                                             ch("Refuse.", "fight"),
                                             ch("Walk away from me while you still can.", "cowed", test={"combatOver": ["robber", 12]}, otherwise="fight"),
                                             ch("I have nothing worth taking.", "searched", test={"coinsUnder": 15}, otherwise="fight")),
                                  nxt=[("player_far:24", "chase"), ("hurt:robber", "fight"), ("timer:45", "fight")])),
                 ("paid", stage(do={"robber": "leave"}, say={"robber": ["A pleasure. Mind the road, it's full of thieves.", "Off with you, then."]}, effects=["rumor:paid a highwayman's toll on the King's Road"], end="paid")),
                 ("searched", stage(do={"robber": "leave"}, say={"robber": ["Not worth the knife. Get gone."]}, end="let_go")),
                 ("cowed", stage(do={"robber": "flee"}, say={"robber": ["Easy - easy. We'll find softer purses."]}, effects=["honor:3:faced down highwaymen"], end="cowed")),
                 ("chase", stage(do={"robber": "attack_player"}, say={"robber": ["Running? After them!"]}, nxt=[("player_far:70", "gone"), ("dead:robber:all", "won"), ("hurt_half:robber", "rout")])),
                 ("fight", stage(do={"robber": "attack_player"}, say={"robber": ["Your mistake.", "Take it off them, then!"]}, nxt=[("dead:robber:all", "won"), ("hurt_half:robber", "rout"), ("player_far:70", "gone")])),
                 ("rout", stage(do={"robber": "flee"}, say={"robber": ["Enough! Run!"]}, effects=["honor:2:put highwaymen to flight"], end="routed")),
                 ("won", stage(effects=["honor:4:cleared highwaymen from the road", "rumor:cut down the highwaymen of the King's Road", "flag:highwaymen_beaten"], end="won")),
                 ("gone", stage(do={"robber": "leave"}, end="lost_interest"))]))

E.append(enc("night_ambush", "Ambush", "human", "uncommon", 1.5, 600,
             [("wilds", True), ("road", 30), ("playerLevel", [8, 99]), ("hours", [20, 5])], "around_hidden",
             [cast("ambusher", "person", (3, 4), role="BANDIT", title="Cutthroat", level=[16, 30], wear=["steel_sword", "stone_body"], give={"coins": 20}, drift="camp")],
             "wait", [
                 ("wait", stage(do={"ambusher": "hide"}, nxt=[("near:ambusher:12", "strike"), ("timer:300", "gone")])),
                 ("strike", stage(do={"ambusher": "attack_player"}, say={"ambusher": ["Now!", "Cut them down!"]}, nxt=[("dead:ambusher:all", "won"), ("hurt_half:ambusher", "rout"), ("player_far:60", "gone")])),
                 ("rout", stage(do={"ambusher": "flee"}, end="routed")),
                 ("won", stage(effects=["honor:5:survived an ambush in the dark", "rumor:beat off an ambush on the night road"], end="won")),
                 ("gone", stage(do={"ambusher": "leave"}, end="lost_interest"))]))

E.append(enc("false_guide", "The obliging stranger", "human", "rare", 1.0, 1440,
             [("wilds", True), ("road", 40), ("playerLevel", [5, 99]), ("hours", [7, 19])], "ahead",
             [cast("guide", "person", (1, 1), role="BANDIT", title="Traveller", level=[12, 18], wear=["iron_dagger"], hostile=False, drift="camp"),
              cast("waiting", "person", (2, 3), role="BANDIT", title="Cutthroat", level=[12, 22], wear=["iron_sword"], spawn="hidden_far", drift="camp")],
             "meet", [
                 ("meet", stage(do={"guide": "approach", "waiting": "hide"}, nxt=[("near:guide:4", "offer"), ("timer:120", "gone")])),
                 ("offer", stage(do={"guide": "hold", "waiting": "hide"},
                                 dialog=dlg("guide", "Well met. The road bends wide here - I know a shorter way through the scrub, if you'd walk with me. Safer than the open road, these days.",
                                            ch("Lead on.", "lead"), ch("I'll keep to the road.", "insist"),
                                            ch("You've the look of a cutpurse. Who's waiting in the scrub?", "unmasked", test={"skillOver": ["THIEVING", 20]}, otherwise="insist")),
                                 nxt=[("player_far:20", "gone"), ("timer:40", "gone")])),
                 ("lead", stage(do={"guide": "lead:waiting", "waiting": "hide"}, nxt=[("near:waiting:9", "trap"), ("player_far:30", "gone"), ("timer:150", "gone")])),
                 ("insist", stage(do={"guide": "attack_player", "waiting": "attack_player"}, say={"guide": ["Then we'll do it the hard way. Now, lads!"]}, nxt=[("dead:waiting:all", "won"), ("player_far:70", "gone")])),
                 ("unmasked", stage(do={"guide": "flee", "waiting": "leave"}, say={"guide": ["...Sharp eyes. Another day, then."]}, effects=["honor:2:saw through a false guide"], end="unmasked")),
                 ("trap", stage(do={"guide": "attack_player", "waiting": "attack_player"}, say={"waiting": ["Here they are. Take everything."]}, nxt=[("dead:waiting:all", "won"), ("player_far:70", "gone")])),
                 ("won", stage(effects=["honor:4:turned a trap on its setters", "rumor:turned the tables on a false guide and his friends"], end="won")),
                 ("gone", stage(do={"guide": "leave", "waiting": "leave"}, end="lost_interest"))]))

# ------------------------------------------------------------------ people on the road
E.append(enc("traveller_news", "A traveller with news", "human", "common", 4.0, 60,
             [("road", 20), ("hours", [6, 21])], "ahead",
             [cast("traveller", "person", (1, 2), role="VILLAGER", title="Traveller", level=[1, 6], give={"bread": 1, "coins": 8}, travel="settlement")],
             "walk", [
                 ("walk", stage(do={"traveller": "approach"}, nxt=[("near:traveller:4", "greet"), ("timer:150", "onward")])),
                 ("greet", stage(do={"traveller": "hold"},
                                 dialog=dlg("traveller", "Good day to you. Mind how you go - {rumor}",
                                            ch("Safe travels.", "onward"), ch("Where are you bound?", "where")),
                                 nxt=[("player_far:10", "onward"), ("timer:30", "onward")])),
                 ("where", stage(do={"traveller": "hold"}, say={"traveller": ["To {destination}, and with luck before dark."]}, nxt=[("timer:4", "onward")])),
                 ("onward", stage(do={"traveller": "travel"}, nxt=[("arrived:traveller", "done"), ("player_far:120", "done")])),
                 ("done", stage(end="passed"))]))

E.append(enc("pedlar", "A pedlar on the road", "human", "common", 2.0, 240,
             [("road", 20), ("hours", [7, 19])], "ahead",
             [cast("pedlar", "person", (1, 1), role="MERCHANT", title="Pedlar", level=[3, 8], give={"coins": 40}, stock=["bread:3", "torch:2", "fishing_bait:20", "tinderbox:1"], travel="settlement"),
              cast("mule", "animal", (1, 1), species="HORSE", follow="pedlar")],
             "walk", [
                 ("walk", stage(do={"pedlar": "approach", "mule": "follow:pedlar"}, nxt=[("near:pedlar:4", "wares"), ("timer:150", "onward")])),
                 ("wares", stage(do={"pedlar": "hold", "mule": "follow:pedlar"},
                                 dialog=dlg("pedlar", "Bread, a torch for the dark stretches, bait for the rivers - all honest, all cheap. Take a look?",
                                            ch("Show me your wares.", "trading", effects=["trade:pedlar"]), ch("Not today.", "onward")),
                                 nxt=[("player_far:10", "onward"), ("timer:40", "onward")])),
                 ("trading", stage(do={"pedlar": "hold", "mule": "follow:pedlar"}, nxt=[("player_far:8", "onward"), ("timer:90", "onward")])),
                 ("onward", stage(do={"pedlar": "travel", "mule": "follow:pedlar"}, nxt=[("arrived:pedlar", "done"), ("player_far:120", "done")])),
                 ("done", stage(end="passed"))]))

E.append(enc("courier", "A courier in haste", "human", "common", 1.5, 180,
             [("road", 15), ("hours", [5, 22])], "behind",
             [cast("courier", "person", (1, 1), role="CARTER", title="Courier", level=[4, 10], give={"coins": 15}, travel="far_settlement", run=True)],
             "ride", [
                 ("ride", stage(do={"courier": "travel"}, say={"courier": ["Make way! Letters for the crown!", "Pardon - road, please!"]}, nxt=[("hurt:courier", "robbed"), ("arrived:courier", "done"), ("player_far:150", "done")])),
                 ("robbed", stage(do={"courier": "flee"}, say={"courier": ["Help! Thief on the road!"]}, effects=["crime:assault:courier"], end="assaulted")),
                 ("done", stage(end="passed"))]))

E.append(enc("pilgrims", "Pilgrims on the road", "human", "uncommon", 1.5, 480,
             [("road", 25), ("hours", [6, 19]), ("regions", ["loxford_lowlands", "gorrick_shoulder", "southern_marches", "camelot_shoulder", "gallows_heath", "severian_north", "valgard_highlands"])], "ahead",
             [cast("pilgrim", "person", (2, 3), role="VILLAGER", title="Pilgrim", level=[1, 4], give={"bread": 1}, travel="church")],
             "walk", [
                 ("walk", stage(do={"pilgrim": "approach"}, nxt=[("near:pilgrim:4", "alms"), ("timer:150", "onward")])),
                 ("alms", stage(do={"pilgrim": "hold"},
                                dialog=dlg("pilgrim", "Jaehaerys keep you, friend. We walk to the sanctuary to hear the names of our dead read out. A coin or a crust for the road would be a kindness.",
                                           ch("Give them 5 coins.", "blessed", effects=["give:coins:5:pilgrim", "honor:3:gave alms to pilgrims", "rumor:gave alms to pilgrims on the road"], requires={"coins": 5}),
                                           ch("Give them bread.", "blessed", effects=["give:bread:1:pilgrim", "honor:2:fed pilgrims on the road"], requires={"item": "bread"}),
                                           ch("I've nothing to spare.", "onward")),
                                nxt=[("player_far:10", "onward"), ("timer:40", "onward")])),
                 ("blessed", stage(do={"pilgrim": "hold"}, say={"pilgrim": ["The Sun light your road. We'll speak your kindness at the altar."]}, nxt=[("timer:4", "onward")])),
                 ("onward", stage(do={"pilgrim": "travel"}, nxt=[("arrived:pilgrim", "done"), ("player_far:120", "done")])),
                 ("done", stage(end="passed"))]))

E.append(enc("injured_traveller", "Someone hurt by the road", "human", "uncommon", 1.2, 600,
             [("road", 25), ("hours", [6, 20])], "ahead",
             [cast("hurt", "person", (1, 1), role="VILLAGER", title="Injured traveller", level=[1, 5], wounded=0.35, give={"coins": 30}, travel="settlement")],
             "down", [
                 ("down", stage(do={"hurt": "plead"}, say={"hurt": ["Over here... please. They took my purse and left me for the crows."]}, nxt=[("near:hurt:4", "ask"), ("timer:400", "gone")])),
                 ("ask", stage(do={"hurt": "plead"},
                               dialog=dlg("hurt", "Wolves, or worse - I didn't see. I can't walk on this leg. Have you a bandage, or a bite to eat?",
                                          ch("Bind the wound (a bandage).", "helped", effects=["give:bandage:1:hurt", "heal:hurt", "honor:4:tended a stranger by the road", "reward:coins:20"], requires={"item": "bandage"}),
                                          ch("Here, eat something (bread).", "helped", effects=["give:bread:1:hurt", "heal:hurt", "honor:3:fed a hurt stranger", "reward:coins:10"], requires={"item": "bread"}),
                                          ch("I can't help you.", "left")),
                               nxt=[("player_far:10", "left"), ("timer:60", "left")])),
                 ("helped", stage(do={"hurt": "travel"}, say={"hurt": ["Bless you. Take this - it's what they didn't find in my boot."]}, effects=["rumor:helped a hurt traveller on the road"], end="helped")),
                 ("left", stage(do={"hurt": "plead"}, nxt=[("player_far:60", "gone")])),
                 ("gone", stage(do={"hurt": "travel"}, end="ignored"))]))

E.append(enc("roadside_dispute", "A quarrel by the road", "human", "uncommon", 1.2, 480,
             [("road", 25), ("realm", True), ("hours", [8, 20])], "ahead",
             [cast("debtor", "person", (1, 1), role="VILLAGER", title="Carter", level=[3, 8], give={"coins": 6}, travel="settlement"),
              cast("creditor", "person", (1, 1), role="MERCHANT", title="Moneylender", level=[3, 8], give={"coins": 60}, travel="settlement")],
             "argue", [
                 ("argue", stage(do={"debtor": "hold", "creditor": "hold"}, say={"creditor": ["Twelve coins at Midsummer, you swore it before witnesses!"], "debtor": ["And the harvest failed! I'll pay at the next fair!"]},
                                 nxt=[("near:creditor:6", "judge"), ("timer:300", "settled")])),
                 ("judge", stage(do={"debtor": "hold", "creditor": "hold"},
                                 dialog=dlg("creditor", "You there - you've an honest face. This man owes me twelve coins and won't pay. A witnessed promise is a promise, is it not?",
                                            ch("Pay his debt yourself (12 coins).", "paid", effects=["pay:creditor:flat:12", "honor:4:paid a stranger's debt", "rumor:settled a carter's debt out of their own purse"], requires={"coins": 12}),
                                            ch("A failed harvest is no crime. Give him till the fair.", "mercy", effects=["honor:2:spoke for mercy in a debt"]),
                                            ch("A promise is a promise. Pay him.", "strict", effects=["honor:1:upheld a witnessed promise"]),
                                            ch("Not my affair.", "settled")),
                                 nxt=[("player_far:12", "settled"), ("timer:60", "settled")])),
                 ("paid", stage(do={"debtor": "travel", "creditor": "travel"}, say={"debtor": ["I... thank you. I won't forget it."]}, end="paid_debt")),
                 ("mercy", stage(do={"debtor": "travel", "creditor": "travel"}, say={"creditor": ["...Till the fair, then. Not a day after."]}, end="mercy")),
                 ("strict", stage(do={"debtor": "travel", "creditor": "travel"}, say={"debtor": ["Fine! Fine. Here. Take it and choke on it."]}, end="strict")),
                 ("settled", stage(do={"debtor": "travel", "creditor": "travel"}, end="walked_on"))]))

# ------------------------------------------------------------------ the law and its quarry
E.append(enc("realm_patrol", "A patrol of the watch", "human", "common", 2.0, 180,
             [("road", 20), ("realm", True)], "ahead",
             [cast("patrol", "person", (2, 3), role="GUARD", title="Patrol of the watch", level=[30, 40], wear=["steel_sword", "steel_shield", "steel_body"], travel="settlement", realmGuard=True)],
             "approach", [
                 ("approach", stage(do={"patrol": "approach"}, nxt=[("near:patrol:6", "check"), ("timer:150", "onward")])),
                 ("check", stage(do={"patrol": "hold"}, nxt=[("wanted", "arrest"), ("timer:1", "greet")])),
                 ("greet", stage(do={"patrol": "hold"}, say={"patrol": ["Roads are quiet today. Keep them so.", "Move along, citizen. And report any outlaws you see."]}, nxt=[("timer:5", "onward")])),
                 ("arrest", stage(do={"patrol": "arrest"}, say={"patrol": ["You're wanted in these parts. Come quietly."]}, nxt=[("timer:60", "onward")])),
                 ("onward", stage(do={"patrol": "travel"}, nxt=[("arrived:patrol", "done"), ("player_far:140", "done")])),
                 ("done", stage(end="passed"))]))

E.append(enc("prisoner_transport", "A prisoner under guard", "human", "uncommon", 1.0, 600,
             [("road", 25), ("realm", True), ("hours", [7, 19])], "ahead",
             [cast("escort", "person", (2, 2), role="GUARD", title="Gaoler's escort", level=[28, 36], wear=["steel_sword", "steel_body"], travel="settlement", realmGuard=True),
              cast("prisoner", "person", (1, 1), role="VILLAGER", title="Prisoner", level=[5, 10], bound=True)],
             "march", [
                 ("march", stage(do={"escort": "approach", "prisoner": "follow:escort"}, nxt=[("near:prisoner:5", "plea"), ("timer:150", "onward")])),
                 ("plea", stage(do={"escort": "hold", "prisoner": "hold"},
                                dialog=dlg("prisoner", "Please - I took a loaf, that's all, and they'll hang me at the crossroads for it. Cut me loose and I'll vanish, I swear on the Sun.",
                                           ch("Cut the prisoner free.", "freed", effects=["crime:assault:escort", "free:prisoner", "hostile:escort"]),
                                           ch("Speak to the escort for them.", "pleaded", test={"honorOver": 40}, otherwise="refused"),
                                           ch("Walk on.", "onward")),
                                nxt=[("player_far:12", "onward"), ("timer:40", "onward"), ("hurt:escort", "freed")])),
                 ("freed", stage(do={"escort": "attack_player", "prisoner": "flee"}, say={"prisoner": ["Free! Bless you!"], "escort": ["Stop! In the king's name!"]}, nxt=[("dead:escort:all", "rescued"), ("player_far:80", "rescued")])),
                 ("rescued", stage(effects=["rumor:freed a prisoner from the gaolers on the road", "flag:freed_a_prisoner"], end="freed")),
                 ("pleaded", stage(do={"escort": "travel", "prisoner": "flee"}, say={"escort": ["...On your word, then. Go, and don't let me see you again."]}, effects=["honor:3:won a prisoner's release"], end="pardoned")),
                 ("refused", stage(do={"escort": "travel", "prisoner": "follow:escort"}, say={"escort": ["The magistrate decides, not you. Stand aside."]}, end="refused")),
                 ("onward", stage(do={"escort": "travel", "prisoner": "follow:escort"}, nxt=[("arrived:escort", "done"), ("player_far:140", "done")])),
                 ("done", stage(do={"prisoner": "despawn"}, end="delivered"))]))

E.append(enc("fugitive", "A fugitive and the pursuit", "human", "uncommon", 1.0, 600,
             [("hours", [6, 22])], "around",
             [cast("runner", "person", (1, 1), role="THIEF", title="Fugitive", level=[6, 12], give={"coins": 25}, run=True),
              cast("hunters", "person", (2, 3), role="GUARD", title="Pursuer", level=[24, 32], wear=["iron_sword", "stone_body"], spawn="behind_runner", realmGuard=True, travel="settlement")],
             "run", [
                 ("run", stage(do={"runner": "approach", "hunters": "hold"}, nxt=[("near:runner:5", "beg"), ("timer:90", "chase")])),
                 ("beg", stage(do={"runner": "hold", "hunters": "hold"},
                               dialog=dlg("runner", "They're after me - I owe a lord's reeve, nothing more! If they ask, you never saw me. Please!",
                                          ch("Go. I never saw you.", "hidden", effects=["flag:sheltered_fugitive"]),
                                          ch("Stay here till they come.", "held"), ch("Not my business.", "chase")),
                               nxt=[("timer:25", "chase"), ("player_far:15", "chase")])),
                 ("hidden", stage(do={"runner": "flee", "hunters": "approach"}, nxt=[("near:hunters:6", "asked"), ("timer:80", "lost")])),
                 ("asked", stage(do={"hunters": "hold"},
                                 dialog=dlg("hunters", "A man ran this way - lean, grey cloak, owes the reeve. Which way did he go?",
                                            ch("He went toward the river. (lie)", "lost", effects=["honor:-1:lied to the pursuit"]),
                                            ch("That way, after him.", "caught", effects=["reward:coins:15"]),
                                            ch("I saw no one.", "lost")),
                                 nxt=[("timer:30", "lost")])),
                 ("held", stage(do={"runner": "hold", "hunters": "approach"}, nxt=[("near:hunters:6", "caught")])),
                 ("chase", stage(do={"runner": "flee", "hunters": "chase:runner"}, nxt=[("dead:runner:all", "caught"), ("timer:120", "lost")])),
                 ("caught", stage(do={"runner": "follow:hunters", "hunters": "travel"}, say={"hunters": ["That's him. Thank you, traveller."]}, end="caught")),
                 ("lost", stage(do={"hunters": "travel", "runner": "leave"}, end="escaped"))]))

E.append(enc("bounty_hunter", "A bounty hunter", "human", "uncommon", 2.5, 720,
             [("bounty", True), ("playerLevel", [5, 99])], "behind",
             [cast("hunter", "person", (1, 2), role="HUNTER", title="Bounty hunter", level=[25, 45], wear=["steel_sword", "stone_body"], give={"coins": 30})],
             "track", [
                 ("track", stage(do={"hunter": "approach"}, nxt=[("near:hunter:7", "confront"), ("timer:200", "gone")])),
                 ("confront", stage(do={"hunter": "hold"},
                                    dialog=dlg("hunter", "Your face is on a board in town, friend, with a price under it. Come along and we'll both live to see the gaol.",
                                               ch("I'll come quietly.", "surrender", effects=["surrender"]), ch("Make me.", "fight"),
                                               ch("Here - take this and forget my face.", "bribed", effects=["pay:hunter:percent:25:min:40"], requires={"coins": 40})),
                                    nxt=[("player_far:25", "fight"), ("timer:40", "fight"), ("hurt:hunter", "fight")])),
                 ("surrender", stage(do={"hunter": "leave"}, end="surrendered")),
                 ("bribed", stage(do={"hunter": "leave"}, say={"hunter": ["Never saw you. The next one might not be so reasonable."]}, end="bribed")),
                 ("fight", stage(do={"hunter": "attack_player"}, nxt=[("dead:hunter:all", "won"), ("player_far:80", "gone")])),
                 ("won", stage(effects=["rumor:killed a bounty hunter who came for them"], end="won")),
                 ("gone", stage(do={"hunter": "leave"}, end="lost_interest"))]))

E.append(enc("assassin", "The assassin", "human", "rare", 1.0, 2880,
             [("playerLevel", [20, 99]), ("honorUnder", -20)], "ahead",
             [cast("killer", "person", (1, 1), role="THIEF", title="Traveller", level=[35, 50], wear=["steel_dagger"], hostile=False, give={"coins": 80})],
             "approach", [
                 ("approach", stage(do={"killer": "approach"}, nxt=[("near:killer:3", "strike"), ("timer:150", "gone")])),
                 ("strike", stage(do={"killer": "attack_player"}, say={"killer": ["Someone paid well for this."]}, nxt=[("dead:killer:all", "won"), ("hurt_half:killer", "rout"), ("player_far:70", "gone")])),
                 ("rout", stage(do={"killer": "flee"}, end="routed")),
                 ("won", stage(effects=["rumor:slew an assassin sent after them"], end="won")),
                 ("gone", stage(do={"killer": "leave"}, end="lost_interest"))]))

E.append(enc("kidnapping", "A kidnapping", "human", "rare", 1.0, 1440,
             [("wilds", True), ("hours", [6, 21]), ("playerLevel", [6, 99])], "ahead",
             [cast("taken", "person", (1, 1), role="VILLAGER", title="Captive", level=[1, 4], bound=True, female=True, travel="settlement"),
              cast("takers", "person", (2, 3), role="BANDIT", title="Slaver", level=[14, 26], wear=["iron_sword", "stone_body"], give={"coins": 25}, drift="camp")],
             "drag", [
                 ("drag", stage(do={"takers": "leave", "taken": "follow:takers"}, say={"taken": ["Help! Somebody, please! They're taking me!"]}, nxt=[("near:taken:10", "confront"), ("hurt:takers", "fight"), ("timer:120", "gone")])),
                 ("confront", stage(do={"takers": "hold", "taken": "plead"},
                                    dialog=dlg("takers", "Keep walking. She's a debtor of the Count's, and none of yours.",
                                               ch("Let her go.", "fight"), ch("Buy her freedom (50 coins).", "ransomed", effects=["pay:takers:flat:50"], requires={"coins": 50}), ch("Walk on.", "gone")),
                                    nxt=[("player_far:20", "gone"), ("timer:40", "fight"), ("hurt:takers", "fight")])),
                 ("fight", stage(do={"takers": "attack_player", "taken": "plead"}, nxt=[("dead:takers:all", "rescued"), ("player_far:70", "gone")])),
                 ("ransomed", stage(do={"takers": "leave", "taken": "travel"}, say={"taken": ["Thank you... thank you."]}, effects=["free:taken", "honor:4:ransomed a captive"], end="ransomed")),
                 ("rescued", stage(do={"taken": "travel"}, say={"taken": ["You came for me. I'll tell everyone in town."]}, effects=["free:taken", "honor:6:freed a captive from slavers", "rumor:freed a captive from slavers on the road", "reward:coins:30"], end="rescued")),
                 ("gone", stage(do={"takers": "leave", "taken": "follow:takers"}, end="ignored"))]))

E.append(enc("skirmish", "Outlaws and the watch at blows", "human", "uncommon", 1.0, 720,
             [("road", 40), ("hours", [6, 21])], "ahead",
             [cast("watch", "person", (2, 2), role="GUARD", title="Patrol of the watch", level=[28, 36], wear=["steel_sword", "steel_shield"], realmGuard=True, travel="settlement"),
              cast("outlaws", "person", (2, 3), role="BANDIT", title="Outlaw", level=[16, 26], wear=["iron_sword"], give={"coins": 15}, drift="camp")],
             "brawl", [
                 ("brawl", stage(do={"watch": "attack:outlaws", "outlaws": "attack:watch"}, nxt=[("dead:outlaws:all", "watch_won"), ("dead:watch:all", "outlaws_won"), ("timer:240", "over")])),
                 ("watch_won", stage(do={"watch": "travel"}, say={"watch": ["That's the last of them. Our thanks if you lent a hand."]}, effects=["rumor:the watch beat off outlaws on the road"], end="watch_won")),
                 ("outlaws_won", stage(do={"outlaws": "attack_player"}, nxt=[("dead:outlaws:all", "over"), ("player_far:70", "over")])),
                 ("over", stage(do={"outlaws": "leave", "watch": "travel"}, end="over"))]))

# ------------------------------------------------------------------ the wild
E.append(enc("migrating_herd", "A herd on the move", "animal", "common", 2.0, 240,
             [("wilds", True), ("biomes", ["meadow", "clearing", "upland", "heath", "highland", "marches", "willowmeadow"]), ("hours", [5, 20])], "far",
             [cast("herd", "animal", (4, 8), species="DEER")],
             "cross", [
                 ("cross", stage(do={"herd": "migrate"}, nxt=[("timer:240", "done"), ("player_far:160", "done")])),
                 ("done", stage(end="passed"))]))

E.append(enc("wolves_on_the_hunt", "Wolves on the hunt", "animal", "uncommon", 1.5, 360,
             [("wilds", True), ("biomes", ["oakwood", "pinewood", "upland", "highland", "deadwood", "crags"]), ("settledFar", 120)], "far",
             [cast("prey", "animal", (2, 3), species="DEER"), cast("pack", "animal", (2, 4), species="WOLF", spawn="behind_prey")],
             "chase", [
                 ("chase", stage(do={"pack": "hunt:prey", "prey": "flee:pack"}, nxt=[("dead:prey:any", "feast"), ("timer:240", "done")])),
                 ("feast", stage(do={"pack": "graze"}, nxt=[("timer:300", "done"), ("player_far:160", "done")])),
                 ("done", stage(end="passed"))]))

E.append(enc("carrion_feast", "Carrion birds at a carcass", "animal", "common", 2.0, 240,
             [("wilds", True)], "far",
             [cast("carcass", "carcass", (1, 1), species="DEER"),
              cast("crows", "animal", (3, 6), species="CROW", biomesNot=["desert", "dunes", "sandstone", "volcanic", "cinders"]),
              cast("vultures", "animal", (1, 3), species="VULTURE", biomes=["desert", "dunes", "sandstone", "volcanic", "cinders", "scorched", "heath", "crags", "highland", "marches"])],
             "feed", [
                 ("feed", stage(do={"crows": "scavenge:carcass", "vultures": "scavenge:carcass"}, nxt=[("gone:carcass", "done"), ("timer:900", "done"), ("player_far:180", "done")])),
                 ("done", stage(end="picked_clean"))]))

E.append(enc("bear_at_berries", "A bear foraging", "animal", "uncommon", 1.0, 600,
             [("wilds", True), ("biomes", ["oakwood", "pinewood", "copse", "upland"]), ("settledFar", 120), ("hours", [6, 20])], "far",
             [cast("bear", "animal", (1, 1), species="BROWN_BEAR")],
             "forage", [("forage", stage(do={"bear": "graze"}, nxt=[("timer:300", "done"), ("player_far:160", "done")])), ("done", stage(end="passed"))]))

# ------------------------------------------------------------------ monsters
E.append(enc("goblin_raiders", "Goblin raiders", "monster", "uncommon", 1.5, 600,
             [("wilds", True), ("hours", [19, 6]), ("regions", ["loxford_lowlands", "southern_marches", "gallows_heath", "western_crags", "dead_wood", "dimwater_basin"])], "around_hidden",
             [cast("raiders", "monster", (3, 5), monster="GOBLIN", level=[4, 10])],
             "creep", [
                 ("creep", stage(do={"raiders": "hide"}, nxt=[("near:raiders:14", "rush"), ("timer:300", "gone")])),
                 ("rush", stage(do={"raiders": "attack_player"}, nxt=[("dead:raiders:all", "won"), ("player_far:70", "gone")])),
                 ("won", stage(effects=["honor:2:drove off goblin raiders", "rumor:beat off a goblin raiding party"], end="won")),
                 ("gone", stage(do={"raiders": "despawn"}, end="lost_interest"))]))

E.append(enc("monster_hunters", "Monster hunters at work", "mixed", "rare", 1.0, 1440,
             [("wilds", True), ("hours", [6, 20]), ("playerLevel", [10, 99])], "ahead",
             [cast("hunters", "person", (2, 2), role="HUNTER", title="Monster hunter", level=[30, 40], wear=["steel_sword", "stone_body"], give={"coins": 20}, travel="settlement"),
              cast("beast", "monster", (1, 1), monster="ORC", level=[18, 24], spawn="near_hunters")],
             "battle", [
                 ("battle", stage(do={"hunters": "attack:beast", "beast": "attack:hunters"}, nxt=[("dead:beast:all", "thanks"), ("dead:hunters:all", "loose"), ("timer:240", "over")])),
                 ("thanks", stage(do={"hunters": "approach"}, say={"hunters": ["Good timing, if that was you. The Hollow's been spitting these out all season."]}, nxt=[("timer:8", "over")])),
                 ("loose", stage(do={"beast": "attack_player"}, nxt=[("dead:beast:all", "over"), ("player_far:70", "over")])),
                 ("over", stage(do={"hunters": "travel"}, end="over"))]))

D = OD([
    ("_about", "(0.4.7, gamefixes: the Encounter System) the world's encounters. The Encounter Director (rpg.encounter.Encounters) looks every 'interval' seconds at where the player is - the region and its biome, a road near, whose law, the hour, the weather, the player's level, bounties and honour, what has happened before - and draws one encounter from those eligible, by weight x rarity x the region's density, with cooldowns and a history so nothing repeats too soon. Each encounter is data: when it may happen, who is in it (people, animals, monsters, a carcass, each made from the game's own kinds and kits), where they come in (ahead on the road, hidden in cover, behind, around, far off), and its stages - what each party does, what is said, the choices offered and their effects, and what moves it on. People left over when it ends drift: they keep a routine of their own about a camp, or go on to a town. (0.4.8) Its people come in out of the player's sight ('spawnMin' metres off at least, unseen unless past 'spawnSeen'), lurkers come out after 'patience' seconds with the player near, a herd migrates a 'migrateStride' at a time, and an encounter the player has left 'despawnDistance' behind for 'leftBehind' seconds ends."),
    ("director", OD([("interval", 4.0), ("chance", 0.12), ("maxActive", 3), ("minApart", 120), ("spawnDistance", [40, 70]), ("spawnMin", 32), ("spawnSeen", 95), ("despawnDistance", 170), ("leftBehind", 30), ("patience", 40), ("patienceReach", 130), ("migrateStride", 18), ("quietMinutes", 20),
                     ("rarity", OD([("common", 1.0), ("uncommon", 0.4), ("rare", 0.12)])),
                     ("density", OD([("_default", 1.0), ("gallows_heath", 1.8), ("southern_marches", 1.5), ("dead_wood", 1.4), ("dimwater_basin", 1.3), ("western_crags", 1.2), ("ashmaw_coast", 0.8), ("severian_north", 0.9)])),
                     ("realmDensity", 0.7), ("seed", 7471)])),
    ("drifters", OD([("most", 10), ("campReach", 40), ("forgetAfter", 2880)])),
    ("encounters", E)])
json.dump(D, open('../../rpggame/src/main/resources/data/encounters.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print(len(E), "encounters")
