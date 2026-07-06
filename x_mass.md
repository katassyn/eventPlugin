# Winter Event (X-mas) - Plan Implementacji - KOMPLETNY

## 1. Podstawowe Informacje

### Parametry eventu:
- **Nazwa eventu:** `winter_event` (ID w systemie)
- **Czas trwania:** 30 dni
- **Maksymalny progress:** 100,000
- **Komponenty:**
  1. Globalny drop prezentów (ze wszystkich mobów)
  2. Winter Cave (daily gift system)
  3. Winter Summit (3 poziomy trudności + 2 bossy)

---

## 2. Moby Eventowe

### Lokalizacja mobów MythicMobs:
- `C:\Users\mastu\Desktop\Serwer\plugins\MythicMobs\mobs\x_mas_blood.yml`
- `C:\Users\mastu\Desktop\Serwer\plugins\MythicMobs\mobs\x_mas_hell.yml`
- `C:\Users\mastu\Desktop\Serwer\plugins\MythicMobs\mobs\x_mas_inf.yml`

### Sety mobów:
1. **Infernal Set** (x_mas_inf) - podstawowy poziom trudności
2. **Hell Set** (x_mas_hell) - średni poziom trudności
3. **Blood Set** (x_mas_blood) - najtrudniejszy poziom

**Te moby dają PROGRESS do eventu winter_event** (patrz sekcja 8)

---

## 3. System Globalnego Dropu Prezentów

### Mechanika:
- **Szansa na drop:** 0.1% (0.001) z KAŻDEGO moba na serwerze (MythicMob)
- **Trigger:** Śmierć dowolnego MythicMob na całym serwerze
- **Event:** `MythicMobDeathEvent`
- **WAŻNE:** Prezenty dropią globalnie ze WSZYSTKICH mobów, nie tylko eventowych!

### System Rarity Prezentów - 6 POZIOMÓW:

**Kolejność rzadkości:** green > blue > purple > **orange** > gold > red

| Rarity | Szansa                | Kolor | EliteLootbox ID | Nazwa |
|--------|-----------------------|-------|-----------------|-------|
| Zielony | 50%                   | §a | `green_gift` | Common Gift |
| Niebieski | 25%                   | §9 | `blue_gift` | Rare Gift |
| Fioletowy | 10% | §5 | `purple_gift` | Epic Gift |
| **Pomarańczowy** | 6.875% | §6 | `orange_gift` | Legendary Gift |
| Złoty | 5% | §e | `gold_gift` | Mythic Gift |
| Czerwony | 3.125% | §c | `red_gift` | Ultimate Gift |

**✅ PROCENTY USTALONE (suma = 100%):**
- Green: 50%
- Blue: 25%
- Purple: 10%
- Orange: 6.875%
- Gold: 5%
- Red: 3.125%
= **100%**

### Dawanie prezentów:
- Plugin: **EliteLootbox**
- Komenda: `elb give <player> <box_id> 1`
- Wykonanie: `Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)`

### Powiadomienia:
- **Chat:** `[Winter Event] ⛄ You found a <rarity> Gift!`
- **Title:** `⛄ Winter Gift!` + subtitle z rarity
- **Sound:** `BLOCK_NOTE_BLOCK_CHIME`

---

## 4. Winter Cave - Daily Gift System

### Mechanika:
- **Dostęp:** JEDEN RAZ dziennie na gracza (**AŻ DO SKUTKU**)
- **"Aż do skutku":** Jeśli gracz padnie lub wyjdzie, może powtórzyć tego samego dnia
- **Wejście:** Komenda `/winter_cave` → GUI z potwierdzeniem
  - **⚠️ PERMISSION:** `eventplugin.winter_cave` (NIE DEFAULT!)
- **Warp:** `/warp winter_cave` (wykonywany przez plugin)
- **Timer:** 5 minut od wejścia
- **Instancja:** Zablokowana (max 1 gracz jednocześnie)
- **Język:** Wszystkie komunikaty PO ANGIELSKU

