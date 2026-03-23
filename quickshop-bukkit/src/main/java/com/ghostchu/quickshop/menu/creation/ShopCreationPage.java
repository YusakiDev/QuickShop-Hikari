package com.ghostchu.quickshop.menu.creation;
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
import com.ghostchu.quickshop.api.inventory.InventoryWrapperManager;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.economy.QSBenefitProvider;
import com.ghostchu.quickshop.menu.shared.QuickShopPage;
import com.ghostchu.quickshop.obj.QUserImpl;
import com.ghostchu.quickshop.shop.ContainerShop;
import com.ghostchu.quickshop.shop.inventory.BukkitInventoryWrapperManager;
import com.ghostchu.quickshop.util.logger.Log;
import net.tnemc.item.bukkit.BukkitItemStack;
import net.tnemc.menu.core.builder.IconBuilder;
import net.tnemc.menu.core.callbacks.page.PageOpenCallback;
import net.tnemc.menu.core.compatibility.MenuPlayer;
import net.tnemc.menu.core.icon.action.impl.RunnableAction;
import net.tnemc.menu.core.viewer.MenuViewer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.BYPASS;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.CREATION_MAIN;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.PRICE_AMOUNT;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.PRICE_ITEM;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.SELL_AMOUNT;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.SELL_ITEM;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.SHOP_LOCATION;
import static com.ghostchu.quickshop.menu.creation.ShopCreationMenu.SIGN_BLOCK;
import static com.ghostchu.quickshop.shop.SimpleShopManager.SELLING_TYPE;

/**
 * ShopCreationPage - The main page for the shop creation GUI.
 *
 * Layout (3 rows, 27 slots):
 * Row 0: [x] [x] [x] [Sell Label] [x] [x] [x] [Price Label] [x]
 * Row 1: [x] [x] [-]  [SELL]  [+] [x] [-]  [PRICE]      [+]
 * Row 2: [x] [x] [x]  [x] [CONFIRM] [x] [x] [x]         [x]
 *
 * Sell item at slot 12, price item at slot 16, confirm at slot 22.
 *
 * @author creatorfromhell
 * @since 6.2.0.12
 */
public class ShopCreationPage extends QuickShopPage {

  private static final int SLOT_SELL_LABEL = 3;
  private static final int SLOT_PRICE_LABEL = 7;
  private static final int SLOT_SELL_DEC = 11;
  private static final int SLOT_SELL_ITEM = 12;
  private static final int SLOT_SELL_INC = 13;
  private static final int SLOT_PRICE_DEC = 15;
  private static final int SLOT_PRICE_ITEM = 16;
  private static final int SLOT_PRICE_INC = 17;
  private static final int SLOT_CONFIRM = 22;

  public ShopCreationPage() {

    super(CREATION_MAIN);

    setOpen(this::open);
  }

