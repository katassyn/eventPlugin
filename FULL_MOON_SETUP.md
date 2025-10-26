# FULL MOON EVENT - SETUP GUIDE

## Prerequisites
- MythicMobs plugin
- IngredientPouchPlugin (for IPS requirements and currency items)
- EssentialsX (for warps)
- WorldGuard (recommended for Map 1 regions)
- FastAsyncWorldEdit (FAWE) for Map 2 schematic loading

## 1. Configuration Files

### config.yml (eventPlugin)
Located at: `plugins/EventPlugin/config.yml`

**MUST SET:**
```yaml
full_moon:
  coordinates:
    map1:
      amarok_spawn:  # Block where players click with Blood Vial to spawn Amarok
        x: [YOUR_X]
        y: [YOUR_Y]
        z: [YOUR_Z]
        world: "world"
      cursed_amphory:  # Location for Cursed Amphory hourly spawn
        x: [YOUR_X]
        y: [YOUR_Y]
        z: [YOUR_Z]
        world: "world"

    map2:
      player_spawn:  # RELATIVE coordinates in schematic (gold block)
        x: 0
        y: 64
        z: 0
      # Other coordinates are auto-detected from schematic blocks
```

**Event Activation:**
```yaml
events:
  full_moon:
    active: true  # Set to true to start event
    duration_days: 3
    max_progress: 15000
```

## 2. Essentials Warps

Create two warps for Map 1:
```
/setwarp fullmoon_normal [at Normal difficulty area]
/setwarp fullmoon_hard [at Hard difficulty area]
```

## 3. Map 2 Schematic

### Creating blood_moon_arena.schem

The schematic MUST contain marker blocks:
- **grass_block** - Normal mobs spawn here (Bloody Werewolf + blood_sludgeling)
- **obsidian** x3 - Three mini-bosses spawn here (Blood Mage Disciples)
- **diamond_block** x1 - Final boss spawns here (Sanguis)
- **gold_block** x1 - Player spawn point

Place schematic at: `plugins/EventPlugin/schematics/blood_moon_arena.schem`

**Recommended size:** 50x50 blocks or smaller
**Build tips:**
- Place grass_blocks where you want normal mobs
- Place exactly 3 obsidian blocks for mini-bosses
- Place exactly 1 diamond_block for final boss
- Place 1 gold_block as player spawn point
- Build arena walls/atmosphere around these markers

### Saving the schematic:
```
//pos1  (select corner 1)
//pos2  (select corner 2)
//copy
//schem save blood_moon_arena
```

Then move the file to: `plugins/EventPlugin/schematics/`

## 4. Database

The plugin will automatically create required tables:
- `full_moon_quests` - Quest progress tracking
- `event_progress` - Event progress tracking
- `events` - Event metadata

No manual database setup required.

## 5. Permissions

All commands require permissions (NOT default):
```yaml
permissions:
  eventplugin.fullmoon:
    description: Access to /fullmoon command
    default: op
  eventplugin.fullmoon.admin:
    description: Admin commands for Full Moon
    default: op
```

Grant to players:
```
/lp group [group] permission set eventplugin.fullmoon true
```

## 6. MythicMobs Setup

All MythicMobs configs are already created:
- `plugins/MythicMobs/mobs/full_moon_event.yml` - All mobs
- `plugins/MythicMobs/items/full_moon_items.yml` - All items
- `plugins/MythicMobs/droptables/full_moon_drops.yml` - All drop tables

**IMPORTANT:** Reload MythicMobs after placing files:
```
/mm reload
```

## 7. Map 1 Mob Spawning

**Natural mob spawning on Map 1 is handled OUTSIDE this plugin.**

You need to set up mob spawning using:
- MythicMobs RandomSpawns (external config)
- OR custom spawner plugin
- OR manual spawning system

The plugin only handles:
- Map 2 instance mob spawning (automatic)
- Amarok boss spawning (Blood Vial click)
- Cursed Amphory boss spawning (hourly random)

## 8. Starting the Event

1. Ensure all above steps are completed
2. Set coordinates in config.yml
3. Create and place blood_moon_arena.schem
4. Create Essentials warps
5. Set up Map 1 mob spawning (external)
6. Set `active: true` in config.yml
7. Restart server OR `/event reload`

Players can now access event via:
```
/fullmoon
```

## 9. Testing Checklist

- [ ] `/fullmoon` command works
- [ ] GUI opens with Normal/Hard difficulty options
- [ ] Warps work (fullmoon_normal, fullmoon_hard)
- [ ] Map 1 mobs spawn naturally
- [ ] Blood Vial click spawns Amarok boss
- [ ] Quest progress tracks correctly (`/fullmoon quests`)
- [ ] Amarok kill shows Map 2 transition GUI (after Quest 4)
- [ ] Map 2 instance creates successfully
- [ ] Map 2 mobs spawn on grass_blocks
- [ ] Map 2 mini-bosses spawn on obsidian
- [ ] Map 2 final boss spawns after mini-bosses killed
- [ ] 60s timer teleports to /spawn after boss kill
- [ ] Cursed Amphory spawns randomly each hour
- [ ] Drop tables work (items drop from mobs)
- [ ] Progress tracking works (check with `/event`)

## 10. Troubleshooting

**"Arena schematic not found"**
- Ensure `blood_moon_arena.schem` is in `plugins/EventPlugin/schematics/`
- Check file extension (.schem or .schematic)
- Verify FAWE is installed

**"No grass blocks found for normal mob spawns"**
- Your schematic doesn't contain grass_block
- Add grass_blocks to schematic and re-save

**"Less than 3 mini-boss spawn points found"**
- Your schematic has fewer than 3 obsidian blocks
- Add exactly 3 obsidian blocks

**Mobs not tracking for quests**
- Ensure mob internal names match (werewolf_normal, amarok_hard, etc.)
- Check `/mm mobs` to verify mob names
- Reload MythicMobs: `/mm reload`

**Blood Vial not working**
- Ensure IngredientPouchPlugin is loaded
- Player needs Blood Vial in their pouch (not inventory)
- Check coordinates in config.yml match the block location

## 11. Admin Commands

```
/fullmoon - Open main GUI
/fullmoon quests - View quest progress
/fullmoon admin reset [player] - Reset player's Full Moon data
/fullmoon admin reload - Reload configuration
/event - View event progress
```

## 12. Drop Rates

Based on Q1 Infernal (Normal) and Q1 Blood (Hard) patterns:

**Normal Mobs:**
- Rare items: 1-1.5%
- NO Legendary/Mythic drops

**Mini Bosses:**
- Common: 3-5%
- Rare: 1-2%
- Legendary: 0.5-1%
- Mythic: 0.3% (Hard mode ONLY)

**Bosses:**
- Common: 8-12%
- Rare: 4-6%
- Legendary: 2-4%
- Mythic: 1-2% (Hard mode ONLY)

**Currency:**
- Draken: 1% (NOT affected by Luck)
- Blood Vial: 5% (Mini bosses only)

## 13. Event Duration

- **Duration:** 3 days (72 hours)
- **Total Progress Required:** 15,000
- **Progress Multiplier:** 2x on Hard mode
- **Auto-reset:** Quest progress resets when event reruns