### Przebieg:
1. Gracz wykonuje `/winter_cave` (wymaga permission `eventplugin.winter_cave`)
2. Pokazuje się GUI z potwierdzeniem
3. Jeśli potwierdzi → warp na `/warp winter_cave`
   - Chat: "§f§l[Winter Event] §aTeleporting to Winter Cave..."
4. Plugin spawnuje MythicMob na koordach: **2510, -61, -52**
   - Chat: "§f§l[Winter Event] §eA mysterious creature appears! Defeat it to claim your reward."
5. Gracz zabija moba
   - Chat: "§f§l[Winter Event] §aYou defeated the creature! Find a player head and claim your reward."
6. Gracz klika PPM na **DOWOLNY player_head** w ciągu 5 minut (wykrywanie co 5s)
7. Otrzymuje nagrodę do EQ (z bazy danych, według dnia eventu)
   - Chat: "§f§l[Winter Event] §aYou claimed your daily reward! Come back tomorrow."

**WAŻNE:** Komunikaty PO ANGIELSKU, aby gracz wiedział co robić!
### Timer i wymuszenie wyjścia:
- **Timer:** 5 minut od wejścia
- **Po 5 minutach:**
  - Jeśli gracz nadal tam jest → teleport na `/spawn` (jako KONSOLA)
  - Instancja zostaje zwolniona
- **Wcześniejsze wyjście:**
  - Gracz zmienia świat → instancja zwolniona natychmiast

### System nagród (Days 1-30):
- **Admin GUI:** `/winter_cave_rewards`
- Admin ustawia nagrody dla każdego dnia (1-30)
- Nagrody zapisane w bazie danych (podobnie jak quest rewards)
- Gracz dostaje nagrodę według DNIA EVENTU (nie dnia rzeczywistego)

### Przykład:
- Event day 1: Gracz dostaje nagrodę z day 1
- Event day 15: Gracz dostaje nagrodę z day 15

### 📋 Tabelka do uzupełnienia:

| Parametr | Wartość | Status |
|----------|---------|--------|
| **MythicMob ID (winter cave)** | ________________ | [ ] Uzupełniono |
| **Koordynaty spawn** | 2510, -61, -52 | [x] Uzupełniono |
| **Player head koordynaty** | X: ___ Y: ___ Z: ___ | [ ] Uzupełniono |
| **Warp name** | `winter_cave` | [x] Uzupełniono |
Player head koordynaty <- nie ma znaczenia jakakolwiek kliknie w ciagu 5minut odbierze nagrode (5 minut to timer instancji)
---

## 5. Winter Summit - Poziomy Trudności + Bossy

### Dostęp z Event Hub:
- Gracz klika w EventsMainGUI na Winter Event
- Pokazuje się menu wyboru poziomu trudności

### Poziomy trudności:

| Poziom | Wymagania | Warp |
|--------|-----------|------|
| **Infernal** | Level 50, 0 IPS | `/warp winter_summit_inf` |
| **Hell** | Level 65, 15 IPS | `/warp winter_summit_hell` |
| **Blood** | Level 80, 30 IPS | `/warp winter_summit_blood` |

**IPS = "fragments of infernal passages"** (z IngredientPouchPlugin)

### Dwa Bossy na Winter Summit:

#### Boss 1: Gluttonous Bear (Miś Łasuch)
- **Wymagany item:** `honey_bait`
  - Item ID: `honey_bottle`
  - Display: `&6Honey Bait`
  - Lore: `&o&7Used to enter the Gluttonous Bear's cave`
  - Źródło: IngredientPouchPlugin
- **Koszt wejścia:**
  - Infernal: 1x honey_bait
  - Hell: 2x honey_bait
  - Blood: 3x honey_bait
