# Barter Trading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add item-for-item barter trading as an opt-in shop mode to QuickShop-Hikari, keeping all existing currency behavior untouched.

**Architecture:** Add a nullable `priceItem` field to the Shop API. When set, the shop operates in barter mode — trades use two `SimpleInventoryTransaction`s instead of a `QSEconomyTransaction`. A new unified creation GUI replaces the old chat-prompt flow. Economy null-guards are bypassed for barter shops.

**Tech Stack:** Java 21, Paper API 1.21, Maven, EasySQL, PacketEvents (optional for display), Adventure (MiniMessage)

**Spec:** `docs/superpowers/specs/2026-03-23-barter-trading-design.md`

**Codebase:** `/mnt/d/OtherGit/QuickShop-Hikari`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `quickshop-api/.../api/shop/Shop.java` | Modify | Add `getPriceItem()`, `setPriceItem()`, `isBarter()` |
| `quickshop-api/.../api/database/bean/DataRecord.java` | Modify | Add `getPriceItem()` returning nullable String |
| `quickshop-api/.../api/shop/ShopInfoStorage.java` | Modify | Add `priceItem` field and constructor param |
| `quickshop-bukkit/.../database/DataTables.java` | Modify | Add `price_item` column to DATA table |
| `quickshop-bukkit/.../database/bean/SimpleDataRecord.java` | Modify | Add `priceItem` field, read from ResultSet, include in params |
| `quickshop-bukkit/.../database/SimpleDatabaseHelperV2.java` | Modify | Add migration for `price_item` column |
| `quickshop-bukkit/.../shop/ContainerShop.java` | Modify | Add `priceItem` field, constructor param, `inventoryAvailable()` branch, `saveToInfoStorage()` update |
| `quickshop-bukkit/.../shop/ShopLoader.java` | Modify | Deserialize `priceItem` from DataRecord |
| `quickshop-bukkit/.../shop/SimpleShopManager.java` | Modify | Economy null-guard bypass, barter transaction coordinator in `actionBuying`/`actionSelling`, creation GUI hookup |
| `quickshop-bukkit/.../shop/SimpleShopLayoutProvider.java` | Modify | Barter price rendering on sign line 4 |
| `quickshop-bukkit/.../menu/keeper/MainPage.java` | Modify | Barter-aware "Change Price" button |
| `quickshop-bukkit/src/main/resources/config.yml` | Modify | Add `barter` section |
| `quickshop-bukkit/src/main/resources/plugin.yml` | Modify | Add `quickshop.create.barter` permission |
| `quickshop-bukkit/src/main/resources/lang/messages.yml` | Modify | Add barter message keys |

---

### Task 1: API — Add barter methods to Shop interface

**Files:**
- Modify: `quickshop-api/src/main/java/com/ghostchu/quickshop/api/shop/Shop.java`

- [ ] **Step 1: Add barter methods to Shop interface**

After the `setCurrency` method (~line 68), add:

```java
/**
 * Gets the price item for barter shops.
 *
 * @return The price ItemStack (amount encodes quantity), or null if this is a currency shop
 */
@Nullable
ItemStack getPriceItem();

/**
 * Sets the price item for barter trading.
 * Setting to null converts the shop back to currency mode.
 *
 * @param priceItem The price item with amount, or null to use currency
 */
void setPriceItem(@Nullable ItemStack priceItem);

/**
 * Check if this shop uses barter (item-for-item) trading.
 *
 * @return true if the shop has a price item set
 */
default boolean isBarter() {
    return getPriceItem() != null;
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -pl quickshop-api -Pgithub -q`
Expected: BUILD SUCCESS (interface change, no implementations yet — will break downstream until Task 2)

- [ ] **Step 3: Commit**

```bash
git add quickshop-api/src/main/java/com/ghostchu/quickshop/api/shop/Shop.java
git commit -m "feat(api): add barter trading methods to Shop interface"
```

