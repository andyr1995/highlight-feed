# Highlight Feed

Highlights NPCs, ground items and objects whose names come from a list served by an app on **your own computer**,
for example a personal planner, a quest tracker or a stream overlay tool. Change the list in that app and the
highlights in game follow within a few seconds, without retyping anything in RuneLite's settings.

## How it works

The plugin reads a small JSON list from a URL on this computer every few seconds (3 by default):

```json
{
  "npcs":    [{"name": "Imp", "colour": "#FF0000"}, "Rat"],
  "items":   ["Black bead", "Red bead"],
  "objects": [{"name": "Oak tree"}]
}
```

- **npcs**: outlined, with an optional name label.
- **items** (ground items): tile outline and name label.
- **objects**: outlined (game objects, walls, decorations and ground objects), with an optional name label.
- Entries can be plain names or `{"name", "colour"}`. Colours are `#RRGGBB` or `#AARRGGBB`; entries without one use the colours set in the plugin's config.
- Names are matched case-insensitively.

## Safety

- **Local only.** The feed URL must be `localhost` or `127.0.0.1`. Any other address is ignored, so the plugin never contacts an outside server.
- **Read-only.** The plugin only downloads the list. It sends nothing about you, your account or your game.
- It doesn't add menu options or inject input. It only draws outlines and labels, like NPC Indicators.

## Config

| Setting | Default |
|---|---|
| Feed URL | `http://127.0.0.1:8765/highlights` |
| Refresh every (seconds) | 3 |
| Clear when the feed is offline | on (after 1 minute) |
| NPC / object / ground item colours | red |
| Outline width | 3 |
| Show names | on |