- **Interakcja:** Blok na koordach (do uzupełnienia)
- **Instancja:** Generowana z FAWE (jak Map2 dla Full/New Moon)

#### Boss 2: Krampus (Duch Świąt)
- **Wymagany item:** `winter_solstice_log`
  - Item ID: `spruce_wood`
  - Display: `&8Winter Solstice Log`
  - Lore: `&o&7Used to enter Krampus's domain`
  - Źródło: IngredientPouchPlugin
- **Koszt wejścia:**
  - Infernal: 1x winter_solstice_log
  - Hell: 2x winter_solstice_log
  - Blood: 3x winter_solstice_log
- **Interakcja:** Blok na koordach (do uzupełnienia)
- **Instancja:** Generowana z FAWE (jak Map2 dla Full/New Moon)

### 📋 Tabelka do uzupełnienia - Koordynaty interakcji (6 lokacji):

| Poziom | Boss | Blok Interakcji | Koordynaty (X, Y, Z) | Status |
|--------|------|----------------|---------------------|--------|
| **Infernal** | Bear (honey_bait) | _________ | X: ___ Y: ___ Z: ___ | [ ] |
| **Infernal** | Krampus (log) | _________ | X: ___ Y: ___ Z: ___ | [ ] |
| **Hell** | Bear (honey_bait) | _________ | X: ___ Y: ___ Z: ___ | [ ] |
| **Hell** | Krampus (log) | _________ | X: ___ Y: ___ Z: ___ | [ ] |
| **Blood** | Bear (honey_bait) | _________ | X: ___ Y: ___ Z: ___ | [ ] |
| **Blood** | Krampus (log) | _________ | X: ___ Y: ___ Z: ___ | [ ] |

### 📋 Tabelka do uzupełnienia - FAWE Schematics:

| Boss | Poziom | Schematic Name | Status |
|------|--------|---------------|--------|
| Bear | Infernal | _______________ | [ ] |
| Bear | Hell | _______________ | [ ] |
| Bear | Blood | _______________ | [ ] |
| Krampus | Infernal | _______________ | [ ] |
| Krampus | Hell | _______________ | [ ] |
| Krampus | Blood | _______________ | [ ] |

### MythicMob IDs dla bossów:
**✅ Znajdują się w plikach MythicMobs:**
- `C:\Users\mastu\Desktop\Serwer\plugins\MythicMobs\mobs\x_mas_blood.yml`
- `C:\Users\mastu\Desktop\Serwer\plugins\MythicMobs\mobs\x_mas_hell.yml`
- `C:\Users\mastu\Desktop\Serwer\plugins\MythicMobs\mobs\x_mas_inf.yml`

Plugin będzie czytał nazwy mobów bezpośrednio z tych plików podczas implementacji.
---

## 6. Struktura Techniczna

### Pakiety i klasy:

```
org.maks.eventPlugin.winterevent/
├── WinterEventManager.java                    # Główny manager
│
├── listener/
│   ├── GiftDropListener.java                  # Globalny drop prezentów
│   └── WinterEventMobListener.java            # Progress za x_mas_* moby
│
├── wintercave/
│   ├── WinterCaveManager.java                 # Manager dla daily gift
│   ├── WinterCaveInstance.java                # Pojedyncza instancja (locking)
│   ├── WinterCaveDailyRewardDAO.java          # Dostęp do nagród z bazy
│   ├── listener/
│   │   ├── WinterCaveMobListener.java         # Spawn moba, death detection
│   │   └── WinterCavePlayerListener.java      # Timer, cleanup, teleport
│   └── gui/
│       ├── WinterCaveGUI.java                 # /winter_cave (potwierdzenie wejścia)
│       └── WinterCaveRewardsGUI.java          # /winter_cave_rewards (admin)
│
└── summit/
    ├── WinterSummitManager.java               # Manager dla summit
    ├── WinterSummitInstance.java              # Instancja bossa (FAWE)
    ├── listener/
    │   ├── SummitInteractionListener.java     # Interakcja z blokami (honey/log)
    │   └── SummitBossListener.java            # Boss deaths, cleanup
    └── gui/
        └── DifficultySelectionGUI.java        # Wybór poziomu trudności
```