---

### Task 2: API — Add priceItem to DataRecord and ShopInfoStorage

**Files:**
- Modify: `quickshop-api/src/main/java/com/ghostchu/quickshop/api/database/bean/DataRecord.java`
- Modify: `quickshop-api/src/main/java/com/ghostchu/quickshop/api/shop/ShopInfoStorage.java`

- [ ] **Step 1: Add getPriceItem to DataRecord interface**

In `DataRecord.java`, after `double getPrice();` (line 41), add:

```java
/**
 * Gets the serialized price item for barter shops.
 *
 * @return Base64-encoded ItemStack bytes, or null for currency shops
 */
@Nullable
String getPriceItem();
```

- [ ] **Step 2: Add priceItem field to ShopInfoStorage**

In `ShopInfoStorage.java`, add field after `permission` (line 29):

```java
@Nullable
private final String priceItem;
```

Update the constructor (line 31) to add `@Nullable final String priceItem` as the last parameter, and add `this.priceItem = priceItem;` to the body.

- [ ] **Step 3: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -pl quickshop-api -Pgithub -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add quickshop-api/
git commit -m "feat(api): add priceItem to DataRecord and ShopInfoStorage"
```

---

### Task 3: Database — Add price_item column and migration

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/database/DataTables.java`
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/database/SimpleDatabaseHelperV2.java`

- [ ] **Step 1: Add price_item to DATA table definition**

In `DataTables.java`, after `table.addColumn("benefit", "MEDIUMTEXT");` (line 50), add:

```java
table.addColumn("price_item", "TEXT"); // BARTER PRICE ITEM (NULL = currency shop)
```

- [ ] **Step 2: Add migration in SimpleDatabaseHelperV2**

Find the migration/upgrade section in `SimpleDatabaseHelperV2.java`:
1. Increment `LATEST_DATABASE_VERSION` from `19` to `20` (line 61)
2. In `DatabaseUpgrade.upgrade()`, add a new versioned block: `if (currentDatabaseVersion == 19)` that runs the ALTER TABLE to add the `price_item` column, then updates the version to 20
3. Follow the same pattern as existing version upgrade blocks in the upgrade ladder (lines 990-1046)

- [ ] **Step 3: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -pl quickshop-bukkit -Pgithub -q`
Expected: Compilation errors (SimpleDataRecord doesn't implement getPriceItem yet — expected, fixed in Task 4)

- [ ] **Step 4: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/database/
git commit -m "feat(db): add price_item column to DATA table with migration"
```

---

### Task 4: Data layer — Add priceItem to SimpleDataRecord

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/database/bean/SimpleDataRecord.java`

- [ ] **Step 1: Add priceItem field**

After `private final String benefit;` (line 37), add:

```java
@Nullable
private final String priceItem;
```

- [ ] **Step 2: Update both constructors**

In the manual constructor (line 39), add `@Nullable final String priceItem` as the last parameter and `this.priceItem = priceItem;`.

In the ResultSet constructor (line 63), after `this.benefit = set.getString("benefit");` (line 88), add:

```java
this.priceItem = set.getString("price_item");
```

- [ ] **Step 3: Update generateParams()**

In `generateParams()` (line 100), after `map.put("benefit", benefit);` (line 122), add:

```java
map.put("price_item", priceItem);
```

- [ ] **Step 4: Add getter (implements DataRecord.getPriceItem)**

```java
@Override
@Nullable
public String getPriceItem() {
    return priceItem;
}
```

- [ ] **Step 5: Fix all SimpleDataRecord constructor call sites**

Search for `new SimpleDataRecord(` across the entire codebase. Each call site using the manual constructor (not the ResultSet one) needs `null` appended as the last argument for `priceItem`. This may include sites in `SimpleShopManager`, `ShopLoader`, or test files.

- [ ] **Step 6: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -pl quickshop-bukkit -Pgithub -q`
Expected: Compilation errors in ContainerShop (doesn't implement Shop.getPriceItem yet — expected, fixed in Task 5)

- [ ] **Step 7: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/database/bean/SimpleDataRecord.java
git commit -m "feat(db): add priceItem field to SimpleDataRecord"
```

---

### Task 5: ContainerShop — Add priceItem field and barter methods

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/ContainerShop.java`

- [ ] **Step 1: Add priceItem field**

After `private BenefitProvider benefit;` (line 146), add:

```java
@Nullable
private ItemStack priceItem;
```

- [ ] **Step 2: Add priceItem to constructor**

Add `@Nullable final ItemStack priceItem` as the last parameter in the constructor (line 166). Add `this.priceItem = priceItem;` to the constructor body.

- [ ] **Step 3: Implement Shop interface methods**

```java
@Override
@Nullable
public ItemStack getPriceItem() {
    return priceItem != null ? priceItem.clone() : null;
}

@Override
public void setPriceItem(@Nullable final ItemStack priceItem) {
    this.priceItem = priceItem != null ? priceItem.clone() : null;
    setDirty();
}
```

- [ ] **Step 4: Update inventoryAvailable() for barter**

Replace the existing `inventoryAvailable()` method (line 975) to handle barter buy shops:

```java
@Override
public boolean inventoryAvailable() {
    if (isUnlimited()) {
        return true;
    }
    if (isSelling()) {
        return getRemainingStock() > 0;
    }
    if (isBuying()) {
        if (isBarter() && priceItem != null) {
            // For barter buy shops, check space for incoming shop items (what buyers send in)
            return Util.countSpace(getInventory(), this.item) > 0;
        }
        return getRemainingSpace() > 0;
    }
    if (isFrozen()) {
        return false;
    }
    return true;
}
```

- [ ] **Step 5: Update saveToInfoStorage()**

Add `priceItem != null ? Base64.getEncoder().encodeToString(priceItem.serializeAsBytes()) : null` as the last argument to the `ShopInfoStorage` constructor call at line 1475.

- [ ] **Step 6: Fix all ContainerShop constructor call sites**

Search for `new ContainerShop(` in the codebase. Each call site needs `null` (or the loaded priceItem) appended as the last argument. Key locations:
- `SimpleShopManager.actionCreate()` — pass `null` for now (creation GUI will set it later)
- `ShopLoader` — pass the deserialized priceItem from DataRecord

- [ ] **Step 7: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -Pgithub -q`
Expected: BUILD SUCCESS (all interface methods now implemented)

- [ ] **Step 8: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/ContainerShop.java
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/ShopLoader.java
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java
git commit -m "feat(shop): add priceItem field to ContainerShop with barter support"
```

---

### Task 6: Config — Add barter configuration

**Files:**
- Modify: `quickshop-bukkit/src/main/resources/config.yml`
- Modify: `quickshop-bukkit/src/main/resources/plugin.yml`
- Modify: `quickshop-bukkit/src/main/resources/lang/messages.yml`

- [ ] **Step 1: Add barter section to config.yml**

Find an appropriate location in `config.yml` (near the `shop` section) and add:

```yaml
# Barter trading - item-for-item shops without currency
barter:
  # Enable barter shop creation
  enabled: true
  # Prevent shops from pricing an item with itself
  allow-same-item: false
```

- [ ] **Step 2: Add permission to plugin.yml**

In the permissions section of `plugin.yml`, add:

```yaml
quickshop.create.barter:
  default: true
  description: Allow creating barter shops
```

- [ ] **Step 3: Add barter message keys to messages.yml**

Add under appropriate section:

```yaml
barter:
  price-format: "<amount>x <item>"
  not-enough-items: "<red>You don't have enough <item>.</red>"
  shop-full: "<red>This shop is full and cannot accept more items.</red>"
  creation-gui-title: "Create Shop"
  price-gui-title: "Set Barter Price"
  selling-label: "Selling"
  price-label: "Price"
  confirm: "Confirm"
  place-sell-item: "Place the item you want to sell"
  place-price-item: "Place the item you want as payment"
```

- [ ] **Step 4: Commit**

```bash
git add quickshop-bukkit/src/main/resources/
git commit -m "feat(config): add barter configuration, permission, and messages"
```

---

### Task 7: Economy null-guard bypass for barter

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java`

- [ ] **Step 1: Read the barter config flag**

Add a `barterEnabled` boolean field to `SimpleShopManager`, loaded from `config.getBoolean("barter.enabled", false)` during initialization.

- [ ] **Step 2: Bypass economy null-guard in actionCreate()**

At line 446 in `actionCreate()`, change:

```java
if(plugin.getEconomyManager().provider() == null) {
```

to:

```java
if(plugin.getEconomyManager().provider() == null && !barterEnabled) {
```

- [ ] **Step 3: Bypass economy null-guard in actionTrade()**

`actionTrade()` at line 1313 has its own independent null-guard that fires BEFORE `shopIsNotValid`:

```java
if(plugin.getEconomyManager().provider() == null) {
```

This must be bypassed using the shop's barter status. The shop is loaded by this point (from `getShop(info.getLocation())`), so use:

```java
if(plugin.getEconomyManager().provider() == null && !shop.isBarter()) {
```

Additionally, `actionTrade()` passes `eco` (the economy provider) to `actionBuying`/`actionSelling`. When `eco` is null (barter-only server), the barter branch in those methods must be reached BEFORE any code that dereferences `eco`. The barter branch (Task 8) returns early, so `eco` is never used for barter shops. But guard defensively: if `eco == null && !shop.isBarter()`, return with error.

- [ ] **Step 4: Bypass economy null-guard in shopIsNotValid() and actionBuying/actionSelling**

Find remaining `provider() == null` checks in `shopIsNotValid`, `actionBuying`, and `actionSelling`. For each:

```java
if(plugin.getEconomyManager().provider() == null && !shop.isBarter()) {
```

The pattern: barter shops skip all economy requirements. Currency shops keep existing behavior.

- [ ] **Step 4: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -Pgithub -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java
git commit -m "feat(trade): bypass economy null-guards for barter shops"
```

---

### Task 8: Transaction flow — Barter buy/sell logic

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java`

- [ ] **Step 1: Add barter branch in actionBuying()**

In `actionBuying()`, after the pre-trade checks, add a branch:

```java
if (shop.isBarter()) {
    // Barter transaction: two inventory transactions
    final ItemStack priceItem = shop.getPriceItem();
    final int totalPriceAmount = priceItem.getAmount() * amount;

    // Check buyer has enough price items
    if (Util.countItems(buyerInventory, priceItem) < totalPriceAmount) {
        plugin.text().of(buyer, "barter.not-enough-items",
            Util.getItemStackName(priceItem)).send();
        return false;
    }

    // Check shop has space for incoming price items
    final ItemStack priceCheck = priceItem.clone();
    priceCheck.setAmount(totalPriceAmount);
    if (Util.countSpace(shop.getInventory(), priceCheck) < totalPriceAmount) {
        plugin.text().of(buyer, "barter.shop-full").send();
        return false;
    }

    // Payment: remove price items from buyer, add to shop
    final SimpleInventoryTransaction paymentTxn = SimpleInventoryTransaction.builder()
        .from(buyerInventory)
        .to(shop.getInventory())
        .item(priceItem)
        .amount(totalPriceAmount)
        .build();

    if (!paymentTxn.commit()) {
        plugin.text().of(buyer, "barter.not-enough-items",
            Util.getItemStackName(priceItem)).send();
        return false;
    }

    // Delivery: shop gives items to buyer
    try {
        shop.buy(buyerQUser, buyerInventory, buyer.getLocation(), amount);
    } catch (Exception e) {
        // Rollback payment
        paymentTxn.rollback(true);
        plugin.text().of(buyer, "shop-out-of-stock").send();
        return false;
    }

    // Fire success event
    // ... (mirror existing ShopSuccessPurchaseEvent firing with total=0.0)

    return true;
}
// ... existing currency logic unchanged below
```

- [ ] **Step 2: Add barter branch in actionSelling()**

Same pattern as actionBuying but reversed — buyer gives shop items, gets price items.

```java
if (shop.isBarter()) {
    final ItemStack priceItem = shop.getPriceItem();
    final int totalPriceAmount = priceItem.getAmount() * amount;

    // Check shop has enough price items to pay
    if (Util.countItems(shop.getInventory(), priceItem) < totalPriceAmount) {
        plugin.text().of(seller, "shop-out-of-stock").send();
        return false;
    }

    // Check shop has space for incoming sold items
    if (Util.countSpace(shop.getInventory(), shop.getItem()) < amount) {
        plugin.text().of(seller, "barter.shop-full").send();
        return false;
    }

    // Delivery: seller gives items to shop
    try {
        shop.sell(sellerQUser, sellerInventory, seller.getLocation(), amount);
    } catch (Exception e) {
        plugin.text().of(seller, "not-enough-items").send();
        return false;
    }

    // Payment: shop gives price items to seller
    final SimpleInventoryTransaction paymentTxn = SimpleInventoryTransaction.builder()
        .from(shop.getInventory())
        .to(sellerInventory)
        .item(priceItem)
        .amount(totalPriceAmount)
        .build();

    if (!paymentTxn.commit()) {
        // Rollback: give sold items back to seller from shop
        final SimpleInventoryTransaction rollbackTxn = SimpleInventoryTransaction.builder()
            .from(shop.getInventory())
            .to(sellerInventory)
            .item(shop.getItem())
            .amount(amount)
            .build();
        rollbackTxn.commit();
        plugin.text().of(seller, "shop-out-of-stock").send();
        return false;
    }

    return true;
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -Pgithub -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java
git commit -m "feat(trade): add barter transaction flow with coordinator rollback"
```

---

### Task 9: Sign display — Barter price rendering

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopLayoutProvider.java`

- [ ] **Step 1: Add barter price formatting**

Find the `renderPrice()` method (around line 191). Add a barter branch at the top:

```java
if (shop.isBarter() && shop.getPriceItem() != null) {
    final ItemStack priceItem = shop.getPriceItem();
    final String itemName = Util.getItemStackName(priceItem);
    return Component.text(priceItem.getAmount() + "x " + itemName);
}
// existing currency formatting below
```

- [ ] **Step 2: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -Pgithub -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopLayoutProvider.java
git commit -m "feat(display): render barter price on shop signs"
```

---

### Task 10: Creation GUI — Unified shop creation interface

**Files:**
- Create: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/menu/creation/ShopCreationMenu.java`
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java`

This is the largest task. The creation GUI replaces the chat prompt for barter servers and adds item slots for both sell and price items.

- [ ] **Step 1: Create ShopCreationMenu class**

Create a new GUI class following the pattern of existing menus in `quickshop-bukkit/.../menu/`. The GUI should:
- Open a chest inventory (27 slots / 3 rows)
- Slot layout:
  - Row 1: Labels (glass panes with names "Selling" and "Price")
  - Row 2 center-left: sell item slot (slot 10), with [-] at slot 9 and [+] at slot 11
  - Row 2 center-right: price item slot (slot 14), with [-] at slot 13 and [+] at slot 15
  - Row 3 center: confirm button (slot 22, green wool/concrete)
- Handle click events: item placement copies the item, +/- adjust amounts, confirm creates the shop
- On confirm: call into `SimpleShopManager.createShop()` with the configured items

Read these specific reference files for GUI conventions:
- `quickshop-bukkit/.../menu/ShopKeeperMenu.java` — how menus are opened
- `quickshop-bukkit/.../menu/keeper/MainPage.java` — how IconBuilder + GuiConfig renders icons
- `quickshop-bukkit/.../menu/shared/QuickShopPage.java` — base page class
- `quickshop-bukkit/.../menu/shared/GuiChatAction.java` — chat input from GUI
- `quickshop-bukkit/.../menu/shared/GuiChatInputManager.java` — chat input manager
- `quickshop-bukkit/.../config/GuiConfig.java` — GUI configuration
- `quickshop-bukkit/.../config/GuiIconBuilder.java` — icon builder pattern

- [ ] **Step 2: Hook creation GUI into shop creation flow**

In `SimpleShopManager`, when a player triggers shop creation and barter is enabled:
- Instead of storing `SimpleInfo` and waiting for chat input, open `ShopCreationMenu`
- The menu's confirm handler calls `actionCreate()` with the configured price

For barter-only servers (no economy), always use the GUI.
For dual-mode servers, open the GUI with a mode toggle.
For currency-only servers (`barter.enabled: false`), keep existing chat prompt flow.

- [ ] **Step 3: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -Pgithub -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Manual test**

Start a test server with no economy plugin and `barter.enabled: true`:
1. Click an empty chest → creation GUI should open
2. Place diamond in sell slot, place iron ingot in price slot
3. Adjust amounts with +/-
4. Click confirm → shop created
5. Check sign shows "3x Iron Ingot"

- [ ] **Step 5: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/menu/creation/
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/shop/SimpleShopManager.java
git commit -m "feat(gui): add unified shop creation GUI with barter support"
```

---

### Task 11: Keeper menu — Barter-aware price editing

**Files:**
- Modify: `quickshop-bukkit/src/main/java/com/ghostchu/quickshop/menu/keeper/MainPage.java`

- [ ] **Step 1: Update "Change Price" button for barter shops**

In `MainPage.java`, find the change-price icon setup (around line 96-147). Add a branch:

If `shop.get().isBarter()`:
- Instead of opening a chat prompt for a number, open a price item GUI (similar to creation GUI's price slot, but for editing)
- Pre-populate with current price item
- On confirm, call `shop.get().setPriceItem(newPriceItem)` and `shop.get().update()`

If not barter:
- Keep existing chat prompt behavior (unchanged)

- [ ] **Step 2: Verify compilation**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn compile -Pgithub -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add quickshop-bukkit/src/main/java/com/ghostchu/quickshop/menu/keeper/MainPage.java
git commit -m "feat(gui): barter-aware price editing in keeper menu"
```

---

### Task 12: Full build and integration test

- [ ] **Step 1: Full Maven build**

Run: `cd /mnt/d/OtherGit/QuickShop-Hikari && mvn clean install -Pgithub`
Expected: BUILD SUCCESS with no errors

- [ ] **Step 2: Integration test checklist**

On a test server with barter enabled and no economy plugin:
1. Server starts without economy error
2. Click chest → creation GUI opens
3. Create a barter sell shop (diamond for 3 iron ingots)
4. Sign displays "3x Iron Ingot" on line 4
5. Another player clicks shop → buys diamond, loses 3 iron ingots
6. Shop chest now has 3 iron ingots, one fewer diamond
7. Chest full → trade blocked with "shop is full" message
8. Shop out of stock → trade blocked with "out of stock" message
9. Owner changes price via keeper menu → price item updated
10. Create a barter buy shop → reverse trade works correctly

On a test server WITH economy plugin and `barter.enabled: true`:
11. Currency shops still work exactly as before
12. Barter shops work alongside currency shops
13. No errors in console

- [ ] **Step 3: Push branch**

```bash
cd /mnt/d/OtherGit/QuickShop-Hikari
git push YusakiDev feat/barter-trading
```

- [ ] **Step 4: Commit any final fixes**

If integration tests reveal issues, fix and commit incrementally.
