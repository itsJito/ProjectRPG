-- Low health: a faint red edge around the screen while health is below a third.
-- It's an example of a SCREEN fragment driven only by game state.

ui.fragment {
  id = "low_health_edge",
  anchor = "SCREEN", w = 0, h = 0,
  fade_in = 0.4, fade_out = 1.0,
  when = function()
    local p = game.player()
    return p.alive and p.hp < p.max_hp / 3
  end,
  draw = function(d)
    local w, h = ui.screen()
    local t = 18
    for i = 0, 5 do
      local a = string.format("#a0201a%02x", 60 - i * 10)
      d:rect(i * 3, i * 3, w - i * 6, 3, a)
      d:rect(i * 3, h - i * 3 - 3, w - i * 6, 3, a)
      d:rect(i * 3, i * 3, 3, h - i * 6, a)
      d:rect(w - i * 3 - 3, i * 3, 3, h - i * 6, a)
    end
  end,
}