### Komendy:
- `/winter_cave` - GUI wejścia do winter cave (gracz)
  - **Permission:** `eventplugin.winter_cave` (NIE DEFAULT!)
- `/winter_cave_rewards` - GUI nagród admin (admin)
  - **Permission:** `eventplugin.admin.winter_cave_rewards`
- Dodać do `/event_hub` menu Winter Event z wyborem poziomów

### Integracja z EventPlugin.java:
```java
private WinterEventManager winterEventManager;

private void initializeWinterEvent() {
    EventManager winterEvent = eventManagers.get("winter_event");
    if (winterEvent != null) {
        winterEventManager = new WinterEventManager(this, databaseManager, configManager, winterEvent);

        // Register listeners
        getServer().getPluginManager().registerEvents(
            new GiftDropListener(winterEventManager), this);
        getServer().getPluginManager().registerEvents(
            new WinterEventMobListener(winterEventManager), this);
        getServer().getPluginManager().registerEvents(
            new WinterCaveMobListener(winterEventManager.getWinterCaveManager()), this);
        getServer().getPluginManager().registerEvents(
            new WinterCavePlayerListener(winterEventManager.getWinterCaveManager()), this);
        getServer().getPluginManager().registerEvents(
            new SummitInteractionListener(winterEventManager.getSummitManager()), this);
        getServer().getPluginManager().registerEvents(
            new SummitBossListener(winterEventManager.getSummitManager()), this);

        Bukkit.getLogger().info("[EventPlugin] Winter Event initialized");
    }
}
```

---

## 7. Konfiguracja (config.yml)

