# MC2 Interactive Foliage

A client-side Minecraft mod that makes foliage react to entities (through the Sway mod) and, in GPU mode, draws the
foliage near the player with a renderer of its own that sways it in the shader. One source tree builds every supported
version and loader with Stonecutter.

## Read this first

- **`docs/VERSION-MAP.md`**: how the mod works on every version and loader, every piece, every compatibility layer,
  the pitfalls, and the port recipe. Start here instead of reading the whole source tree.
- **`docs/<version>-render-notes.md`**: the research log of a port (what that Minecraft version changed and why the
  code is the way it is).
- `docs/map/index.html`: the same map as an interactive constellation, for people.

The map is generated. Edit `docs/map/curated.json`, then run `python docs/map/generate.py`: it checks every file a node
names against the source tree, warns about source files no node covers, and rewrites `docs/map/foliage-map.json`,
`docs/map/index.html` and `docs/VERSION-MAP.md`. Update it only once a version port, piece or compatibility layer is
finished and the user has tested it, in the same commit that completes it; work in progress stays off the map (the
script's "not named by any node" warnings for its new files are expected meanwhile). Then republish the page to the
same artifact: https://claude.ai/artifact/8wp4SyuVaZ8tx5QtYnhrtW

## Working rules

- Talk to the user in Spanish, including progress notes. Code, comments, commits and docs stay in English.
- Local commits only; the user pushes. Never add a Co-Authored-By trailer or any attribution.
- The newest Minecraft version is Stonecutter's `vcsVersion` and the active one: commit in its form and never switch
  back to an older one to commit. Switch with `./gradlew "Set active project to <version>-<loader>"`.
- New Minecraft version: research first, then one small step at a time with a user test between (the order is in the
  port recipe). Read Sodium's build for that version before inventing an approach.
- Testing: give the user one copyable launch command and a checklist, then wait. Never launch the game yourself.
  Test mods live in `versions/<version>-<loader>/run/mods` and test resource packs in `.../run/resourcepacks`, never
  in the repo.
- Every shader change is compiled offline with the game's shaderc options and shown in game with a visible test pack.
- Do not modify Sway or contact its author. User-facing text goes through translation keys (the user translates).
- Investigate before assuming. Do not ask the user to test third-party mods alone, and never tell players to change
  driver settings.
- After touching mixin or build configuration, check the mixin JSONs in the built jar.
