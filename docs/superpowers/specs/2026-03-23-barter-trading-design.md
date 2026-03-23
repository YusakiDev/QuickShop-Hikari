# Barter Trading Design Spec

**Date:** 2026-03-23
**Status:** Draft
**Branch:** `feat/barter-trading`
**Target:** Upstream PR to QuickShop-Community/QuickShop-Hikari

## Overview

Add item-for-item barter trading as an opt-in shop mode alongside existing currency-based shops. Players create shops that trade items directly (e.g., 3 iron ingots for 1 diamond) without requiring an economy plugin.

## Golden Rule

Do not change existing behavior. Barter is additive. Currency shops continue to work exactly as they do today. When `barter.enabled` is false, QuickShop behaves identically to upstream.

## Data Model

One new nullable field on `Shop`:

```java
@Nullable ItemStack getPriceItem();
void setPriceItem(@Nullable ItemStack item);
boolean isBarter();  // convenience: return getPriceItem() != null
```

- `getPriceItem()` returns null for currency shops (existing behavior unchanged)
- When non-null, the ItemStack's amount encodes the price quantity (e.g., `IRON_INGOT` with amount 3 = "costs 3 iron ingots")
- `isBarter()` is the branch predicate used throughout the codebase

### Database

One new nullable column on the `DATA` table:

```sql
ALTER TABLE {table} ADD COLUMN price_item TEXT DEFAULT NULL;
```

Stores `ItemStack.serializeAsBytes()` encoded as Base64. NULL means currency shop.

**New installs:** The `DataTables.DATA` enum definition must include `price_item TEXT DEFAULT NULL` in the CREATE TABLE statement so new installs get the column without needing migration.

**Existing installs:** ALTER TABLE migration runs automatically on startup when the column doesn't exist. No data loss — all existing shops remain currency shops with `price_item = NULL`.

## Shop Creation Flow

The creation GUI is redesigned as a unified interface for both currency and barter shops.

### Unified Creation GUI

Player clicks an empty chest. A GUI opens:

```
+--------------------------------------+
|           Create Shop                |
|                                      |
|    Selling          Price            |
|   [-] [item] [+]   [-] [item] [+]   |
|                                      |
|            [Confirm]                 |
+--------------------------------------+
```

**Selling slot:** Player places the item they want to sell. Slot shows a copy; original stays in inventory.

**Price slot:** Player places the item they want as payment. Same copy behavior.

**+/- buttons:** Adjacent to each slot, adjust the stack count displayed on the item. For quantities above the item's max stack size (e.g., 128 iron ingots), the quantity is shown via the item's display name lore (e.g., "Amount: 128") since vanilla stack rendering caps at 64.

**Confirm:** Creates the shop with the configured items and amounts.

### Mode Selection

- **Barter-only server** (no economy loaded, `barter.enabled: true`): GUI opens directly. Price slot accepts items only.
- **Currency-only server** (`barter.enabled: false`): Existing creation flow unchanged (click chest, chat prompt for price number).
- **Both modes available** (economy loaded, `barter.enabled: true`): GUI opens with a mode toggle — "Currency" shows a number input for the price side, "Barter" shows the item slot.

### Edge Cases

- Player closes GUI without confirming: creation cancelled
- Player confirms without placing items: error message, stays in GUI
- Price item same as shop item: blocked if `allow-same-item: false`

## Economy Null-Guard Bypass

Multiple methods in `SimpleShopManager` hard-block when no economy provider is loaded:

- `actionCreate()` — line 446: returns with "Economy system not loaded" error
- `actionTrade()` / `actionBuying()` / `actionSelling()` — similar guards via `shopIsNotValid()`

For barter to work without an economy plugin, each of these guards must be bypassed for barter shops:

```
if (provider == null && !shop.isBarter()) {
    // existing error — only for currency shops
}
```

For `actionCreate()`, the guard fires before the shop exists, so the check is against the config:

