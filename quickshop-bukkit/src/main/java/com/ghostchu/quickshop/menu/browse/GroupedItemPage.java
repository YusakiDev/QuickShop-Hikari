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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ghostchu.quickshop.menu.ShopBrowseMenu.BROWSE_FILTER;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.BROWSE_SEARCH;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.BROWSE_SORT;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SELECTED_ITEM_SHOPS;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SHOPS_DATA;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SHOPS_PAGE;
import static com.ghostchu.quickshop.menu.ShopBrowseMenu.SHOP_LIST_PAGE;
import static com.ghostchu.quickshop.menu.shared.QuickShopPage.getConfigDisplay;

/**
 * GroupedItemPage - Market overview page showing items grouped by type
 * with price statistics and the ability to drill down into specific items
 *
 * @author creatorfromhell
 * @since 6.2.0.8
 */
public class GroupedItemPage {

  private final String menuName;
  private final int menuRows;

  public GroupedItemPage(final String menuName, final int menuRows) {
    this.menuName = menuName;
    this.menuRows = Math.max(menuRows, 3); // Minimum 3 rows
  }

  public void handle(final PageOpenCallback callback) {
    final Optional<MenuViewer> viewerOpt = callback.getPlayer().viewer();
    if (viewerOpt.isEmpty()) return;
    
    final MenuViewer viewer = viewerOpt.get();
    if (!(callback.getPage() instanceof final PlayerInstancePage playerPage)) return;

    final Optional<Object> shopsData = viewer.findData(SHOPS_DATA);
    final UUID id = viewer.uuid();
    final Player player = Bukkit.getPlayer(id);
    
    if (shopsData.isEmpty() || player == null) return;

    playerPage.getIcons(id).clear();

    // Load GUI configuration
    final GuiConfig.MenuConfig menuConfig = QuickShop.getInstance().getGuiConfig().getMenuConfig("browse");
    final GuiConfig.IconConfig borderConfig = menuConfig != null ? menuConfig.getIcon("border") : null;
    final GuiConfig.IconConfig sortConfig = menuConfig != null ? menuConfig.getIcon("sort") : null;
    final GuiConfig.IconConfig filterConfig = menuConfig != null ? menuConfig.getIcon("filter") : null;
    final GuiConfig.IconConfig prevPageConfig = menuConfig != null ? menuConfig.getIcon("previous-page") : null;
    final GuiConfig.IconConfig nextPageConfig = menuConfig != null ? menuConfig.getIcon("next-page") : null;
    final GuiConfig.IconConfig pageInfoConfig = menuConfig != null ? menuConfig.getIcon("page-info") : null;
    final GuiConfig.IconConfig closeConfig = menuConfig != null ? menuConfig.getIcon("close") : null;
    
    final int listStartSlot = menuConfig != null ? menuConfig.getSection().getInt("list-start-slot", 9) : 9;

    // Get current state
    final BrowseSortMode sortMode = (BrowseSortMode) viewer.dataOrDefault(BROWSE_SORT, BrowseSortMode.PRICE_ASC);
    final BrowseFilterMode filterMode = (BrowseFilterMode) viewer.dataOrDefault(BROWSE_FILTER, BrowseFilterMode.ALL);
    final String searchQuery = (String) viewer.dataOrDefault(BROWSE_SEARCH, "");
    final int page = (Integer) viewer.dataOrDefault(SHOPS_PAGE, 1);

    @SuppressWarnings("unchecked")
    final List<Shop> allShops = (ArrayList<Shop>) shopsData.get();

    // Process shops into groups with current filters/sort/search
    final List<MarketItemGroup> groups = MarketUtils.processGroups(allShops, filterMode, sortMode, searchQuery);

    // Calculate pagination (same pattern as MainPage)
    final int offset = 9;
    final int items = (menuRows - 2) * offset;
    final int start = ((page - 1) * offset);
    final int maxPages = (groups.size() / items) + (((groups.size() % items) > 0) ? 1 : 0);
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
    
    // Sort button (slot 3)
    final String sortMaterial = sortConfig != null ? sortConfig.getMaterial() : "HOPPER";
    final int sortSlot = sortConfig != null ? sortConfig.getSlot() : 3;
    final BrowseSortMode nextSort = sortMode.next();
    
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(sortMaterial, 1)
            .display(QuickShop.getInstance().platform().miniMessage().deserialize("<green>Sort: " + getSortDisplayName(sortMode) + "</green>"))
            .lore(List.of(
                    QuickShop.getInstance().platform().miniMessage().deserialize("<yellow>Click: " + getSortDisplayName(nextSort) + "</yellow>")
            )))
            .withSlot(sortSlot)
            .withActions(
                    new DataAction(BROWSE_SORT, nextSort),
                    new DataAction(SHOPS_PAGE, 1),
                    new SwitchPageAction(menuName, 1)
            )
            .build());

    // Filter button (slot 5)
    final String filterMaterial = filterConfig != null ? filterConfig.getMaterial() : "PAPER";
    final int filterSlot = filterConfig != null ? filterConfig.getSlot() : 5;
    final BrowseFilterMode nextFilter = filterMode.next();
    
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(filterMaterial, 1)
            .display(QuickShop.getInstance().platform().miniMessage().deserialize("<aqua>Filter: " + getFilterDisplayName(filterMode) + "</aqua>"))
            .lore(List.of(
                    QuickShop.getInstance().platform().miniMessage().deserialize("<yellow>Click: " + getFilterDisplayName(nextFilter) + "</yellow>")
            )))
            .withSlot(filterSlot)
            .withActions(
                    new DataAction(BROWSE_FILTER, nextFilter),
                    new DataAction(SHOPS_PAGE, 1),
                    new SwitchPageAction(menuName, 1)
            )
            .build());

    // Close button (slot 8)
    final String closeMaterial = closeConfig != null ? closeConfig.getMaterial() : "BARRIER";
    final int closeSlot = closeConfig != null ? closeConfig.getSlot() : 8;
    
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(closeMaterial, 1)
            .display(getConfigDisplay(closeConfig, "<red>Close</red>")))
            .withSlot(closeSlot)
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
              .withActions(new DataAction(SHOPS_PAGE, prev), new SwitchPageAction(menuName, 1))
              .build());

      playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(nextMaterial, 1)
              .display(getConfigDisplay(nextPageConfig, "<white>Next Page >></white>")))
              .withSlot(nextSlot)
              .withActions(new DataAction(SHOPS_PAGE, next), new SwitchPageAction(menuName, 1))
              .build());
    }

    // Page info
    playerPage.addIcon(id, new IconBuilder(QuickShop.getInstance().stack().of(pageInfoMaterial, 1)
            .display(getConfigDisplay(pageInfoConfig, "<yellow>Page {0}/{1}</yellow>", page, Math.max(1, maxPages))))
            .withSlot(pageInfoSlot)
            .build());

    // === Item Grid ===
    int i = 0;
    for (final MarketItemGroup group : groups) {
      if (i < start) {
        i++;
        continue;
      }
      if (i >= (start + items)) break;
      
      // Build item lore with market statistics
      final List<Component> lore = buildGroupLore(id, group);
      
      // Get display name for the item
      final String itemName = CommonUtil.prettifyText(group.getRepresentativeItem().getType().name());
      
      final AbstractItemStack<ItemStack> stack = new BukkitItemStack()
              .of(group.getRepresentativeItem().getType().key().asString(), 1)
              .display(QuickShop.getInstance().platform().miniMessage().deserialize("<yellow>" + itemName + "</yellow>"))
              .lore(lore);

      // Create a final reference for the lambda
      final List<Shop> groupShops = group.getShops();
      
      playerPage.addIcon(id, new IconBuilder(stack)
              .withSlot(listStartSlot + (i - start))
              .withActions(
                      new RunnableAction((click) -> {
                        final Optional<MenuViewer> v = click.player().viewer();
                        if (v.isPresent()) {
                          v.get().addData(SELECTED_ITEM_SHOPS, new ArrayList<>(groupShops));
                          v.get().addData(SHOP_LIST_PAGE, 1);
                        }
                      }),
                      new SwitchPageAction(menuName, 2)
              )
              .build());
      
      i++;
    }
  }
  
  private String getSortDisplayName(final BrowseSortMode mode) {
    return switch (mode) {
      case PRICE_ASC -> "Price ↑";
      case PRICE_DESC -> "Price ↓";
      case STOCK -> "Stock";
      case NAME -> "Name";
    };
  }
  
  private String getFilterDisplayName(final BrowseFilterMode mode) {
    return switch (mode) {
      case ALL -> "All";
      case BUYING -> "Buying";
      case SELLING -> "Selling";
      case IN_STOCK -> "In Stock";
    };
  }

  /**
   * Build the lore for a grouped item showing market statistics
   */
  private List<Component> buildGroupLore(final UUID playerId, final MarketItemGroup group) {
    final List<Component> lore = new ArrayList<>();
    final var mm = QuickShop.getInstance().platform().miniMessage();
    
    // Total shops count
    lore.add(mm.deserialize("<gray>Shops: <white>" + group.getTotalShopCount() + "</white></gray>"));
    lore.add(Component.empty());
    
    // Selling shops statistics
    if (group.hasSellingShops()) {
      lore.add(mm.deserialize("<green>▼ Selling (" + group.getSellingShopCount() + " shops)</green>"));
      lore.add(mm.deserialize("<gray>  Price: <white>" + formatPrice(group.getSellingMinPrice()) + 
              " - " + formatPrice(group.getSellingMaxPrice()) + "</white></gray>"));
      lore.add(mm.deserialize("<gray>  Average: <yellow>" + formatPrice(group.getSellingAvgPrice()) + "</yellow></gray>"));
      lore.add(mm.deserialize("<gray>  Median: <gold>" + formatPrice(group.getSellingMedianPrice()) + "</gold></gray>"));
      lore.add(mm.deserialize("<gray>  Stock: <aqua>" + group.getSellingTotalStock() + "</aqua></gray>"));
    }
    
    // Buying shops statistics
    if (group.hasBuyingShops()) {
      if (group.hasSellingShops()) lore.add(Component.empty());
      lore.add(mm.deserialize("<orange>▲ Buying (" + group.getBuyingShopCount() + " shops)</orange>"));
      lore.add(mm.deserialize("<gray>  Price: <white>" + formatPrice(group.getBuyingMinPrice()) + 
              " - " + formatPrice(group.getBuyingMaxPrice()) + "</white></gray>"));
      lore.add(mm.deserialize("<gray>  Average: <yellow>" + formatPrice(group.getBuyingAvgPrice()) + "</yellow></gray>"));
      lore.add(mm.deserialize("<gray>  Median: <gold>" + formatPrice(group.getBuyingMedianPrice()) + "</gold></gray>"));
    }
    
    lore.add(Component.empty());
    lore.add(mm.deserialize("<yellow>Click to view all shops</yellow>"));
    
    return lore;
  }

  /**
   * Format a price value
   */
  private String formatPrice(final double price) {
    return QuickShop.getInstance().getEconomyManager().provider()
            .format(BigDecimal.valueOf(price), null, null);
  }
}
