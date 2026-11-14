def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:90], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'
patch(R + 'objects/Objects.java', [
("""            furniture.add(add(new Furniture(fid(), kind, p.x(), p.z(), kind == Furniture.Kind.BENCH ? x1 : p.x(), kind == Furniture.Kind.BENCH ? z1 : p.z(), 1, p.facing(), 0, true, p.floor())));
        }
        nextFixture = saved;""",
"""            furniture.add(add(new Furniture(fid(), kind, p.x(), p.z(), kind == Furniture.Kind.BENCH ? x1 : p.x(), kind == Furniture.Kind.BENCH ? z1 : p.z(), 1, p.facing(), 0, true, p.floor())));
        }
        // (0.4.4, gamefixes: every interior built, furnished and working) the interiors' furniture, inner doors, stations,
        // chests, altars and gaol cells, in their own band (their cells are already in the world's blocks)
        nextFixture = Ids.SV_INTERIORS;
        if (t.arch != null) rpg.world.shadowveil.Rooms.spawn(g, t.arch, new rpg.world.shadowveil.Rooms.Maker() {
            @Override public long id() { return fid(); }
            @Override public <T extends WorldObject> T add(T o) { return Objects.this.add(o); }
            @Override public void furniture(Furniture f) { furniture.add(f); }
            @Override public void door(Door d, boolean cell) { d.spawn(g); if (cell) jailDoors.add(d); }
        });
        // (0.4.4) the routes know every door (who may pass a locked one): they never did on ShadowVeil
        g.vox.doors(doors);
        nextFixture = saved;"""),
("""    public Door jailDoor(String jurisdiction) {
        for (Door door : jailDoors) if (door.name.equals(jurisdiction + " cell door")) return door;
        return null;
    }""",
"""    public Door jailDoor(String jurisdiction) {
        for (Door door : jailDoors) if (door.name.equals(jurisdiction + " cell door")) return door;
        return null;
    }
    /** (0.4.4) Every altar of the world (the devmap's one, ShadowVeil's churches'). */
    public List<Fixtures.Altar> altars() { List<Fixtures.Altar> out = new ArrayList<>(); for (WorldObject o : list) if (o instanceof Fixtures.Altar a) out.add(a); return out; }"""),
])
print('ok')