```
if (provider == null && !barterEnabled) {
    // error
}
```

This is the core enablement change. Without it, barter shops cannot be created or traded on servers without an economy plugin.

## Transaction Flow

### Barter Transaction Coordinator

Two `SimpleInventoryTransaction`s must execute as a coordinated pair. Since each `SimpleInventoryTransaction` has its own independent rollback stack, a coordinator is needed:

```
1. Run Payment transaction (remove price items from buyer, add to shop)
2. If Payment fails → abort, notify player
3. Run Delivery transaction (remove shop items from shop, add to buyer)
4. If Delivery fails → rollback Payment transaction, then abort
5. Both succeeded → commit
```

This is NOT atomic in the database sense — there is a window between steps 1 and 3 where payment has occurred but delivery hasn't. The coordinator handles this by explicitly rolling back payment on delivery failure. This matches how the existing `QSEconomyTransaction` + `SimpleInventoryTransaction` pair works for currency shops (economy commit, then inventory, rollback economy on inventory failure).

### Buying from a Sell Shop

Example: shop sells diamonds, price is 3 iron ingots.

1. Player clicks the shop
2. Trade UI shows: "Buy 1 Diamond for 3 Iron Ingots"
3. Player confirms amount
4. Barter transaction coordinator runs:
   - **Payment:** Remove 3 iron ingots from player inventory, add to shop chest
   - **Delivery:** Remove 1 diamond from shop chest, add to player inventory
5. If delivery fails, payment rolls back. Player notified.

### Selling to a Buy Shop

Example: shop buys diamonds, pays 3 iron ingots.

1. Trade UI shows: "Sell 1 Diamond for 3 Iron Ingots"
2. **Payment:** Remove 1 diamond from player inventory, add to shop chest
3. **Delivery:** Remove 3 iron ingots from shop chest, add to player inventory

### Pre-Trade Checks (Barter-Specific)

- Does buyer have enough price items? If not: "You don't have enough [item name]"
- Does shop chest have space for incoming price items? This requires a **separate space check** against the price item type, not `getRemainingSpace()` which only measures space for the shop's sell item. Use `Util.countSpace(inventory, priceItemStack)` directly.
- Does shop chest have enough stock? If not: "Shop is out of stock"

### What Does NOT Run for Barter Shops

- `QSEconomyTransaction` — skipped entirely
- Tax calculation — no currency to tax
- Benefit splits — no currency to split
- Economy balance checks — not applicable

### Event Compatibility

Existing `ShopPurchaseEvent` and `ShopSuccessPurchaseEvent` still fire. They carry the `Shop` reference, so consumers can call `shop.isBarter()` and `shop.getPriceItem()`.

Currency fields (`total`, `tax`) are 0.0 for barter trades. **Compatibility caveat:** Third-party plugins listening to these events must check `shop.isBarter()` before interpreting `total` or `tax` values, as 0.0 does not mean "free" — it means "paid in items." This caveat should be documented in the event's Javadoc.

### Purchase Logging

Barter trades are logged to `LOG_PURCHASE` with `money = 0.0` and `tax = 0.0`. The price item info is not stored in the log table (would require schema changes to a logging table, not worth it for v1). The shop history UI will show barter trades with a "Barter" label instead of a currency amount, using `shop.isBarter()` to branch the display.

## Price Editing

When a shop owner clicks their shop, the keeper menu opens (existing behavior).

**"Change Price" button behavior:**

- **Currency shop:** Existing chat prompt for a number (unchanged)
- **Barter shop:** Opens the price item GUI with the current price item pre-populated. Player can swap the item or adjust quantity with +/- buttons.

**`/qs price` command:**

- When targeting a barter shop, opens the price item GUI instead of expecting a number argument
- When targeting a currency shop, existing behavior (unchanged)

## Display & Signs

### Sign Layout

