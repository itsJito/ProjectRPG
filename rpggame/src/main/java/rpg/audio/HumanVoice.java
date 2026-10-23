package rpg.audio;

import java.util.Arrays;
import java.util.List;
import rpg.actor.Actor;

/** Human voice resources and routing, shared by hurt and death cues. */
enum HumanVoice {
    MALE("male", "HumanMaleMain_Death_1", "HumanMaleMain_Death_2", "HumanMaleMain_Death_3",
            "HumanMaleMain_Death_4", "HumanMaleMain_Death_5", "HumanMaleMainDeath6"),
    FEMALE("female", "HumanFeamleMain_Death1", "HumanFemaleMain_Death_2", "HumanFemaleMain_Death_3",
            "HumanFemaleMain_Death_4", "HumanFemaleMain_Death_5");

    final String deathBank, hurtBank;
    final List<String> recordings;

    HumanVoice(String bank, String... stems) {
        deathBank = "death." + bank;
        hurtBank = "hurt." + bank;
        recordings = Arrays.stream(stems).map(stem -> "voice/" + stem + ".ogg").toList();
    }

    static HumanVoice of(Actor actor) { return actor.look.female ? FEMALE : MALE; }
}
