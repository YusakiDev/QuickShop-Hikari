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
import com.ghostchu.quickshop.menu.shared.QuickShopMenu;

/**
 * ShopCreationMenu - GUI for creating shops with barter support.
 * Players set both the sell item and price item in this 3-row menu.
 *
 * @author creatorfromhell
 * @since 6.2.0.12
 */
public class ShopCreationMenu extends QuickShopMenu {

  public static final int CREATION_MAIN = 1;

  /** Viewer data key for the sell ItemStack (already set from hand item). */
  public static final String SELL_ITEM = "CREATION_SELL_ITEM";

  /** Viewer data key for the sell item amount. */
  public static final String SELL_AMOUNT = "CREATION_SELL_AMOUNT";

  /** Viewer data key for the price ItemStack (set via prompt). */
  public static final String PRICE_ITEM = "CREATION_PRICE_ITEM";

  /** Viewer data key for the price item amount. */
  public static final String PRICE_AMOUNT = "CREATION_PRICE_AMOUNT";

  /** Viewer data key for the shop Location. */
  public static final String SHOP_LOCATION = "CREATION_SHOP_LOCATION";

  /** Viewer data key for the sign Block. */
  public static final String SIGN_BLOCK = "CREATION_SIGN_BLOCK";

  /** Viewer data key for the bypass flag. */
  public static final String BYPASS = "CREATION_BYPASS";

  public ShopCreationMenu() {

    this.rows = 3;
    this.name = "qs:creation";

    setOpen((open)->open.getMenu().setTitle(legacy(open.getPlayer().identifier(), "gui.creation.title")));

    addPage(new ShopCreationPage());
  }
}
