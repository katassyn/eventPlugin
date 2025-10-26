# FULL MOON EVENT - IMPORTANT NOTES

## CURRENCY ITEMS (w IngredientPouchPlugin)
- `blood_vial` - POTION, używane do przywołania Amarok boss (1 szt na spawn)
- `joker` - COPPER_INGOT, event currency
- `draken` - **TYLKO TEN JEDEN!** (DrakenMelon z crafting_currency_materials.yml)
  - NIE tworzyć draken_normal/draken_hard
  - Używać tylko: `draken`

## MOB CONFIGURATION RULES
Moby muszą być skonfigurowane jak w bloodborne.yml i q1:
- `FollowRange: 22` (dla normalnych mobów 15-22)
- `ShowHealth: true`
- `AlwaysShowName: true`
- `PreventItemPickup: true`
- `PreventOtherDrops: true`
- `PreventRandomEquipment: true`
- `PreventSunburn: true`
- `PreventJockeyMounts: true`
- `PreventTransformation: true`
- `Despawn: CHUNK` (dla normal mobów) lub `Despawn: false` (dla bossów)
- `Faction: FullMoon` i `Group: FullMoon`
- `KnockbackResistance: 0.85` (TYLKO dla bossów!)
- Health bar display: `&r &r&r&7[&c<caster.hp{round=1}>&7/&c<caster.mhp>&7] &4<&heart>`
- **NIE UŻYWAĆ POLA `Armor`!** - MC limituje do 30, używaj Equipment lub stat resistance

## MOB STATISTICS (bazując na Q1 Infernal/Blood)
**NORMAL MODE (wzorowane na Q1 Infernal):**
- Normal mob: HP 600-800, Damage 150-220
- Mini boss: HP 2500-3500, Damage 300-350
- Boss: HP 4000-5000, Damage 350-400

**HARD MODE (~x10 HP, ~x5 Damage jak Q1 Blood):**
- Normal mob: HP 6000-10000, Damage 750-1100
- Mini boss: HP 25000-35000, Damage 1500-1750
- Boss: HP 40000-50000, Damage 1750-2000

**Q1 Reference Stats:**
- q1_inf servant: HP 750, Dmg 150
- q1_inf worshipper: HP 1000, Dmg 200
- q1_inf boss: HP 2000, Dmg 250
- q1_blood servant: HP 7500, Dmg 750
- q1_blood worshipper: HP 10000, Dmg 1000
- q1_blood boss: HP 20000, Dmg 1250

## DROP TABLES
Drop tables są w osobnych plikach w `droptables/` folderze, NIE bezpośrednio w mobach.
Moby mają tylko referencję do drop table według wzorca Q1 Infernal/Blood.

**DROP CHANCE GUIDELINES (based on Q1):**

**Normal mobs:**
- Rare (&9): 1-1.5%
- NIE dropią Extraordinary/Legendary/Mythic!

**Mini bosses:**
- Rare (&9): 3-5%
- Extraordinary (&5): 1-2%
- Legendary (&6): 0.5-1%
- Mythic (&c&l): 0.3% MAX (only hard mode!)

**Bosses:**
- Rare (&9): 8-12%
- Extraordinary (&5): 4-6%
- Legendary (&6): 2-4%
- Mythic (&c&l): 1-2% MAX (only hard mode!)

**IMPORTANT:**
- Normal moby NIE dropią Legendary/Mythic items!
- Mythic items TYLKO w hard mode i TYLKO z mini bossów+
- Draken drop: 1% dla wszystkich (Luck NIE skaluje tego!)
- Blood Vial: 5% drop z mini bossów
- Używaj `MinItems: 0` + `nothing` entry - NIE guaranteed drops!

## ITEMS - NO PLACEHOLDER MATERIALS
NIE tworzyć "placeholder" materiałów typu:
- ~~full_moon_werewolf_item_normal~~ ❌
- ~~full_moon_wolf_item_normal~~ ❌
- ~~full_moon_commander_equipment_normal~~ ❌

Itemy to tylko:
- Zbroje (helmety, chestplaty, etc.)
- Bronie
- Akcesoria (ringi, necklace, cloak, belt)
- Currency items (blood_vial, joker, draken)

## ITEM COLORS/PREFIXES
- NORMAL mode items: `&7[ N ]` (szary prefix)
- HARD mode items: `&c[ H ]` (czerwony prefix)
- Rarity colors (display name):
  - Magic: &9
  - Extraordinary: &5
  - Legendary: &6
  - Mythic: &c&l (CAPS NAME)

## REQUIREMENTS
- Normal mode: level 50
- Hard mode: level 75 + 15 IPS (z IngredientPouchPlugin)

## MAPS
- Map 1: Warhlom - open area, warp: fullmoon_normal / fullmoon_hard
  - Amarok boss spawning: Blood Vial (1 szt) + click na odpowiedni blok
  - Cursed Amphory: co 1h losowanie spawnu (czarny shulker box)
  - Natural mob spawning: Handled by external system (NOT by this plugin)
- Map 2: Blood Moon above Warhlom - solo instance, loaded from FAWE schematic
  - Spawn: złoty blok (player_spawn coordinates in config)
  - Normal moby: spawny nad grass_block (Bloody Werewolf + blood_sludgeling alternating)
  - Mini bossy (3x): spawny nad obsydianem (Despawn: false!)
  - Boss: spawn nad diamond_block po zabiciu mini bossów
  - Timer 60s po zabiciu bossa -> tp /spawn
  - Schematic must contain: grass_block (normal mobs), obsidian x3 (mini bosses), diamond_block x1 (final boss)

## MOBY MAP 2
- Bloody Werewolf (normal mob) - spawns on grass_block
- **blood_sludgeling** (existing mob from bloodborne.yml) - używamy istniejącego! - spawns on grass_block
- Werewolf, Blood Mage Disciple (mini boss) - spawns on obsidian x3
- Sanguis the Blood Mage (boss) - spawns on diamond_block after mini bosses killed

**SPAWN MECHANISM MAP 2:**
- System automatically scans schematic for marker blocks
- grass_block -> Bloody Werewolf and blood_sludgeling (alternating)
- obsidian -> 3x Werewolf Blood Mage Disciple (mini bosses)
- diamond_block -> Sanguis the Blood Mage (spawns ONLY after all 3 mini bosses killed)

## EVENT PROGRESS
- Normal mode: 1x progress
- Hard mode: 2x progress
- Total needed: 15000 progress
- Duration: 3 days

## PROGRESS PER MOB (z Full_Moon_PLAN.md):
**Map 1:**
- Werewolf: Normal 1-3, Hard 2-6
- Wolf: Normal 1-2, Hard 2-4
- Werewolf Commander: Normal 25-35, Hard 50-75
- Amarok Boss: Normal 100, Hard 300

**Map 2:**
- Bloody Werewolf: Normal 6, Hard 12
- blood_sludgeling: Normal 4, Hard 8
- Blood Mage Disciple: Normal 100, Hard 200
- Sanguis Boss: Normal 500, Hard 1000
