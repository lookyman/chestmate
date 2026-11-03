# Chestmate

Small buttons on chests and your inventory that do the tedious inventory work for you.

## What it does

- **⇅ Organize**: merges partial stacks and sorts everything in **creative inventory order**. There is one button for the container and one for your inventory.
- **▲ Put everything in**: moves your inventory into the open container, topping up existing stacks first.
- **▼ Take everything out**: moves the container's contents into your inventory, topping up existing stacks first.

Your **hotbar, armor and offhand are never touched**. If something doesn't fit, it stays where it was.

Buttons appear on chests, double chests, trapped chests, barrels, ender chests, shulker boxes and the survival inventory. Machines like furnaces and hoppers are left alone.

## Works on any server

- **The server has Chestmate** (including singleplayer): the operation happens instantly on the server, in one step.
- **The server doesn't have it**: Chestmate does the clicks you would do by hand, at a safe pace (20 clicks per second by default). It checks every step and stops if anything changes under it, such as a hopper or another player.

Players without the mod can still join a server that has it and play normally.

## Shortcuts

There are four key bindings, one per button: Organize container, Organize inventory, Put everything in and Take everything out. All are **unbound by default**, so set them in *Controls → Key Binds → Chestmate*. They accept keyboard keys and mouse buttons, and on Chestmate screens they never also trigger the vanilla action on the same key.

## Settings

The click speed for servers without the mod can be changed in the config screen through [Mod Menu](https://modrinth.com/mod/modmenu) (optional), or in `config/chestmate.json`.

## Requirements

- Minecraft 26.3
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for the settings screen)

## Notes

- Bundles are handled too: their contents are never touched while sorting.
- Built for vanilla, Fabric and lightly modded servers. Servers with strict anti-cheat plugins aren't a target.
- Items with no creative tab, like filled maps and written books, sort at the end, grouped by item.

## License

MIT, see [LICENSE](LICENSE).