```yaml
events:
  winter_event:
    active: true
    name: "Winter Event"
    description: "Celebrate winter with gifts, challenges, and epic bosses!"
    max_progress: 100000
    duration_days: 30

    # ==================== GLOBALNY DROP PREZENTÓW ====================
    gift_drop:
      global_chance: 0.001  # 0.1% z każdego moba na serwerze

      # Rarity (6 poziomów) - orange między purple a gold
      rarities:
        green: 50.0
        blue: 25.0
        purple: 10.0      # [DO UZUPEŁNIENIA]
        orange: 6.875     # [DO UZUPEŁNIENIA]
        gold: 5.0         # [DO UZUPEŁNIENIA]
        red: 3.125

      # EliteLootbox IDs
      boxes:
        green: "green_gift"
        blue: "blue_gift"
        purple: "purple_gift"
        orange: "orange_gift"
        gold: "gold_gift"
        red: "red_gift"

    # ==================== WINTER CAVE (Daily Gift) ====================
    winter_cave:
      enabled: true

      # Spawn moba
      spawn_location:
        world: "world"  # [DO UZUPEŁNIENIA jeśli inny]
        x: 2510
        y: -61
        z: -52
      mob_name: "[DO UZUPEŁNIENIA]"  # MythicMob ID

      # Player head (odbiór nagrody)
      reward_head_location:
        world: "world"  # [DO UZUPEŁNIENIA]
        x: 0  # [DO UZUPEŁNIENIA]
        y: 0  # [DO UZUPEŁNIENIA]
        z: 0  # [DO UZUPEŁNIENIA]

      # Warp i timer
      warp_name: "winter_cave"
      timer_minutes: 5
      spawn_command: "spawn {player}"  # Komenda do wyrzucenia gracza

      # PPM detection cooldown (żeby nie spamować wiadomości)
      interaction_cooldown_seconds: 5

    # ==================== WINTER SUMMIT (Bossy) ====================
    summit:
      enabled: true

      # Wymagania poziomów
      requirements:
        infernal:
          level: 50
          ips: 0
          warp: "winter_summit_inf"
        hell:
          level: 65
          ips: 15
          warp: "winter_summit_hell"
        blood:
          level: 80
          ips: 30
          warp: "winter_summit_blood"

      # Koszt wejścia do bossów (według poziomu)
      boss_costs:
        bear:
          infernal: 1  # honey_bait
          hell: 2
          blood: 3
        krampus:
          infernal: 1  # winter_solstice_log
          hell: 2
          blood: 3

      # Interakcje (6 lokacji - do uzupełnienia)
      interactions:
        infernal:
          bear:
            world: "world"  # [DO UZUPEŁNIENIA]
            x: 0  # [DO UZUPEŁNIENIA]
            y: 0  # [DO UZUPEŁNIENIA]
            z: 0  # [DO UZUPEŁNIENIA]
            block_type: "PLAYER_HEAD"  # lub inny
          krampus:
            world: "world"  # [DO UZUPEŁNIENIA]
            x: 0  # [DO UZUPEŁNIENIA]
            y: 0  # [DO UZUPEŁNIENIA]
            z: 0  # [DO UZUPEŁNIENIA]
            block_type: "PLAYER_HEAD"
        hell:
          bear:
            world: "world"  # [DO UZUPEŁNIENIA]
            x: 0
            y: 0
            z: 0
            block_type: "PLAYER_HEAD"
          krampus:
            world: "world"  # [DO UZUPEŁNIENIA]
            x: 0
            y: 0
            z: 0
            block_type: "PLAYER_HEAD"
        blood:
          bear:
            world: "world"  # [DO UZUPEŁNIENIA]
            x: 0
            y: 0
            z: 0
            block_type: "PLAYER_HEAD"
          krampus:
            world: "world"  # [DO UZUPEŁNIENIA]
            x: 0
            y: 0
            z: 0
            block_type: "PLAYER_HEAD"

      # FAWE Schematics (do uzupełnienia)
      schematics:
        bear:
          infernal: "[DO UZUPEŁNIENIA]"
          hell: "[DO UZUPEŁNIENIA]"
          blood: "[DO UZUPEŁNIENIA]"
        krampus:
          infernal: "[DO UZUPEŁNIENIA]"
          hell: "[DO UZUPEŁNIENIA]"
          blood: "[DO UZUPEŁNIENIA]"

      # MythicMob IDs dla bossów (do uzupełnienia)
      bosses:
        bear:
          infernal: "[DO UZUPEŁNIENIA]"
          hell: "[DO UZUPEŁNIENIA]"
          blood: "[DO UZUPEŁNIENIA]"
        krampus:
          infernal: "[DO UZUPEŁNIENIA]"
          hell: "[DO UZUPEŁNIENIA]"
          blood: "[DO UZUPEŁNIENIA]"

    # ==================== PROGRESS ZA MOBY ====================
    # [DO UZUPEŁNIENIA - szansa na drop progress według poziomu trudności]
    # Możesz użyć drop_chances jak w innych eventach
    drop_chances:
      # TODO: Uzupełnić według poziomu trudności i typu moba
```

---

## 8. Progress System i Drop Chances

### Jak działa:
- Moby x_mas_* dają progress do winter_event
- Progress zależy od poziomu trudności gracza (Infernal/Hell/Blood)
- System używa `drop_chances` jak w Full/New Moon

### 📋 TABELKA DO UZUPEŁNIENIA - Drop Progress:

**Dla mobów na Winter Summit (według poziomu trudności):**

