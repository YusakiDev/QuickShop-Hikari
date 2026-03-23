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

Stores `ItemStack.serializeAsBytes()` encoded as Base64. NULL means currency shop. Migration runs automatically on startup when the column doesn't exist. No data loss — all existing shops remain currency shops with `price_item = NULL`.

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

**+/- buttons:** Adjacent to each slot, adjust the stack count displayed on the item. The item in the slot visually shows the quantity via vanilla stack size rendering.

**Confirm:** Creates the shop with the configured items and amounts.

### Mode Selection

- **Barter-only server** (no economy loaded, `barter.enabled: true`): GUI opens directly. Price slot accepts items only.
- **Currency-only server** (`barter.enabled: false`): Existing creation flow unchanged (click chest, chat prompt for price number).
- **Both modes available** (economy loaded, `barter.enabled: true`): GUI opens with a mode toggle — "Currency" shows a number input for the price side, "Barter" shows the item slot.

### Edge Cases

- Player closes GUI without confirming: creation cancelled
- Player confirms without placing items: error message, stays in GUI
- Price item same as shop item: blocked if `allow-same-item: false`

## Transaction Flow

### Buying from a Sell Shop

Example: shop sells diamonds, price is 3 iron ingots.

1. Player clicks the shop
2. Trade UI shows: "Buy 1 Diamond for 3 Iron Ingots"
3. Player confirms amount
4. Two `SimpleInventoryTransaction`s run atomically:
   - **Payment:** Remove 3 iron ingots from player inventory, add to shop chest
   - **Delivery:** Remove 1 diamond from shop chest, add to player inventory
5. If either transaction fails, both roll back. No partial trades.

### Selling to a Buy Shop

Example: shop buys diamonds, pays 3 iron ingots.

1. Trade UI shows: "Sell 1 Diamond for 3 Iron Ingots"
2. **Payment:** Remove 3 iron ingots from shop chest, add to player inventory
3. **Delivery:** Remove 1 diamond from player inventory, add to shop chest

### Pre-Trade Checks (Barter-Specific)

- Does buyer have enough price items? If not: "You don't have enough [item name]"
- Does shop chest have space for incoming items? If not: "Shop is full"
- Does shop chest have enough stock? If not: "Shop is out of stock"

### What Does NOT Run for Barter Shops

- `QSEconomyTransaction` — skipped entirely
- Tax calculation — no currency to tax
- Benefit splits — no currency to split
- Economy balance checks — not applicable

### Event Compatibility

Existing `ShopPurchaseEvent` and `ShopSuccessPurchaseEvent` still fire. They carry the `Shop` reference, so consumers can call `shop.isBarter()` and `shop.getPriceItem()`. Currency fields (`total`, `tax`) are 0.0 for barter trades.

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
  allow-without-economy: true  # allow server to run without economy plugin
```

When `barter.enabled: true` and no economy provider is loaded, QuickShop starts normally instead of showing an error. Only barter shops can be created in this mode.

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

### No New Events

Existing events carry `Shop` reference. Consumers check `shop.isBarter()`.

### No New Commands

- `/qs create` still works for currency shops
- `/qs price` detects barter shops and opens the GUI
- Barter creation uses the chest-click GUI

## Files Expected to Change

| File | Change |
|------|--------|
| `Shop.java` (API) | Add 3 new methods |
| `ContainerShop.java` | Add `priceItem` field, persist/load, implement new methods |
| `SimpleDataRecord.java` / `DataRecord.java` | Add `priceItem` field |
| `DataTables.java` | Add `price_item` column, migration |
| `SimpleDatabaseHelperV2.java` | Read/write new field |
| `ShopInfoStorage.java` | Add `priceItem` field |
| `ShopLoader.java` | Deserialize new field |
| `SimpleShopManager.java` | Branch in `actionBuying`/`actionSelling` for barter; new creation GUI |
| `SimpleShopLayoutProvider.java` | Barter price rendering on signs |
| `MainPage.java` (keeper menu) | Barter-aware "Change Price" button |
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
