# Event Rewards Preview - Implementation Guide

## ✅ Completed Components

1. **ItemSerializer.java** - Serializes items to/from Base64 for database storage
2. **EventRewardPreviewDAO.java** - Manages showcase rewards in database
3. **EventRewardPreviewGUI.java** - Displays showcase rewards (read-only)
4. **SetEventShowcaseCommand.java** - Admin command to configure showcase items

## 📝 Remaining Manual Changes

### 1. DatabaseManager.java

Add this table creation in `setupTables()` method, after the `new_moon_quest_rewards` table (around line 196):

```java
// Event showcase rewards for rewards preview GUI
st.executeUpdate("CREATE TABLE IF NOT EXISTS event_showcase_rewards(" +
        "event_id VARCHAR(100) PRIMARY KEY," +
        "gui_title VARCHAR(255)," +
        "serialized_inventory TEXT NOT NULL)");
```

### 2. EventsMainGUI.java

#### 2a. Add imports at the top:
```java
import org.bukkit.event.inventory.ClickType;
import org.maks.eventPlugin.db.DatabaseManager;
```

#### 2b. Add field declarations (around line 38):
```java
private final EventRewardPreviewDAO rewardPreviewDAO;
```

#### 2c. Update constructor to accept DatabaseManager (around line 44):
```java
public EventsMainGUI(
        JavaPlugin plugin,
        Map<String, EventManager> eventManagers,
        PlayerProgressGUI progressGUI,
        FullMoonManager fullMoonManager,
        MapSelectionGUI mapSelectionGUI,
        QuestGUI questGUI,
        NewMoonManager newMoonManager,
        Map1SelectionGUI newMoonMapSelectionGUI,
        NewMoonQuestGUI newMoonQuestGUI,
        DatabaseManager databaseManager  // ADD THIS PARAMETER
) {
    this.plugin = plugin;
    this.eventManagers = eventManagers;
    this.progressGUI = progressGUI;
    this.fullMoonManager = fullMoonManager;
    this.mapSelectionGUI = mapSelectionGUI;
    this.questGUI = questGUI;
    this.newMoonManager = newMoonManager;
    this.newMoonMapSelectionGUI = newMoonMapSelectionGUI;
    this.newMoonQuestGUI = newMoonQuestGUI;
    this.rewardPreviewDAO = new EventRewardPreviewDAO(plugin, databaseManager);  // ADD THIS LINE
}
```

#### 2d. Add MMB handling in `onClick()` method (around line 282):
```java
@EventHandler
public void onClick(InventoryClickEvent event) {
    Player player = (Player) event.getWhoClicked();
    Inventory inv = openGUIs.get(player.getUniqueId());

    if (inv == null || !event.getInventory().equals(inv)) {
        return;
    }

    event.setCancelled(true);

    int slot = event.getRawSlot();
    Map<Integer, String> eventSlots = slotToEventId.get(player.getUniqueId());
    if (eventSlots == null) return;

    String eventId = eventSlots.get(slot);
    if (eventId == null) return;

    EventManager manager = eventManagers.get(eventId);
    if (manager == null || !manager.isActive()) {
        player.sendMessage("§c§lThis event is no longer active!");
        player.closeInventory();
        return;
    }

    // ADD THIS: Handle MIDDLE (MMB) click for rewards preview
    if (event.getClick() == ClickType.MIDDLE) {
        player.closeInventory();

        // Check if showcase rewards exist
        if (rewardPreviewDAO.hasShowcaseRewards(eventId)) {
            rewardPreviewDAO.openShowcasePreview(player, eventId);
        } else {
            player.sendMessage("§c§lNo rewards preview configured for this event yet!");
            player.sendMessage("§7An admin must use §e/seteventshowcase " + eventId + " §7to set it up.");
        }
        return;
    }

    // Special handling for Full Moon
    if (eventId.equalsIgnoreCase("full_moon")) {
        // ... rest of existing code
```

#### 2e. Update item lore to mention MMB (optional):
In `createFullMoonItem()`, `createNewMoonItem()`, and `createRegularEventItem()` methods, add this line to the lore:
```java
lore.add("§7Middle-click to preview rewards");
```

### 3. EventPlugin.java

#### 3a. Add field:
```java
private EventRewardPreviewDAO rewardPreviewDAO;
private SetEventShowcaseCommand setShowcaseCommand;
```

#### 3b. In `onEnable()` method, after initializing `databaseManager` (around line 84):
```java
// Initialize reward preview DAO
rewardPreviewDAO = new EventRewardPreviewDAO(this, databaseManager);
```

#### 3c. Update EventsMainGUI initialization (around line 102-143):
Add `databaseManager` parameter to all `EventsMainGUI` constructor calls:
```java
eventsMainGUI = new EventsMainGUI(
    this,
    eventManagers,
    progressGUI,
    fullMoonManager,
    mapSelectionGUI,
    questGUI,
    newMoonManager,
    newMoonMap1SelectionGUI,
    newMoonQuestGUI,
    databaseManager  // ADD THIS
);
```

#### 3d. Register command (around line 256):
```java
// Register /seteventshowcase command
PluginCommand setShowcaseCmd = getCommand("seteventshowcase");
if (setShowcaseCmd != null) {
    setShowcaseCommand = new SetEventShowcaseCommand(this, rewardPreviewDAO);
    setShowcaseCmd.setExecutor(setShowcaseCommand);
    Bukkit.getLogger().info("[EventPlugin] SetEventShowcase command registered");
}
```

### 4. plugin.yml

Add the new command:
```yaml
  seteventshowcase:
    description: Set up showcase rewards for an event
    usage: /seteventshowcase <event_id>
    permission: eventplugin.admin
```

## 🎮 Usage Instructions

### For Admins:
1. Use `/seteventshowcase <event_id>` to configure showcase items
   - Example: `/seteventshowcase full_moon`
2. A GUI will open where you can place items to showcase
3. Close the inventory to save

### For Players:
1. Open `/event_hub`
2. **Middle-click (MMB/scroll click)** on any event placeholder (Full Moon, New Moon, etc.)
3. The rewards preview GUI will open showing the showcase items

## 📊 Database Table

The plugin will automatically create this table:
```sql
CREATE TABLE event_showcase_rewards (
    event_id VARCHAR(100) PRIMARY KEY,
    gui_title VARCHAR(255),
    serialized_inventory TEXT NOT NULL
);
```

## 🔧 Testing Checklist

- [ ] Server starts without errors
- [ ] `/seteventshowcase full_moon` opens GUI for admin
- [ ] Items can be placed and saved in showcase GUI
- [ ] `/event_hub` shows events
- [ ] MMB click on event opens rewards preview
- [ ] Rewards preview displays correct items
- [ ] Items cannot be taken from preview GUI
