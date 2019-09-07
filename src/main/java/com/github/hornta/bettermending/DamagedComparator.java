package com.github.hornta.bettermending;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Comparator;

public class DamagedComparator implements Comparator<ItemStack> {
  @Override
  public int compare(ItemStack o1, ItemStack o2) {
    Damageable d1 = (Damageable) o1.getItemMeta();
    Damageable d2 = (Damageable) o2.getItemMeta();
    float percentD1 = (float)d1.getDamage() / o1.getType().getMaxDurability();
    float percentD2 = (float)d2.getDamage() / o2.getType().getMaxDurability();
    if(percentD1 > percentD2) {
      return -1;
    } else if(percentD1 < percentD2) {
      return 1;
    }

    return 0;
  }
}