| Mob Type | Infernal (progress) | Hell (progress) | Blood (progress) | Szansa (%) |
|----------|---------------------|-----------------|------------------|------------|
| x_mas_inf | _________ | _________ | _________ | ______% |
| x_mas_hell | _________ | _________ | _________ | ______% |
| x_mas_blood | _________ | _________ | _________ | ______% |
| Bear (infernal) | _________ | - | - | ______% |
| Bear (hell) | - | _________ | - | ______% |
| Bear (blood) | - | - | _________ | ______% |
| Krampus (infernal) | _________ | - | - | ______% |
| Krampus (hell) | - | _________ | - | ______% |
| Krampus (blood) | - | - | _________ | ______% |

**Przykładowa struktura w config.yml:**
```yaml
drop_chances:
  # Mob type -> difficulty -> progress amount -> chance
  x_mas_inf:
    infernal:
      5: 0.8    # 80% szans na 5 progressu
      10: 0.15  # 15% szans na 10 progressu
      20: 0.05  # 5% szans na 20 progressu
    hell:
      10: 0.7
      20: 0.2
      40: 0.1
    blood:
      20: 0.6
      40: 0.3
      80: 0.1
```

---

## 9. Database Schema

### Nowe tabele:

#### 1. winter_cave_daily_rewards (nagrody dla admin GUI)
```sql
CREATE TABLE IF NOT EXISTS winter_cave_daily_rewards (
    event_id VARCHAR(64) NOT NULL,
    day INT NOT NULL,
    item TEXT NOT NULL,  -- Serialized ItemStack
    PRIMARY KEY (event_id, day),
    FOREIGN KEY (event_id) REFERENCES events(event_id)
);
```

#### 2. winter_cave_claims (tracking czy gracz odebrał danego dnia)
```sql
CREATE TABLE IF NOT EXISTS winter_cave_claims (
    event_id VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    event_day INT NOT NULL,  -- Dzień eventu (1-30)
    claimed_at BIGINT NOT NULL,  -- Timestamp
    PRIMARY KEY (event_id, player_uuid, event_day),
    FOREIGN KEY (event_id) REFERENCES events(event_id)
);
```

#### 3. winter_cave_active_instances (locking instancji)
```sql
CREATE TABLE IF NOT EXISTS winter_cave_active_instances (
    event_id VARCHAR(64) PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    entry_time BIGINT NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(event_id)
);
```

#### 4. winter_summit_instances (instancje bossów FAWE)
```sql
CREATE TABLE IF NOT EXISTS winter_summit_instances (
    instance_id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    boss_type VARCHAR(32) NOT NULL,  -- 'bear' lub 'krampus'
    difficulty VARCHAR(32) NOT NULL,  -- 'infernal', 'hell', 'blood'
    created_at BIGINT NOT NULL,
    FOREIGN KEY (event_id) REFERENCES events(event_id)
);
```

---

## 10. Fazy Implementacji

### Faza 1: Globalny Drop System ✅
- [ ] `WinterEventManager`
- [ ] `GiftDropListener` (globalny drop ze WSZYSTKICH mobów)
- [ ] `WinterEventMobListener` (progress za x_mas_*)
- [ ] Konfiguracja gift_drop w config.yml
- [ ] Testowanie dropu

### Faza 2: Winter Cave (Daily Gift)
- [ ] `WinterCaveManager` (instancja locking, timer)
- [ ] `WinterCaveInstance`
- [ ] `WinterCaveDailyRewardDAO` (baza nagród)
- [ ] `WinterCaveGUI` (`/winter_cave`)
- [ ] `WinterCaveRewardsGUI` (`/winter_cave_rewards` admin)
- [ ] `WinterCaveMobListener` (spawn, death)
- [ ] `WinterCavePlayerListener` (timer, cleanup, teleport)
- [ ] Tabele bazy danych
- [ ] Integracja z warpami
- [ ] Testowanie

### Faza 3: Winter Summit (Poziomy trudności)
- [ ] `WinterSummitManager`
- [ ] `DifficultySelectionGUI` (wybór poziomu)
- [ ] Integracja z EventsMainGUI
- [ ] Sprawdzanie wymagań (level, IPS)
- [ ] Integracja z IngredientPouchPlugin
- [ ] Warpy na summit
- [ ] Testowanie

