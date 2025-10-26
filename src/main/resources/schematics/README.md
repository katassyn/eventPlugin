# Schematics Folder

Place your Blood Moon Arena schematic file here.

## File Name
The schematic file should be named according to your config.yml setting:
- Default: `blood_moon_arena.schem` or `blood_moon_arena.schematic`

## File Format
Supported formats:
- `.schem` (WorldEdit 7+, FAWE)
- `.schematic` (Legacy WorldEdit)

## Arena Requirements
Your schematic should contain:

### Marker Blocks
1. **Gold Block** - Player spawn point (1 required)
2. **Obsidian Blocks** - Mini-boss spawn points (3 required)
3. **Diamond Block** - Final boss spawn point (1 required)

### Configuration
Update your `config.yml` with the relative coordinates of these blocks:
```yaml
full_moon:
  coordinates:
    map2:
      player_spawn:
        x: 0  # Relative to schematic origin
        y: 64
        z: 0
      mini_boss_1:
        x: 10
        y: 64
        z: 10
      mini_boss_2:
        x: -10
        y: 64
        z: 10
      mini_boss_3:
        x: 0
        y: 64
        z: -10
      final_boss:
        x: 0
        y: 70
        z: 0
```

### Notes
- Coordinates are relative to the bottom-left corner of the schematic
- The arena will be pasted dynamically in the world when a player enters
- Multiple instances can exist simultaneously (spacing: 1000 blocks by default)
- Instances are automatically cleaned up after completion
