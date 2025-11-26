package com.ghostchu.quickshop.menu.browse;
/*
 * QuickShop-Hikari
 * Copyright (C) 2024 Daniel "creatorfromhell" Vidmar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.common.util.CommonUtil;
import com.ghostchu.quickshop.menu.config.GuiConfig;
import net.kyori.adventure.text.Component;
import net.tnemc.item.AbstractItemStack;
import net.tnemc.item.bukkit.BukkitItemStack;
import net.tnemc.menu.core.PlayerInstancePage;
import net.tnemc.menu.core.builder.IconBuilder;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.icon.action.impl.DataAction;
import net.tnemc.menu.core.icon.action.impl.RunnableAction;
import net.tnemc.menu.core.icon.action.impl.SwitchPageAction;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ghostchu.quickshop.menu.ShopBrowseMenu.BROWSE_SORT;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SELECTED_ITEM_SHOPS;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SHOPS_PAGE;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SHOP_LIST_PAGE;
import static com.ghostchu.quickshop.menu.shared.QuickShopPage.getConfigDisplay;

/**
 * ShopListPage - Shows all shops for a specific item type
 * with price comparison and location information
 *
 * @author creatorfromhell
 * @since 6.2.0.8
 */
public class ShopListPage {

  private final String menuName;
  private final int menuRows;

  public ShopListPage(final String menuName, final int menuRows) {
    this.menuName = menuName;
    this.menuRows = Math.max(menuRows, 3);
  }

