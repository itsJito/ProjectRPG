package rpg.objects;

import rpg.item.Inventory;

/**
 * (0.3.0) A saddled mount's saddlebags: a view over the animal's own bag space (never a second owner of the items - the
 * animal carries them), opened beside it like any container.
 */
public final class Saddlebags extends Container {
    public Saddlebags(long id, String name, float x, float y, float z, long owner, Inventory bags) { super(id, name, "saddlebags", x, y, z, owner, bags); }
}
