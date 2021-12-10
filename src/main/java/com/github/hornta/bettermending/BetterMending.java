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
import java.util.logging.Level;
import java.util.stream.Collectors;

public class BetterMending extends JavaPlugin implements Listener {
  private Map<Integer, ExperienceOrb> orbs = new HashMap<>();
  private ExperienceOrb current;
  private Metrics metrics;
  private boolean debug = false;
  private DamagedComparator damagedComparator = new DamagedComparator();

  private Set<UUID> ignoreMendingEvent = new HashSet<>();
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
    if (ignoreMendingEvent.contains(event.getPlayer().getUniqueId())) {
      ignoreMendingEvent.remove(event.getPlayer().getUniqueId());
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

    MendResult mendResult = new MendResult(true, event.getAmount());
    while (mendResult.isContinueMending()) {
      mendResult = mendItem(event.getPlayer(), mendResult.getNewExperience());
    }

    event.setAmount(0);

    if(mendResult.getNewExperience() > 0) {
      PlayerExpChangeEvent expEvent = new PlayerExpChangeEvent(event.getPlayer(), mendResult.getNewExperience());
      skipProcessExpChangeEvent = true;
      Bukkit.getPluginManager().callEvent(expEvent);
      event.getPlayer().giveExp(expEvent.getAmount());
    }
  }

  private MendResult mendItem(Player player, int experience) {
    ItemStack itemToBeMended = getRandomMendableItem(player);
    if(itemToBeMended == null) {
      return new MendResult(false, experience);
    }

    Damageable damageable = (Damageable) itemToBeMended.getItemMeta();
    int repairAmount = Math.min(experience * 2, damageable.getDamage());

    PlayerItemMendEvent mendEvent = new PlayerItemMendEvent(player, itemToBeMended, current, repairAmount);
    ignoreMendingEvent.add(player.getUniqueId());
    Bukkit.getPluginManager().callEvent(mendEvent);
    if (mendEvent.isCancelled()) {
      return new MendResult(false, experience);
    }

    int oldDamage = damageable.getDamage();

    repairAmount = mendEvent.getRepairAmount();
    damageable.setDamage(damageable.getDamage() - repairAmount);
    itemToBeMended.setItemMeta((ItemMeta) damageable);

    if(debug) {
      player.sendMessage(itemToBeMended.getType().name() + ": " + (itemToBeMended.getType().getMaxDurability() - oldDamage) + " / " + itemToBeMended.getType().getMaxDurability() + " -> " + (itemToBeMended.getType().getMaxDurability() - damageable.getDamage()) + " / " + itemToBeMended.getType().getMaxDurability());
    }

    int newExp = experience - repairAmount / 2;
    if (newExp < 0) {
      newExp = 0;
    }

    return new MendResult(newExp != 0, newExp);
  }

  private ItemStack getRandomMendableItem(Player player) {
    try {
      // collect all items that can be mended
      List<ItemStack> itemStacks = Arrays.asList(player.getInventory().getContents());
      
      List<ItemStack> filtered = itemStacks
        .stream()
        .filter(Objects::nonNull)
        .filter((ItemStack itemStack) -> {
          // not sure if this check is neccessary but best to be on the safe side
          if (!(itemStack.getItemMeta() instanceof Damageable)) {
            return false;
          }

          if (!itemStack.containsEnchantment(Enchantment.MENDING)) {
            return false;
          }

          Damageable damageable = (Damageable) itemStack.getItemMeta();
          return damageable.hasDamage();
        })
        .collect(Collectors.toList());

      if (filtered.isEmpty()) {
        return null;
      }

      filtered.sort(damagedComparator);

      if(debug) {
        for(ItemStack is : filtered) {
          player.sendMessage(is.getType().name() + " - " + ((Damageable)is.getItemMeta()).getDamage() / is.getType().getMaxDurability());
        }
      }

      // randomly choose an item to be mended
      return filtered.get(0);
    } catch (Exception e) {
      getLogger().log(Level.SEVERE, e.getMessage(), e);
    }

    return null;
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
