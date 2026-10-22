# UI addons (Lua)

Every `*.lua` file in this folder runs when the game starts. To run them again, use **Reload addons** in the Debug scene (F7).

Addons run in a sandbox. They can't use files, processes or Java; they only have the `ui` and `game` tables below.

If an addon fails, it's reported in chat as `[Addon] ...` and only the part that failed is switched off.

## Fragments

The interface is made of *fragments*: small pieces of HUD that fade in when the state they show is active and fade out afterwards. An addon can add its own fragments:

```lua
ui.fragment {
  id = "my_thing",              -- unique
  anchor = "TOP_LEFT",          -- TOP_LEFT TOP TOP_RIGHT LEFT CENTER RIGHT BOTTOM_LEFT BOTTOM BOTTOM_RIGHT SCREEN
  x = 16, y = 220,              -- offset from the anchor, toward the middle of the screen
  w = 200, h = 40,
  group = "addons",             -- the fragment group it joins (default "addons", which every scene shows)
  when = function() return game.player().hp < 20 end,   -- optional: shown while this is true
  wake = { "combat", "law" },   -- optional: shown for `linger` seconds after these UI events
  linger = 6,
  fade_in = 0.15, fade_out = 0.45,
  draw = function(d)            -- coordinates are relative to the fragment
    d:rect(0, 0, d.w, d.h, "card")
    d:text("Hello", 8, 8, 13, "accent")
  end,
}
```

### Drawing

Inside `draw(d)`:

| Call | Draws |
|---|---|
| `d:rect(x, y, w, h, colour)` | a filled rectangle |
| `d:border(x, y, w, h, colour)` | a one-pixel outline |
| `d:text(s, x, y, size, colour)` | text |
| `d:icon(name, x, y, size, tint)` | an icon; `ui.icons()` lists the names |
| `d:skill_icon("MINING", x, y, size)` | a skill's icon |
| `d:bar(x, y, w, h, share, colour)` | a progress bar |
| `d:width(s, size)` | returns the text width |

The table also has `d.w`, `d.h` and `d.alpha` (the current fade).

A colour can be any of these:
- a number `0xAARRGGBB`
- `"#rrggbb"` or `"#rrggbbaa"`
- a style name: `text`, `dim`, `faint`, `accent`, `good`, `bad`, `info`, `card`, `edge`

## Reorganising the interface

| Call | Effect |
|---|---|
| `ui.fragments()` | lists every fragment id: `minimap`, `chat`, `vitals`, `actionbar`, `wanted`, `target`, `xpdrops`, `area`, `levelup`, `grids`, `equipment`, ... |
| `ui.move(id, anchor, x, y)` | moves a fragment in every scene |
| `ui.hide(id)` / `ui.show(id)` / `ui.reset(id)` | forces a fragment off or on, or hands it back to the game |
| `ui.group(groupId, fragmentId, anchor, x, y)` | puts a fragment into a group (the group is created if it's new) |
| `ui.add_group(scene, groupId)` | shows a group in a scene: `WORLD`, `INVENTORY`, `CHARACTER`, `SKILLS`, `JOURNAL`, `MAGIC` or `DEBUG` |
| `ui.scene()` / `ui.open(scene)` | returns the current scene, or switches to one |
| `ui.screen()` | returns the screen width and height |
| `ui.chat(text)` | writes a line to the chat |

Everything an addon changes is undone when the addons are reloaded.

## Events

```lua
ui.on("law", function(kind, detail) ui.chat("Law: " .. detail) end)
ui.on("*", function(kind, detail) end)   -- every event
```

UI events:
- `combat`, `law`, `inventory`, `coins`, `container`, `xp`, `quest`, `world`: things that involved the player.
- `area`: the jurisdiction changed.
- `scene`: a scene opened.
- `chat`: a new chat line.
- `event:<Type>`: every committed domain event, for example `event:ItemTaken`, `event:CrimeReported` or `event:LevelUp`.

## Reading the game

| Call | Returns |
|---|---|
| `game.player()` | `name`, `hp`, `max_hp`, `stamina`, `max_stamina`, `faith`, `max_faith`, `mana`, `max_mana`, `x`, `y`, `z`, `alive`, `sprinting`, `combat_level`, `total_level`, `gold`, `carried` (kg), `burden`, `in_combat` |
| `game.skill("MINING")` | level and xp |
| `game.skills()` | name to level |
| `game.count("logs")` | how many of an item the player carries |
| `game.inventory()` | a list of `{id, name, amount, stolen, grid, x, y, category}` |
| `game.bounty([jurisdiction])` | the bounty |
| `game.jurisdiction()`, `game.clock()`, `game.hour()`, `game.time()` | where and when |
| `game.gold_text(n)` | the gold display text and its colour (`"150K"`, yellow) |