### Faza 4: Summit Bossy (Bear + Krampus)
- [ ] `WinterSummitInstance` (FAWE instancje)
- [ ] `SummitInteractionListener` (honey_bait, winter_solstice_log)
- [ ] `SummitBossListener` (deaths, cleanup, rewards)
- [ ] Spawn bossów w instancjach
- [ ] Pobieranie itemów z IngredientPouch
- [ ] Cleanup instancji
- [ ] Testowanie

### Faza 5: Progress System
- [ ] Drop chances według poziomu trudności
- [ ] Tracking poziomu trudności gracza
- [ ] Integracja z EventManager
- [ ] Testowanie

---

## 11. PODSUMOWANIE - Do Uzupełnienia

### ✅ PILNE:

#### 1. Procenty dla 6 poziomów rarity:
```
Opcja A:
  Green: 50%, Blue: 25%, Purple: 10%, Orange: 6.875%, Gold: 5%, Red: 3.125%

Opcja B:
  Green: 50%, Blue: 25%, Purple: 9.375%, Orange: 7.5%, Gold: 5%, Red: 3.125%

Twoje wartości: ___________________________________
```

#### 2. MythicMob IDs:
```
Winter Cave mob:     _______________
Bear (infernal):     _______________
Bear (hell):         _______________
Bear (blood):        _______________
Krampus (infernal):  _______________
Krampus (hell):      _______________
Krampus (blood):     _______________
```

#### 3. Koordynaty (6 interakcji + 1 player head):
```
Winter Cave player head: X: ____ Y: ____ Z: ____

Infernal Bear:     X: ____ Y: ____ Z: ____
Infernal Krampus:  X: ____ Y: ____ Z: ____
Hell Bear:         X: ____ Y: ____ Z: ____
Hell Krampus:      X: ____ Y: ____ Z: ____
Blood Bear:        X: ____ Y: ____ Z: ____
Blood Krampus:     X: ____ Y: ____ Z: ____
```

#### 4. FAWE Schematics (6 schematów):
```
Bear Infernal:    _______________
Bear Hell:        _______________
Bear Blood:       _______________
Krampus Infernal: _______________
Krampus Hell:     _______________
Krampus Blood:    _______________
```

#### 5. Drop Progress (tabela powyżej - sekcja 8)

---

## 12. Pytania Dodatkowe

1. **Winter Cave:** Czy po zabiciu moba gracz od razu może kliknąć player head, czy musi poczekać?
2. **Summit Bossy:** Czy po zabiciu bossa instancja od razu się usuwa czy gracz ma czas na loot?
3. **Progress:** Czy progress z bossów ma być wyższy niż z normalnych mobów?
4. **GUI EventHub:** Jak ma wyglądać menu wyboru poziomu (layout, itemy)?
5. **Winter Cave rewards:** Czy każdy dzień ma JEDNĄ nagrodę czy można ustawić wiele?

---

## 13. Integracje

### IngredientPouchPlugin:
```java
// Sprawdzanie i pobieranie itemów
PouchHelper.hasEnough(player, "ips", amount)
PouchHelper.takeItem(player, "ips", amount)
PouchHelper.hasEnough(player, "honey_bait", amount)
PouchHelper.takeItem(player, "honey_bait", amount)
PouchHelper.hasEnough(player, "winter_solstice_log", amount)
PouchHelper.takeItem(player, "winter_solstice_log", amount)
```

### EliteLootbox:
```java
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "elb give " + player.getName() + " " + boxId + " 1");
```

### FAWE (instancje jak Map2):
```java
// Podobnie jak w Map2BossSequenceManager i Map2Instance
SchematicHandler.pasteSchematic(location, schematicName);
```

### Warpy (Essentials):
```java
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warp " + warpName + " " + player.getName());
Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
```
