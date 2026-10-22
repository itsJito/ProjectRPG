-- Session gold: how much gold you have gained or lost since the game started.
-- It appears for a few seconds whenever coins change hands, then gets out of the way.

local start = nil

ui.fragment {
  id = "session_gold",
  anchor = "TOP_RIGHT", x = 16, y = 236, w = 152, h = 34,
  wake = { "coins", "inventory" }, linger = 5,
  when = function()
    if start == nil then start = game.player().gold end
    return false
  end,
  draw = function(d)
    local gold = game.player().gold
    local delta = gold - (start or gold)
    local text, colour = game.gold_text(gold)
    d:rect(0, 0, d.w, d.h, "card")
    d:text("Gold", 8, 3, 11, "dim")
    d:text(text .. " G", 8, 16, 13, colour)
    local sign = delta >= 0 and "+" or ""
    local s = sign .. delta .. " this session"
    d:text(s, d.w - 8 - d:width(s, 11), 18, 11, delta >= 0 and "good" or "bad")
  end,
}
