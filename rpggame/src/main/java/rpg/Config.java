package rpg;

/**
 * Fixture values and tuning data, all in one place (GDD 4.2: every fixture value and action range is configuration).
 * None of these is final balance; the GDD leaves them open.
 */
public final class Config {
    /** Multiplier on every test XP reward (the debug panel changes it). */
    public float xpRate = 10f;
    /** Interaction: commit range, inspect range, the assist cone (cosine). */
    public float interactRange = 3f, inspectRange = 5f, assistCos = 0.97f;
    /** Gaits: brisk walking, a jog (Caps Lock), sprint on Shift, and a deliberate sneak. */
    public float sneakSpeed = 1.45f;
    public float walkSpeed = 2.45f, jogSpeed = 4.6f, runSpeed = 8.2f, jumpSpeed = 7.4f, dodgeSpeed = 9.5f, dodgeTime = 0.28f;
    /** Stamina costs and regeneration. */
    public float staminaLight = 8, staminaHeavy = 18, staminaDodge = 20, staminaSprint = 9, staminaRegen = 22, staminaBlockPerDamage = 0.5f;
    /** Combat XP per HP: melee splits Attack/Strength; Health shares dealt damage; Defense follows harm or guard. */
    public float xpPerDamage = 4f, healthXpPerDamage = 1.33f, defenseXpPerDamage = 2f, defenseXpPerBlocked = 2f;
    /** Parry window: a guard raised this long before contact parries. */
    public float parryWindow = 0.2f;
    /** Bounty: a theft is half the stolen value; the stated ceiling. */
    public float theftBountyShare = 0.5f;
    public int bountyCap = 1500, assaultBounty = 40, murderBounty = 1000;
    /** How far a noise of combat carries for NPC ears. */
    public float combatNoise = 18f;
    /** Game minutes per real second. */
    public float minutesPerSecond = 1f;
    /** Respawn times (game minutes) for gathering nodes. */
    public float treeRespawn = 30, rockRespawn = 20, patchRespawn = 25;
    /** The camera's lens: motion blur (the share of a frame the shutter is open) and depth of field. Both off unless chosen. */
    public boolean motionBlur = false, depthOfField = false;
    /** The renderer's preset (AstrattoNative.QUALITY_*) and the share of the window's resolution rendered (the TAA upscales). */
    public int quality = astratto.bridge.AstrattoNative.QUALITY_HIGH;
    public float renderScale = 1f;
    /**
     * Dynamic resolution: the render scale follows the GPU's time, down (to minScale) when frames run over the budget of
     * targetFps, back up to renderScale when there is room. The TAA upscales, so a lower scale costs little sharpness.
     */
    public boolean autoScale = true;
    public float minScale = 0.5f;
    /** The frame rate aimed at (set from the monitor's refresh rate when the game starts). */
    public int targetFps = 60;
    /** A fixed render scale from RPG_RENDER_SCALE (for measuring), or -1: none. */
    public float fixedScale() {
        String e = System.getenv("RPG_RENDER_SCALE");
        try { if (e != null) return Math.max(0.4f, Math.min(1f, Float.parseFloat(e))); } catch (NumberFormatException ignored) { }
        return -1;
    }
    public float shutter = 0.5f, aperture = 5f;
}
