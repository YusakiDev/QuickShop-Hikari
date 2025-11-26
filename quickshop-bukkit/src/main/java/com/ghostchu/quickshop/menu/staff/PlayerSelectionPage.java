package com.ghostchu.quickshop.menu.staff;
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
import com.ghostchu.quickshop.api.shop.permission.BuiltInShopPermissionGroup;
import com.ghostchu.quickshop.menu.config.GuiConfig;
import net.kyori.adventure.text.Component;
import net.tnemc.item.providers.SkullProfile;
import net.tnemc.menu.core.builder.IconBuilder;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.icon.action.IconAction;
import net.tnemc.menu.core.icon.action.impl.DataAction;
import net.tnemc.menu.core.icon.action.impl.RunnableAction;
import net.tnemc.menu.core.icon.action.impl.SwitchPageAction;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ghostchu.quickshop.menu.shared.QuickShopPage.get;
import static com.ghostchu.quickshop.menu.shared.QuickShopPage.getList;
import static com.ghostchu.quickshop.menu.shared.QuickShopPage.getShop;

/**
 * PlayerSelectionMenu
 *
 * @author creatorfromhell
 * @since 6.2.0.8
 */
public class PlayerSelectionPage {

  protected final String returnMenu;
  protected final String menuName;
  protected final int menuPage;
  protected final int returnPage;
  protected final String playerPageID;
  protected final int menuRows;
  protected final String iconLore;
  protected final IconAction[] actions;

  public PlayerSelectionPage(final String returnMenu, final String menuName,
                             final int menuPage, final int returnPage, final String playerPageID,
                             final int menuRows, final String iconLore, final IconAction... actions) {

    this.returnMenu = returnMenu;
    this.menuName = menuName;
    this.menuPage = menuPage;
    this.returnPage = returnPage;
    this.playerPageID = playerPageID;
    this.iconLore = iconLore;
    this.actions = actions;

    //we need a controller row and then at least one row for items.
    this.menuRows = (menuRows <= 1)? 2 : menuRows;
  }

  public void handle(final PageOpenCallback callback) {

    final Optional<MenuViewer> viewer = callback.getPlayer().viewer();
    if(viewer.isPresent()) {

      final Optional<Shop> shop = getShop(viewer.get());
      if(shop.isPresent()) {

        final List<OfflinePlayer> players = sorted(shop.get());

        callback.getPage().getIcons().clear();
        final UUID id = viewer.get().uuid();
        
        // Load GUI configuration for modern styling
        final GuiConfig.MenuConfig menuConfig = QuickShop.getInstance().getGuiConfig().getMenuConfig("staff");
        final GuiConfig.IconConfig borderConfig = menuConfig != null ? menuConfig.getIcon("border") : null;
        final GuiConfig.IconConfig prevPageConfig = menuConfig != null ? menuConfig.getIcon("previous-page") : null;
        final GuiConfig.IconConfig nextPageConfig = menuConfig != null ? menuConfig.getIcon("next-page") : null;
        final GuiConfig.IconConfig backConfig = menuConfig != null ? menuConfig.getIcon("back") : null;
        
        // Set up borders from config (gray for modern look)
        final String borderMaterial = borderConfig != null ? borderConfig.getMaterial() : "GRAY_STAINED_GLASS_PANE";
        final IconBuilder borderBuilder = new IconBuilder(QuickShop.getInstance().stack().of(borderMaterial, 1));
        final List<Integer> borderRows = borderConfig != null ? borderConfig.getRows() : List.of(2, 5);
        for (final int row : borderRows) {
          callback.getPage().setRow(row, borderBuilder);
        }
        
        // Get list start slot from config
        final int listStartSlot = menuConfig != null ? menuConfig.getSection().getInt("list-start-slot", 18) : 18;
        
        final int offset = 9;
        final int page = (Integer)viewer.get().dataOrDefault(playerPageID, 1);
        final int items = (menuRows - 2) * offset; // Adjusted for border rows
        final int start = ((page - 1) * offset);

        final int maxPages = (players.size() / items) + (((players.size() % items) > 0)? 1 : 0);

        final int prev = (page <= 1)? maxPages : page - 1;
        final int next = (page >= maxPages)? 1 : page + 1;

        // Navigation icons from config (ARROW for modern look)
        final String prevMaterial = prevPageConfig != null ? prevPageConfig.getMaterial() : "ARROW";
        final int prevSlot = prevPageConfig != null ? prevPageConfig.getSlot() : 0;
        final String nextMaterial = nextPageConfig != null ? nextPageConfig.getMaterial() : "ARROW";
        final int nextSlot = nextPageConfig != null ? nextPageConfig.getSlot() : 8;

        if(maxPages > 1) {

          callback.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of(prevMaterial, 1)
                                                             .display(get(id, "gui.shared.previous-page")))
                                             .withActions(new DataAction(playerPageID, prev), new SwitchPageAction(menuName, menuPage))
                                             .withSlot(prevSlot)
                                             .build());

          callback.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of(nextMaterial, 1)
                                                             .display(get(id, "gui.shared.next-page")))
                                             .withActions(new DataAction(playerPageID, next), new SwitchPageAction(menuName, menuPage))
                                             .withSlot(nextSlot)
                                             .build());
        }

        // Back button from config (OAK_DOOR for "go back")
        final String backMaterial = backConfig != null ? backConfig.getMaterial() : "OAK_DOOR";
        final int backSlot = backConfig != null ? backConfig.getSlot() : 4;
        callback.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of(backMaterial, 1)
                                                           .display(get(id, "gui.shared.previous-menu")))
                                           .withActions(new SwitchPageAction(returnMenu, returnPage))
                                           .withSlot(backSlot)
                                           .build());

        int i = 0;
        for(final OfflinePlayer player : players) {

          final UUID uuid = player.getUniqueId();
          if(i < start) {

            i++;

            continue;
          }
          if(i >= (start + items)) break;

          SkullProfile profile = null;
          try {

            if(player.hasPlayedBefore()) {
              profile = new SkullProfile();

              profile.setUuid(uuid);
            }

          } catch(final Exception ignore) { }

          final String name = (player.getName() != null)? player.getName() : uuid.toString();
          callback.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("PLAYER_HEAD", 1)
                                                             .display(Component.text(name))
                                                             .lore(getList(id, iconLore))
                                                             .profile(profile))
                                             .withActions(actions)
                                             .withActions(new RunnableAction((click)->{
                                               shop.get().setPlayerGroup(uuid, BuiltInShopPermissionGroup.STAFF);
                                               QuickShop.getInstance().text().of(id, "shop-staff-added", name).send();
                                             }), new SwitchPageAction(returnMenu, returnPage))
                                             .withSlot(listStartSlot + (i - start))
                                             .build());

          i++;
        }
      }
    }
  }

  public List<OfflinePlayer> sorted(final Shop shop) {

    final List<OfflinePlayer> sortedPlayers = new ArrayList<>();

    final List<UUID> staffs = shop.playersCanAuthorize(BuiltInShopPermissionGroup.STAFF);

    for(final OfflinePlayer player : Bukkit.getOfflinePlayers()) {

      final UUID id = player.getUniqueId();
      if(id.equals(shop.getOwner().getUniqueId()) || staffs.contains(id)) {
        continue;
      }
      sortedPlayers.add(player);
    }
    return sortedPlayers;
  }
}