  public void handle(final PageOpenCallback callback) {
    final Optional<MenuViewer> viewerOpt = callback.getPlayer().viewer();
    if (viewerOpt.isEmpty()) return;

    final MenuViewer viewer = viewerOpt.get();
    if (!(callback.getPage() instanceof final PlayerInstancePage playerPage)) return;

    final Optional<Object> shopsData = viewer.findData(SELECTED_ITEM_SHOPS);
    final UUID id = viewer.uuid();
    final Player player = Bukkit.getPlayer(id);

    if (shopsData.isEmpty() || player == null) return;

    playerPage.getIcons(id).clear();

    // Load GUI configuration
    final GuiConfig.MenuConfig menuConfig = QuickShop.getInstance().getGuiConfig().getMenuConfig("browse");
    final GuiConfig.IconConfig borderConfig = menuConfig != null ? menuConfig.getIcon("border") : null;
    final GuiConfig.IconConfig backConfig = menuConfig != null ? menuConfig.getIcon("back") : null;
    final GuiConfig.IconConfig prevPageConfig = menuConfig != null ? menuConfig.getIcon("previous-page") : null;
    final GuiConfig.IconConfig nextPageConfig = menuConfig != null ? menuConfig.getIcon("next-page") : null;
    final GuiConfig.IconConfig pageInfoConfig = menuConfig != null ? menuConfig.getIcon("page-info") : null;
    
    final int listStartSlot = menuConfig != null ? menuConfig.getSection().getInt("list-start-slot", 9) : 9;

    // Get current state
    final BrowseSortMode sortMode = (BrowseSortMode) viewer.dataOrDefault(BROWSE_SORT, BrowseSortMode.PRICE_ASC);
    final int page = (Integer) viewer.dataOrDefault(SHOP_LIST_PAGE, 1);

    @SuppressWarnings("unchecked")
    final List<Shop> shops = (ArrayList<Shop>) shopsData.get();

    // Sort shops
    final List<Shop> sortedShops = MarketUtils.sortShops(shops, sortMode);

    // Calculate average price for comparison indicators
    final double avgPrice = sortedShops.isEmpty() ? 0 : 
            CommonUtil.avg(sortedShops.stream().map(Shop::getPrice).toList());

    // Calculate pagination (same pattern as MainPage)
    final int offset = 9;
    final int items = (menuRows - 2) * offset;
    final int start = ((page - 1) * offset);
    final int maxPages = (sortedShops.size() / items) + (((sortedShops.size() % items) > 0) ? 1 : 0);
    final int prev = (page <= 1) ? maxPages : page - 1;
    final int next = (page >= maxPages) ? 1 : page + 1;

    // Set up border rows
    final String borderMaterial = borderConfig != null ? borderConfig.getMaterial() : "GRAY_STAINED_GLASS_PANE";
    final IconBuilder borderBuilder = new IconBuilder(QuickShop.getInstance().stack().of(borderMaterial, 1));
    final List<Integer> borderRows = borderConfig != null ? borderConfig.getRows() : List.of(1, 6);
    for (final int row : borderRows) {
      playerPage.setRow(id, row, borderBuilder);
    }

    // === Control Row (Row 1) ===

    // Back button (slot 0)
    final String backMaterial = backConfig != null ? backConfig.getMaterial() : "OAK_DOOR";
    final int backSlot = backConfig != null ? backConfig.getSlot() : 0;

    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(backMaterial, 1)
            .display(getConfigDisplay(backConfig, "<white>Back to Market</white>")))
            .withSlot(backSlot)
            .withActions(
                    new DataAction(SHOPS_PAGE, 1),
                    new SwitchPageAction(menuName, 1) // Go back to grouped view
            )
            .build());

    // Item info (slot 4) - shows what item we're viewing
    if (!sortedShops.isEmpty()) {
      final Shop firstShop = sortedShops.getFirst();
      final AbstractItemStack<ItemStack> infoStack = new BukkitItemStack()
              .of(firstShop.getItem().getType().key().asString(), 1)
              .display(QuickShop.getInstance().platform().miniMessage().deserialize(
                      "<yellow>" + CommonUtil.prettifyText(firstShop.getItem().getType().name()) + "</yellow>"))
              .lore(List.of(
                      QuickShop.getInstance().platform().miniMessage().deserialize("<gray>Viewing <white>" + sortedShops.size() + "</white> shops</gray>"),
                      QuickShop.getInstance().platform().miniMessage().deserialize("<gray>Average price: <gold>" + formatPrice(avgPrice) + "</gold></gray>")
              ));
      
      playerPage.addIcon(id, new IconBuilder(infoStack).withSlot(4).build());
    }

    // Sort toggle (slot 6)
    final BrowseSortMode nextSort = sortMode.next();
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of("HOPPER", 1)
            .display(QuickShop.getInstance().platform().miniMessage().deserialize("<green>Sort</green>"))
            .lore(List.of(
                    QuickShop.getInstance().platform().miniMessage().deserialize("<gray>Current: <white>" + getSortDisplayName(sortMode) + "</white></gray>"),
                    QuickShop.getInstance().platform().miniMessage().deserialize("<yellow>Click: <white>" + getSortDisplayName(nextSort) + "</white></yellow>")
            )))
            .withSlot(6)
            .withActions(
                    new DataAction(BROWSE_SORT, nextSort),
                    new DataAction(SHOP_LIST_PAGE, 1),
                    new SwitchPageAction(menuName, 2)
            )
            .build());

    // Close button (slot 8)
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of("BARRIER", 1)
            .display(QuickShop.getInstance().platform().miniMessage().deserialize("<red>Close</red>")))
            .withSlot(8)
            .withActions(new RunnableAction((click) -> {
              final Player p = Bukkit.getPlayer(click.player().identifier());
              if (p != null) p.closeInventory();
            }))
            .build());

    // === Pagination Row (Bottom) ===
    final String prevMaterial = prevPageConfig != null ? prevPageConfig.getMaterial() : "ARROW";
    final int prevSlot = prevPageConfig != null ? prevPageConfig.getSlot() : 48;
    final String nextMaterial = nextPageConfig != null ? nextPageConfig.getMaterial() : "ARROW";
    final int nextSlot = nextPageConfig != null ? nextPageConfig.getSlot() : 50;
    final String pageInfoMaterial = pageInfoConfig != null ? pageInfoConfig.getMaterial() : "BOOK";
    final int pageInfoSlot = pageInfoConfig != null ? pageInfoConfig.getSlot() : 49;

    if (maxPages > 1) {
      playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(prevMaterial, 1)
              .display(getConfigDisplay(prevPageConfig, "<white><< Previous Page</white>")))
              .withSlot(prevSlot)
              .withActions(new DataAction(SHOP_LIST_PAGE, prev), new SwitchPageAction(menuName, 2))
              .build());

      playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(nextMaterial, 1)
              .display(getConfigDisplay(nextPageConfig, "<white>Next Page >></white>")))
              .withSlot(nextSlot)
              .withActions(new DataAction(SHOP_LIST_PAGE, next), new SwitchPageAction(menuName, 2))
              .build());
    }

    // Page info
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(pageInfoMaterial, 1)
            .display(getConfigDisplay(pageInfoConfig, "<yellow>Page {0}/{1}</yellow>", page, Math.max(1, maxPages))))
            .withSlot(pageInfoSlot)
            .build());

    // === Shop Grid ===
    int i = 0;
    for (final Shop shop : sortedShops) {
      if (i < start) {
        i++;
        continue;
      }
      if (i >= (start + items)) break;

      // Build shop lore with price indicator
      final List<Component> lore = buildShopLore(id, shop, avgPrice);
      
      // Get display name for the item
      final String itemName = CommonUtil.prettifyText(shop.getItem().getType().name());

      final AbstractItemStack<ItemStack> stack = new BukkitItemStack()
              .of(shop.getItem().getType().key().asString(), shop.getShopStackingAmount())
              .display(QuickShop.getInstance().platform().miniMessage().deserialize("<yellow>" + itemName + "</yellow>"))
              .lore(lore);

      playerPage.addIcon(id, new IconBuilder(stack).withSlot(listStartSlot + (i - start)).build());
      
      i++;
    }
  }

  /**
   * Build the lore for an individual shop
   */
  private List<Component> buildShopLore(final UUID playerId, final Shop shop, final double avgPrice) {
    final List<Component> lore = new ArrayList<>();
    final var mm = QuickShop.getInstance().platform().miniMessage();

    // Owner
    lore.add(mm.deserialize("<gray>Owner: <white>" + shop.getOwner().getDisplay() + "</white></gray>"));

    // Shop type
    final String typeColor = shop.isSelling() ? "<green>" : "<orange>";
    final String typeText = shop.isSelling() ? "Selling" : "Buying";
    lore.add(mm.deserialize("<gray>Type: " + typeColor + typeText + "</gray>"));

    // Price with indicator
    final String priceIndicator = getPriceIndicator(shop.getPrice(), avgPrice, shop.isSelling());
    final String priceColor = getPriceColor(priceIndicator);
    lore.add(mm.deserialize("<gray>Price: " + priceColor + formatPrice(shop.getPrice()) + " " + priceIndicator + "</gray>"));

    // Stock/Space
    if (shop.isSelling()) {
      final int stock = shop.isUnlimited() ? -1 : shop.getRemainingStock();
      final String stockText = stock < 0 ? "Unlimited" : String.valueOf(stock);
      lore.add(mm.deserialize("<gray>Stock: <aqua>" + stockText + "</aqua></gray>"));
    } else {
      final int space = shop.isUnlimited() ? -1 : shop.getRemainingSpace();
      final String spaceText = space < 0 ? "Unlimited" : String.valueOf(space);
      lore.add(mm.deserialize("<gray>Space: <aqua>" + spaceText + "</aqua></gray>"));
    }

    // Location
    final String world = shop.getLocation().getWorld() != null ? 
            shop.getLocation().getWorld().getName() : "Unknown";
    final String coords = shop.getLocation().getBlockX() + ", " + 
            shop.getLocation().getBlockY() + ", " + 
            shop.getLocation().getBlockZ();
    lore.add(mm.deserialize("<gray>Location: <white>" + world + "</white></gray>"));
    lore.add(mm.deserialize("<dark_gray>" + coords + "</dark_gray>"));

    return lore;
  }

  /**
   * Get a price indicator based on comparison to average
   */
  private String getPriceIndicator(final double price, final double avgPrice, final boolean isSelling) {
    if (avgPrice == 0) return "";
    
    final double ratio = price / avgPrice;
    
    if (isSelling) {
      // For selling shops: lower is better for buyers
      if (ratio < 0.85) return "▼▼ Great Deal!";
      if (ratio < 0.95) return "▼ Below Avg";
      if (ratio > 1.15) return "▲▲ Expensive";
      if (ratio > 1.05) return "▲ Above Avg";
    } else {
      // For buying shops: higher is better for sellers
      if (ratio > 1.15) return "▲▲ Great Price!";
      if (ratio > 1.05) return "▲ Above Avg";
      if (ratio < 0.85) return "▼▼ Low Offer";
      if (ratio < 0.95) return "▼ Below Avg";
    }
    return "● Average";
  }

  /**
   * Get color based on price indicator
   */
  private String getPriceColor(final String indicator) {
    if (indicator.contains("Great")) return "<green>";
    if (indicator.contains("Below") || indicator.contains("Low")) return "<yellow>";
    if (indicator.contains("Above")) return "<gold>";
    if (indicator.contains("Expensive")) return "<red>";
    return "<white>";
  }

  /**
   * Get display name for sort mode
   */
  private String getSortDisplayName(final BrowseSortMode mode) {
    return switch (mode) {
      case PRICE_ASC -> "Price ↑";
      case PRICE_DESC -> "Price ↓";
      case STOCK -> "Stock";
      case NAME -> "Name";
    };
  }

  /**
   * Format a price value
   */
  private String formatPrice(final double price) {
    return QuickShop.getInstance().getEconomyManager().provider()
            .format(BigDecimal.valueOf(price), null, null);
  }
}