  public void open(final PageOpenCallback open) {

    final UUID id = open.getPlayer().identifier();

    final Optional<MenuViewer> viewerOpt = open.getPlayer().viewer();
    if(viewerOpt.isEmpty()) {
      return;
    }

    final MenuViewer viewer = viewerOpt.get();
    final Player player = Bukkit.getPlayer(id);
    if(player == null) {
      return;
    }

    // Clear existing icons
    open.getPage().getIcons().clear();

    // Fill all slots with gray glass panes
    final IconBuilder borderBuilder = new IconBuilder(QuickShop.getInstance().stack().of("GRAY_STAINED_GLASS_PANE", 1)
                                                              .display(QuickShop.getInstance().platform().miniMessage().deserialize(" ")));
    for(int row = 1; row <= 3; row++) {
      open.getPage().setRow(row, borderBuilder);
    }

    // --- Sell Label ---
    open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("NAME_TAG", 1)
                                                   .display(QuickShop.getInstance().platform().miniMessage().deserialize("<bold><green>Selling Item</green></bold>")))
                                   .withSlot(SLOT_SELL_LABEL).build());

    // --- Price Label ---
    open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("GOLD_NUGGET", 1)
                                                   .display(QuickShop.getInstance().platform().miniMessage().deserialize("<bold><gold>Price Item</gold></bold>")))
                                   .withSlot(SLOT_PRICE_LABEL).build());

    // --- Sell Item Display ---
    final Optional<Object> sellItemObj = viewer.findData(SELL_ITEM);
    final int sellAmount = (Integer)viewer.findData(SELL_AMOUNT).orElse(1);

    if(sellItemObj.isPresent()) {
      final ItemStack sellItem = ((ItemStack)sellItemObj.get()).clone();
      sellItem.setAmount(sellAmount);
      open.getPage().addIcon(new IconBuilder(new BukkitItemStack().of(sellItem)).withSlot(SLOT_SELL_ITEM).build());
    }

    // --- Price Item Display ---
    final Optional<Object> priceItemObj = viewer.findData(PRICE_ITEM);
    final int priceAmount = (Integer)viewer.findData(PRICE_AMOUNT).orElse(1);

    if(priceItemObj.isPresent()) {
      final ItemStack priceItem = ((ItemStack)priceItemObj.get()).clone();
      priceItem.setAmount(priceAmount);
      open.getPage().addIcon(new IconBuilder(new BukkitItemStack().of(priceItem)).withSlot(SLOT_PRICE_ITEM).build());
    } else {
      // Show a placeholder prompting the player to pick up an item and click this slot
      open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("HOPPER", 1)
                                                     .display(QuickShop.getInstance().platform().miniMessage().deserialize("<yellow>Click to set price item</yellow>"))
                                                     .lore(Collections.singletonList(QuickShop.getInstance().platform().miniMessage().deserialize("<gray>Pick up an item from your inventory, then click here</gray>"))))
                                     .withActions(new RunnableAction((click)->{
                                       final Player p = Bukkit.getPlayer(id);
                                       if(p == null) {
                                         return;
                                       }
                                       final ItemStack cursor = p.getItemOnCursor();
                                       if(cursor.getType().isAir()) {
                                         p.sendMessage(guiMessage("creation.hold-item"));
                                         return;
                                       }
                                       // Clone the cursor item as the price item
                                       final ItemStack priceClone = cursor.clone();
                                       priceClone.setAmount(1);
                                       viewer.addData(PRICE_ITEM, priceClone);
                                       viewer.addData(PRICE_AMOUNT, cursor.getAmount());

                                       // Return the cursor item to the player's inventory before reopening
                                       // (reopening the GUI drops cursor items)
                                       p.setItemOnCursor(null);
                                       p.getInventory().addItem(cursor);

                                       // Reopen on next tick so the cursor clear takes effect first
                                       QuickShop.folia().getScheduler().runAtEntityLater(p, ()->{
                                         final MenuPlayer menuPlayer = QuickShop.getInstance().createMenuPlayer(p);
                                         menuPlayer.inventory().openMenu(menuPlayer, "qs:creation", CREATION_MAIN);
                                       }, 1);
                                     }))
                                     .withSlot(SLOT_PRICE_ITEM).build());
    }

    // --- Sell Amount [-] ---
    open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("RED_STAINED_GLASS_PANE", 1)
                                                   .display(QuickShop.getInstance().platform().miniMessage().deserialize("<red>-1</red>")))
                                   .withActions(new RunnableAction((click)->{
                                     final int current = (Integer)viewer.findData(SELL_AMOUNT).orElse(1);
                                     if(current > 1) {
                                       final int newAmount = current - 1;
                                       viewer.addData(SELL_AMOUNT, newAmount);
                                       viewer.findData(SELL_ITEM).ifPresent(obj->{
                                         final ItemStack updated = ((ItemStack)obj).clone();
                                         updated.setAmount(newAmount);
                                         click.player().inventory().updateInventory(SLOT_SELL_ITEM, new BukkitItemStack().of(updated));
                                       });
                                     }
                                   }))
                                   .withSlot(SLOT_SELL_DEC).build());

    // --- Sell Amount [+] ---
    open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("LIME_STAINED_GLASS_PANE", 1)
                                                   .display(QuickShop.getInstance().platform().miniMessage().deserialize("<green>+1</green>")))
                                   .withActions(new RunnableAction((click)->{
                                     final int current = (Integer)viewer.findData(SELL_AMOUNT).orElse(1);
                                     if(current < 64) {
                                       final int newAmount = current + 1;
                                       viewer.addData(SELL_AMOUNT, newAmount);
                                       viewer.findData(SELL_ITEM).ifPresent(obj->{
                                         final ItemStack updated = ((ItemStack)obj).clone();
                                         updated.setAmount(newAmount);
                                         click.player().inventory().updateInventory(SLOT_SELL_ITEM, new BukkitItemStack().of(updated));
                                       });
                                     }
                                   }))
                                   .withSlot(SLOT_SELL_INC).build());

    // --- Price Amount [-] ---
    if(priceItemObj.isPresent()) {
      open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("RED_STAINED_GLASS_PANE", 1)
                                                     .display(QuickShop.getInstance().platform().miniMessage().deserialize("<red>-1</red>")))
                                     .withActions(new RunnableAction((click)->{
                                       final int current = (Integer)viewer.findData(PRICE_AMOUNT).orElse(1);
                                       if(current > 1) {
                                         final int newAmount = current - 1;
                                         viewer.addData(PRICE_AMOUNT, newAmount);
                                         viewer.findData(PRICE_ITEM).ifPresent(obj->{
                                           final ItemStack updated = ((ItemStack)obj).clone();
                                           updated.setAmount(newAmount);
                                           click.player().inventory().updateInventory(SLOT_PRICE_ITEM, new BukkitItemStack().of(updated));
                                         });
                                       }
                                     }))
                                     .withSlot(SLOT_PRICE_DEC).build());

      // --- Price Amount [+] ---
      open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("LIME_STAINED_GLASS_PANE", 1)
                                                     .display(QuickShop.getInstance().platform().miniMessage().deserialize("<green>+1</green>")))
                                     .withActions(new RunnableAction((click)->{
                                       final int current = (Integer)viewer.findData(PRICE_AMOUNT).orElse(1);
                                       if(current < 64) {
                                         final int newAmount = current + 1;
                                         viewer.addData(PRICE_AMOUNT, newAmount);
                                         viewer.findData(PRICE_ITEM).ifPresent(obj->{
                                           final ItemStack updated = ((ItemStack)obj).clone();
                                           updated.setAmount(newAmount);
                                           click.player().inventory().updateInventory(SLOT_PRICE_ITEM, new BukkitItemStack().of(updated));
                                         });
                                       }
                                     }))
                                     .withSlot(SLOT_PRICE_INC).build());
    }

    // --- Confirm Button ---
    if(sellItemObj.isPresent() && priceItemObj.isPresent()) {
      open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("LIME_CONCRETE", 1)
                                                     .display(QuickShop.getInstance().platform().miniMessage().deserialize("<bold><green>Confirm</green></bold>"))
                                                     .lore(Collections.singletonList(QuickShop.getInstance().platform().miniMessage().deserialize("<gray>Click to create the shop</gray>"))))
                                     .withActions(new RunnableAction((click)->{
                                       handleConfirm(viewer, player);
                                     }))
                                     .withSlot(SLOT_CONFIRM).build());
    } else {
      // Gray confirm button (disabled)
      open.getPage().addIcon(new IconBuilder(QuickShop.getInstance().stack().of("GRAY_CONCRETE", 1)
                                                     .display(QuickShop.getInstance().platform().miniMessage().deserialize("<gray>Confirm</gray>"))
                                                     .lore(Collections.singletonList(QuickShop.getInstance().platform().miniMessage().deserialize("<red>Set both items first</red>"))))
                                     .withSlot(SLOT_CONFIRM).build());
    }
  }

  private void handleConfirm(final MenuViewer viewer, final Player player) {

    final Optional<Object> sellItemObj = viewer.findData(SELL_ITEM);
    final Optional<Object> priceItemObj = viewer.findData(PRICE_ITEM);
    final Optional<Object> locationObj = viewer.findData(SHOP_LOCATION);
    final Optional<Object> signBlockObj = viewer.findData(SIGN_BLOCK);

    if(sellItemObj.isEmpty() || priceItemObj.isEmpty() || locationObj.isEmpty()) {
      QuickShop.getInstance().text().of(player, "shop-creation-failed").send();
      return;
    }

    final ItemStack sellItem = ((ItemStack)sellItemObj.get()).clone();
    sellItem.setAmount((Integer)viewer.findData(SELL_AMOUNT).orElse(1));

    final ItemStack priceItem = ((ItemStack)priceItemObj.get()).clone();
    priceItem.setAmount((Integer)viewer.findData(PRICE_AMOUNT).orElse(1));

    final Location location = (Location)locationObj.get();
    final Block signBlock = signBlockObj.isPresent()? (Block)signBlockObj.get() : null;
    final boolean bypass = (Boolean)viewer.findData(BYPASS).orElse(false);

    // Close the menu
    viewer.close(QuickShop.getInstance().createMenuPlayer(player));

    // Create the shop on the region thread
    final QUser createQUser = QUserImpl.createFullFilled(player);
    QuickShop.folia().getScheduler().runAtLocation(location, task->{
      final BlockState state = location.getBlock().getState();
      if(state instanceof final InventoryHolder holder) {

        final String symbolLink;
        final InventoryWrapperManager manager = QuickShop.getInstance().getInventoryWrapperManager();
        if(manager instanceof final BukkitInventoryWrapperManager bukkitInventoryWrapperManager) {
          symbolLink = bukkitInventoryWrapperManager.mklink(location);
        } else {
          symbolLink = manager.mklink(new com.ghostchu.quickshop.shop.inventory.BukkitInventoryWrapper(holder.getInventory()));
        }

        final ContainerShop shop = new ContainerShop(QuickShop.getInstance(), -1, location,
                                                     0.0, sellItem, createQUser, false,
                                                     SELLING_TYPE, new YamlConfiguration(), null,
                                                     !QuickShop.getInstance().getConfig().getBoolean("shop.display-default", true),
                                                     null, QuickShop.getInstance().getJavaPlugin().getName(),
                                                     symbolLink,
                                                     null, Collections.emptyMap(), new QSBenefitProvider(), priceItem);
        try {
          QuickShop.getInstance().getShopManager().createShop(shop, signBlock, bypass);
          Log.debug("Barter shop created via creation GUI at " + location);
        } catch(final IllegalStateException e) {
          Log.debug("Failed to create barter shop: " + e.getMessage());
        }
      } else {
        QuickShop.getInstance().text().of(player, "invalid-container").send();
      }
    });
  }

}
