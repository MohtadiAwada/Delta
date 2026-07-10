# Delta

> Version control for Minecraft builders.

Delta brings Git-inspired saving and restoring to Minecraft building projects. Save snapshots of your builds, restore them at any point, and manage multiple projects on the same server — all from within the game.

---

## What it does

- **Save** your build at any point with a message
- **Restore** any past save instantly
- **Manage multiple projects** in the same world without overlap
- **Select** a project once and stop typing its name in every command

---

## Requirements

- Paper 26.1.2 or later
- Java 26 or later

---

## Installation

1. Download `delta-0.1.0.jar` from the [releases page](https://github.com/MohtadiAwada/Delta/releases)
2. Drop it into your server's `plugins/` folder
3. Restart the server

No configuration needed.

---

## Commands

| Command | Description |
|---|---|
| `/delta initialize <name> <x,y,z> <x,y,z>` | Create a new project for a region |
| `/delta save <message>` | Save the current state of the selected project |
| `/delta restore <saveHash>` | Restore a project to a past save |
| `/delta select <project>` | Select a project to work on |
| `/delta selected` | Show the currently selected project |
| `/delta list projects` | List all projects on the server |
| `/delta list commits [amount]` | List saves for the selected project |
| `/delta help [command]` | Show help |

All commands have aliases for Git users: `init`, `commit`, `checkout`.

Run `/delta help <command>` in game for detailed usage of any command.

---

## Quick start

```
# create a project for your house
/delta initialize myhouse -64,64,-64 64,128,64

# select it
/delta select myhouse

# build something, then save
/delta save added the front wall

# build more
/delta save finished the roof

# see your saves
/delta list commits

# restore to an earlier save
/delta restore a3f8c1b2
```

---

## How saves work

Short hashes are supported — you only need to type the first few characters of a save hash. Delta finds the match automatically. If multiple saves match, it asks you to type more characters.

Your project selection resets when you leave the server.

---

## Known limitations in v0.1.0

- No status command (planned for v0.2.0)
- No branching (planned for v1.0.0)
- No undo for restore — once you restore, the current state is replaced
- Entities are not tracked, only blocks
- No permissions system yet — all players can use all commands

---

## Project structure

Delta stores all data in a `.delta/` folder next to your world folder:

```
.delta/
├── repos.dlr                  ← project registry (binary)
└── <projectName>/
    ├── objects/               ← content-addressable object storage
    │   ├── 00/ ... ff/        ← 256 subdirectories by hash prefix
    ├── branches/
    │   └── main.dlb           ← branch file (binary, ordered commit chain)
```

### Object types

Every object is stored by the SHA-256 hash of its content. Three object types exist:

**Blob** — stores block data for one chunk column of the region using a palette compression scheme. Identical chunks across commits share the same blob file — no duplication.

**Tree** — groups all blob references for one commit. Lists each blob's hash and the region bounds it covers.

**Commit** — points to a tree, a parent commit, and stores author, timestamp, and message.

### Branch file format

The branch file (`.dlb`) is a binary file — not human readable. It stores an ordered list of commit records, each containing the commit hash, parent hash, author, UTC timestamp, and message. The last record is always the current HEAD.

### Storage efficiency

Two commits where only one chunk changed will share all unchanged blob files. Only the modified chunk produces a new blob. The tree and commit objects are small (a few hundred bytes each) and are always written fresh per commit.

---

## Building from source

Requirements: Java 26, Maven 3.8+

```bash
git clone https://github.com/moti/delta.git
cd delta/Plugin
mvn package
# output: target/delta-0.1.0.jar
```

---

## Roadmap

| Version | Planned features |
|---|---|
| v0.2.0 | `/delta remove`, restore confirmation, status command |
| v0.3.0 | GZIP compression, math expressions in coordinates, player position variables |
| v1.0.0 | Branching, merging, entity tracking |

---

## License

Delta is licensed under the [GNU General Public License v3.0](LICENSE).
