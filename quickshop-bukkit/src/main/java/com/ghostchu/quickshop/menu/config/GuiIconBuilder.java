package com.ghostchu.quickshop.menu.config;
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
import net.kyori.adventure.text.Component;
import net.tnemc.item.AbstractItemStack;
import net.tnemc.menu.core.builder.IconBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GuiIconBuilder - Helper class to build icons from configuration
 *
 * @author creatorfromhell
 * @since 6.2.0.11
 */
public class GuiIconBuilder {

  /**
   * Creates an AbstractItemStack from IconConfig
   *
   * @param config The icon configuration
   * @param player The player UUID for language resolution
   * @param args   Arguments for placeholder replacement in language strings
   * @return The configured AbstractItemStack
   */
  @NotNull
  public static AbstractItemStack<?> createStack(@NotNull final GuiConfig.IconConfig config,
                                                  @Nullable final UUID player,
                                                  final Object... args) {
    AbstractItemStack<?> stack = QuickShop.getInstance().stack()
            .of(config.getMaterial(), config.getAmount());

    // Set display name if configured
    final String nameKey = config.getName();
    if (nameKey != null && !nameKey.isEmpty()) {
      if (nameKey.startsWith("$")) {
        // Language key reference (e.g., "$gui.trade.title")
        final String langKey = nameKey.substring(1);
        stack = stack.display(getText(player, langKey, args));
      } else if (nameKey.equals(" ")) {
        // Empty name (single space = blank display name)
        stack = stack.display(Component.empty());
      } else {
        // Direct inline MiniMessage text (e.g., "<bold><green>Buy Items</green></bold>")
        stack = stack.display(parseMiniMessage(nameKey));
      }
    }

    // Set lore if configured
    final List<String> loreKeys = config.getLore();
    if (!loreKeys.isEmpty()) {
      final List<Component> lore = new ArrayList<>();
      for (final String loreKey : loreKeys) {
        if (loreKey.startsWith("$")) {
          // Language key reference (e.g., "$gui.trade.custom.lore-buy")
          final String langKey = loreKey.substring(1);
          lore.addAll(getTextList(player, langKey, args));
        } else {
          // Direct inline MiniMessage text (e.g., "<yellow>Click to buy</yellow>")
          lore.add(parseMiniMessage(loreKey));
        }
      }
      stack = stack.lore(lore);
    }

    return stack;
  }

  /**
   * Creates an IconBuilder from IconConfig with slot
   *
   * @param config The icon configuration
   * @param player The player UUID for language resolution
   * @param args   Arguments for placeholder replacement
   * @return The configured IconBuilder
   */
  @NotNull
  public static IconBuilder createIconBuilder(@NotNull final GuiConfig.IconConfig config,
                                               @Nullable final UUID player,
                                               final Object... args) {
    return new IconBuilder(createStack(config, player, args))
            .withSlot(config.getSlot());
  }

  /**
   * Creates an IconBuilder with custom slot
   *
   * @param config The icon configuration
   * @param slot   The slot to use
   * @param player The player UUID for language resolution
   * @param args   Arguments for placeholder replacement
   * @return The configured IconBuilder
   */
  @NotNull
  public static IconBuilder createIconBuilder(@NotNull final GuiConfig.IconConfig config,
                                               final int slot,
                                               @Nullable final UUID player,
                                               final Object... args) {
    return new IconBuilder(createStack(config, player, args))
            .withSlot(slot);
  }

  /**
   * Creates an IconBuilder with custom display name and lore
   *
   * @param config     The icon configuration (for material and custom model data)
   * @param slot       The slot to use
   * @param name       The display name component
   * @param lore       The lore components
   * @return The configured IconBuilder
   */
  @NotNull
  public static IconBuilder createIconBuilder(@NotNull final GuiConfig.IconConfig config,
                                               final int slot,
                                               @NotNull final Component name,
                                               @NotNull final List<Component> lore) {
    AbstractItemStack<?> stack = QuickShop.getInstance().stack()
            .of(config.getMaterial(), config.getAmount())
            .display(name)
            .lore(lore);

    return new IconBuilder(stack).withSlot(slot);
  }

  /**
   * Creates an IconBuilder with custom display name
   *
   * @param config The icon configuration
   * @param slot   The slot to use
   * @param name   The display name component
   * @return The configured IconBuilder
   */
  @NotNull
  public static IconBuilder createIconBuilder(@NotNull final GuiConfig.IconConfig config,
                                               final int slot,
                                               @NotNull final Component name) {
    AbstractItemStack<?> stack = QuickShop.getInstance().stack()
            .of(config.getMaterial(), config.getAmount())
            .display(name);

    return new IconBuilder(stack).withSlot(slot);
  }

  /**
   * Creates a border IconBuilder
   *
   * @param config The border icon configuration
   * @return The configured IconBuilder for borders
   */
  @NotNull
  public static IconBuilder createBorderBuilder(@NotNull final GuiConfig.IconConfig config) {
    AbstractItemStack<?> stack = QuickShop.getInstance().stack()
            .of(config.getMaterial(), 1);

    final String name = config.getName();
    if (name != null) {
      if (name.equals(" ") || name.isEmpty()) {
        stack = stack.display(Component.empty());
      }
    }

    return new IconBuilder(stack);
  }

  /**
   * Applies custom model data to an ItemStack if configured
   *
   * @param item   The ItemStack to modify
   * @param config The icon configuration
   * @return The modified ItemStack
   */
  @NotNull
  public static ItemStack applyCustomModelData(@NotNull final ItemStack item,
                                                @NotNull final GuiConfig.IconConfig config) {
    if (config.hasCustomModelData()) {
      final ItemMeta meta = item.getItemMeta();
      if (meta != null) {
        meta.setCustomModelData(config.getCustomModelData());
        item.setItemMeta(meta);
      }
    }
    return item;
  }

  /**
   * Gets a component from the language system
   */
  @NotNull
  private static Component getText(@Nullable final UUID player, @NotNull final String key, final Object... args) {
    return QuickShop.getInstance().text().of(player, key, args).forLocale();
  }

  /**
   * Gets a list of components from the language system
   */
  @NotNull
  private static List<Component> getTextList(@Nullable final UUID player, @NotNull final String key, final Object... args) {
    return QuickShop.getInstance().text().ofList(player, key, args).forLocale();
  }

  /**
   * Parses a string as MiniMessage format directly.
   * Use this for inline text in gui.yml that doesn't reference language keys.
   *
   * @param text The MiniMessage formatted text (e.g., "<bold><green>Buy Items</green></bold>")
   * @return The parsed Component
   */
  @NotNull
  private static Component parseMiniMessage(@NotNull final String text) {
    return QuickShop.getInstance().platform().miniMessage().deserialize(text);
  }
}