Existing sign layout, line 4 changes for barter:

**Currency:**
```
[Shop header]
Buy/Sell
Diamond
$5.00
```

**Barter:**
```
[Shop header]
Buy/Sell
Diamond
3x Iron Ingot
```

Line 4 uses the same item name formatter QuickShop uses for shop items.

### Display Item on Chest

Unchanged. Shows what the shop sells, not the price.

### Chat Messages

Wherever QuickShop formats a price string (e.g., "costs $5.00"), a barter branch formats as "costs 3x Iron Ingot" using the existing item name resolver.

### Map Addons

Addons (bluemap, dynmap, pl3xmap) call `shop.getPrice()` for display. For barter shops, they can check `shop.getPriceItem()`. Addon updates are follow-up work, not blocking for the core feature.

## Configuration

```yaml
barter:
  enabled: true              # enable barter shop creation
  allow-same-item: false     # prevent pricing an item with itself
```

**Behavior when `barter.enabled: true` and no economy provider is loaded:**

The economy null-guards in `actionCreate()`, `actionTrade()`, `shopIsNotValid()`, `actionBuying()`, and `actionSelling()` are bypassed for barter shops. QuickShop starts normally without an economy plugin. Only barter shops can be created in this mode. Attempting to create a currency shop without an economy plugin still shows the existing error.

## Permissions

One new permission:

```yaml
quickshop.create.barter:
  default: true
  description: Allow creating barter shops
```

## API Summary

### New Methods on `Shop`

```java
@Nullable ItemStack getPriceItem();
void setPriceItem(@Nullable ItemStack item);
boolean isBarter();
```

### New Method on `DataRecord`

```java
@Nullable String getPriceItem();  // Base64-encoded ItemStack, null for currency shops
```

### No New Events

Existing events carry `Shop` reference. Consumers check `shop.isBarter()`. Javadoc updated to document that `total = 0.0` on barter trades does not mean free.

### No New Commands

- `/qs create` still works for currency shops
- `/qs price` detects barter shops and opens the GUI
- Barter creation uses the chest-click GUI

## Files Expected to Change

| File | Change |
|------|--------|
| `Shop.java` (API) | Add 3 new methods |
| `DataRecord.java` (API) | Add `getPriceItem()` method |
| `ContainerShop.java` | Add `priceItem` field, persist/load, implement new methods. Update `inventoryAvailable()` to use `Util.countSpace(getInventory(), getPriceItem())` for barter buy shops so sign status and trade validation correctly reflect space for incoming price items |
| `SimpleDataRecord.java` | Add `priceItem` field, implement `DataRecord.getPriceItem()` |
| `DataTables.java` | Add `price_item` to CREATE TABLE and ALTER TABLE migration |
| `SimpleDatabaseHelperV2.java` | Read/write new field |
| `ShopInfoStorage.java` | Add `priceItem` field, update constructor (15th param) and all call sites (`ContainerShop.saveToInfoStorage()`) |
| `ShopLoader.java` | Deserialize new field |
| `SimpleShopManager.java` | Bypass economy null-guards for barter in `actionCreate`, `actionTrade`, `shopIsNotValid`, `actionBuying`, `actionSelling`; new creation GUI; barter transaction coordinator |
| `SimpleShopLayoutProvider.java` | Barter price rendering on signs |
| `MainPage.java` (keeper menu) | Barter-aware "Change Price" button |
| `ShopPurchaseEvent.java` / `ShopSuccessPurchaseEvent.java` | Javadoc update for barter caveat |
| `config.yml` | Add `barter` section |
| `plugin.yml` | Add `quickshop.create.barter` permission |
| `messages.yml` | Add barter-related message keys |

## What This Does NOT Change

- Existing currency shop behavior
- `QSEconomyTransaction` class
- `EconomyProvider` interface
- Tax system
- Benefit system
- Display item rendering
- Shop protection
- Any compatibility module
