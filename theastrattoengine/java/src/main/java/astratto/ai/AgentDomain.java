package astratto.ai;

/** Controller domains are independent of factions, species and utility policies. MONSTER reserves space, no content. */
public enum AgentDomain {
    HUMAN(0), ANIMAL(1 << 16), MONSTER(2 << 16);
    public final int flags;
    AgentDomain(int flags) { this.flags = flags; }
    public static AgentDomain fromFlags(int flags) { int domain = (flags >>> 16) & 3; if (domain == 3) throw new IllegalArgumentException("Reserved agent domain"); return values()[domain]; }
}
