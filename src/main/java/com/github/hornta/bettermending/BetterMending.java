package com.github.hornta.bettermending;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class BetterMending extends JavaPlugin implements Listener {
  private Map<Integer, ExperienceOrb> orbs = new HashMap<>();
  private ExperienceOrb current;
  private Metrics metrics;

  private Set<Player> ignoreMendingEvent = new HashSet<>();
  private ProtocolManager protocolManager;
  private boolean skipProcessExpChangeEvent = false;

  @EventHandler(priority = EventPriority.HIGHEST)
  void onEntityDeathEvent(EntityDeathEvent event) {
    if(event instanceof ExperienceOrb) {
      orbs.remove(event.getEntity().getEntityId());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void onEntityTarget(EntityTargetEvent event) {
    if (event.getEntity() instanceof ExperienceOrb) {
      orbs.put(event.getEntity().getEntityId(), (ExperienceOrb) event.getEntity());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void onPlayerItemMend(PlayerItemMendEvent event) {
    if (ignoreMendingEvent.contains(event.getPlayer())) {
      ignoreMendingEvent.remove(event.getPlayer());
    } else {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  void onPlayerExpChange(PlayerExpChangeEvent event) {
    orbs.entrySet().removeIf((Map.Entry<Integer, ExperienceOrb> entry) -> !entry.getValue().isValid());

    if(current != null) {
      orbs.remove(current.getEntityId());
    }
    if(skipProcessExpChangeEvent) {
      skipProcessExpChangeEvent = false;
      return;
    }
    int experience = event.getAmount();

    ItemStack itemToBeMended = getRandomMendableItem(event.getPlayer());
    if(itemToBeMended == null) {
      return;
    }
    event.setAmount(0);
    Damageable damageable = (Damageable) itemToBeMended.getItemMeta();

    int repairAmount = Math.min(experience * 2, damageable.getDamage());
    PlayerItemMendEvent mendEvent = new PlayerItemMendEvent(event.getPlayer(), itemToBeMended, current, repairAmount);
    ignoreMendingEvent.add(event.getPlayer());
    Bukkit.getPluginManager().callEvent(mendEvent);
    if (!mendEvent.isCancelled()) {
      repairAmount = mendEvent.getRepairAmount();
      //this.value -= this.b(i);
      experience -= repairAmount / 2;
      damageable.setDamage(damageable.getDamage() - repairAmount);
      itemToBeMended.setItemMeta((ItemMeta) damageable);
    }

    if(experience > 0) {
      PlayerExpChangeEvent expEvent = new PlayerExpChangeEvent(event.getPlayer(), experience);
      skipProcessExpChangeEvent = true;
      Bukkit.getPluginManager().callEvent(expEvent);
      event.getPlayer().giveExp(experience);
    }
  }

  private ItemStack getRandomMendableItem(Player player) {
    // collect all items that can be mended
    List<ItemStack> itemStacks = new ArrayList<>();
    itemStacks.add(player.getInventory().getHelmet());
    itemStacks.add(player.getInventory().getChestplate());
    itemStacks.add(player.getInventory().getLeggings());
    itemStacks.add(player.getInventory().getBoots());
    itemStacks.add(player.getInventory().getItemInMainHand());
    itemStacks.add(player.getInventory().getItemInOffHand());
    itemStacks = itemStacks
      .stream()
      .filter(Objects::nonNull)
      .filter((ItemStack itemStack) -> {
        // not sure if this check is neccessary but best to be on the safe side
        if(!(itemStack.getItemMeta() instanceof Damageable)) {
          return false;
        }

        if (!itemStack.containsEnchantment(Enchantment.MENDING)) {
          return false;
        }

        Damageable damageable = (Damageable) itemStack.getItemMeta();
        return damageable.hasDamage();
      })
      .collect(Collectors.toList());

    if(itemStacks.isEmpty()) {
      return null;
    }

    // randomly choose an item to be mended
    return itemStacks.get(new Random().nextInt(itemStacks.size()));
  }

  @Override
  public void onEnable() {
    metrics = new Metrics(this);
    protocolManager = ProtocolLibrary.getProtocolManager();
    protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.HIGHEST, PacketType.Play.Server.COLLECT) {
      @Override
      public void onPacketSending(PacketEvent event) {
        int entityId = event.getPacket().getIntegers().read(0);
        if (orbs.containsKey(entityId)) {
          current = orbs.get(entityId);
        }
      }
    });
    Bukkit.getPluginManager().registerEvents(this, this);
  }
}
