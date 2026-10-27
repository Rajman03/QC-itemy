package pl.rajman.striderspawner.items.boskidirt;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Built-in Boski Dirt item and its abilities. */
public final class BoskiDirtItem implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey itemKey, itemInstanceKey, furyKey, totemUsesKey;
    private final Map<UUID, MaceProtection> rainbowMaceShield = new HashMap<>();
    private final Map<UUID, Map<Ability, Long>> abilityCooldowns = new HashMap<>();
    private BukkitTask actionBarTask;
    private FileConfiguration config;
    private BoskiDirtBuffAbility buffAbility;
    private BoskiDirtXrayAbility xrayAbility;
    private enum Ability { BUFF, RAINBOW_SHEEP, ROTATE, CAGE, XRAY }
    private record MaceProtection(UUID attackerId, long expiresAt) { }

    public BoskiDirtItem(JavaPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "boski_dirt");
        itemInstanceKey = new NamespacedKey(plugin, "boski_dirt_instance");
        furyKey = new NamespacedKey(plugin, "boski_dirt_fury");
        totemUsesKey = new NamespacedKey(plugin, "boski_dirt_totem_uses");
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "items/boski-dirt/config.yml");
        if (!file.exists()) plugin.saveResource("items/boski-dirt/config.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream stream = plugin.getResource("items/boski-dirt/config.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                config.save(file);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Nie można uzupełnić konfiguracji Boskiego Dirta.", ex);
        }
        buffAbility = new BoskiDirtBuffAbility(plugin, config);
        xrayAbility = new BoskiDirtXrayAbility(plugin, config);
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateActionBars, 2L, 10L);
    }

    public void shutdown() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        if (xrayAbility != null) xrayAbility.shutdown();
        rainbowMaceShield.clear();
        abilityCooldowns.clear();
    }

    public boolean enabled() { return config.getBoolean("enabled", true); }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.DIRT);
        ItemMeta meta = item.getItemMeta();
        BoskiDirtLore.apply(meta);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        // Every instance carries its own fury and totem counter, so separate dirt items must not stack.
        meta.getPersistentDataContainer().set(itemInstanceKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.getPersistentDataContainer().set(furyKey, PersistentDataType.INTEGER, 0);
        meta.getPersistentDataContainer().set(totemUsesKey, PersistentDataType.INTEGER, config.getInt("totem.uses", 5));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        abilityCooldowns.remove(playerId);
        rainbowMaceShield.remove(playerId);
    }

    private boolean startAbilityCooldown(Player player, Ability ability) {
        long now = System.currentTimeMillis();
        Map<Ability, Long> playerCooldowns = abilityCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Ability.class));
        long readyAt = playerCooldowns.getOrDefault(ability, 0L);
        if (readyAt > now) {
            actionBar(player, "&cUmiejętność odnowi się za " + ((readyAt - now + 999) / 1000) + " s.");
            return false;
        }
        playerCooldowns.put(ability, now + 5_000L);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isItem(event.getItemInHand())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(color("&cBoski Dirt nie może być postawiony jako blok."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!enabled()) return;
        Player player = event.getPlayer();
        if (!isItem(heldItem(player))) return;
        if (player.isSneaking()) {
            event.setCancelled(true);
            if (startAbilityCooldown(player, Ability.XRAY)) xrayAbility.activate(player);
        } else {
            event.setCancelled(true);
            if (startAbilityCooldown(player, Ability.BUFF)) buffAbility.activate(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!enabled()) return;
        if (!isAbilityHand(event.getPlayer(), event.getHand())) return;
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            if (event.getRightClicked() instanceof LivingEntity target
                    && startAbilityCooldown(event.getPlayer(), Ability.ROTATE)) {
                rotate(target);
                event.getPlayer().sendMessage(color("&6Boski Dirt zakręcił celem!"));
            }
        } else if (event.getRightClicked() instanceof LivingEntity target) {
            event.setCancelled(true);
            if (!startAbilityCooldown(event.getPlayer(), Ability.RAINBOW_SHEEP)) return;
            rainbowParticles(event.getPlayer(), target);
            if (target instanceof Player) {
                event.getPlayer().sendMessage(color("&dTęczowa Owca oznaczyła cel. Jego cios maczugą nie trafi przez 5 s."));
            } else {
                event.getPlayer().sendMessage(color("&dTęczowa Owca oznaczyła moba!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!enabled()) return;
        if (!isItem(heldItem(event.getPlayer()))) return;
        if (event.getBlock().getType() == Material.BEDROCK) {
            event.setCancelled(true);
            return;
        }
        event.setInstaBreak(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.getEntity() instanceof Player victim
                && player.getInventory().getItemInMainHand().getType() == Material.MACE) {
            MaceProtection protection = rainbowMaceShield.get(victim.getUniqueId());
            if (protection != null) {
                if (protection.expiresAt() > System.currentTimeMillis()
                        && protection.attackerId().equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    rainbowMaceShield.remove(victim.getUniqueId());
                    victim.sendMessage(color("&dTęczowa osłona zablokowała cios maczugą!"));
                    victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
                    return;
                }
                if (protection.expiresAt() <= System.currentTimeMillis()) rainbowMaceShield.remove(victim.getUniqueId());
            }
        }
        if (!isItem(heldItem(player))) return;
        // Shift + left-click applies to living targets, including mobs. The item lore
        // describes a dirt cage, so the effect should not be limited to PvP players.
        if (player.isSneaking() && event.getEntity() instanceof LivingEntity target
                && startAbilityCooldown(player, Ability.CAGE)) cage(target);
        ItemStack item = heldItem(player);
        int max = furyHits();
        int charge = item.getItemMeta().getPersistentDataContainer().getOrDefault(furyKey, PersistentDataType.INTEGER, 0);
        if (charge >= max) {
            double damageHearts = furyDamageHearts();
            event.setDamage(damageHearts * 2.0);
            setFury(item, 0);
            actionBar(player, "&cDIRTOWA FURIA  -" + heartsText(damageHearts) + " serc");
            player.sendMessage(color("&c&lDIRTOWA FURIA &f-" + heartsText(damageHearts) + " serc"));
            player.getWorld().playSound(event.getEntity().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.7f);
        } else {
            setFury(item, charge + 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!enabled()) return;
        ItemStack item = heldItem(event.getPlayer());
        if (!isItem(item) || event.isCancelled()) return;
        if (event.getBlock().getType() == Material.BEDROCK) {
            event.setCancelled(true);
            return;
        }
        Collection<ItemStack> pickaxeDrops = event.getBlock().getDrops(
                new ItemStack(Material.NETHERITE_PICKAXE), event.getPlayer());
        event.setDropItems(false);
        Location dropLocation = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        pickaxeDrops.forEach(drop -> event.getBlock().getWorld().dropItemNaturally(dropLocation, drop));
        double chance = Math.max(0, Math.min(1, config.getDouble("treasure.chance", 0.03)));
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            List<Material> rewards = config.getStringList("treasure.items").stream().map(Material::matchMaterial)
                    .filter(Objects::nonNull).filter(Material::isItem).toList();
            Material reward = rewards.isEmpty() ? Material.DIAMOND : rewards.get(ThreadLocalRandom.current().nextInt(rewards.size()));
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), new ItemStack(reward));
            event.getPlayer().sendMessage(color("&6Boski Dirt znalazł skarb: &e" + reward.name()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Player player) || event.isCancelled()) return;
        ItemStack item = heldItem(player);
        if (!isItem(item)
                || player.getHealth() + player.getAbsorptionAmount() > event.getFinalDamage()) return;
        int remaining = item.getItemMeta().getPersistentDataContainer().getOrDefault(totemUsesKey, PersistentDataType.INTEGER, config.getInt("totem.uses", 5));
        if (remaining <= 0) return;
        event.setCancelled(true);
        player.setHealth(Math.min(player.getMaxHealth(), config.getDouble("totem.health", 1.0)));
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 20));
        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, true, true));
        remaining--;
        if (remaining == 0) replaceHeldItem(player, new ItemStack(Material.AIR));
        else {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(totemUsesKey, PersistentDataType.INTEGER, remaining);
            item.setItemMeta(meta);
            replaceHeldItem(player, item);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1, 1);
        player.sendMessage(color(remaining == 0 ? "&6Totem Boskiego Dirta zużył się i zniknął." : "&6Boski Dirt uratował Cię przed śmiercią. Pozostało użyć: &e" + remaining));
    }

    private void rotate(Entity target) {
        if (!(target instanceof LivingEntity) || !target.isValid()) return;
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!target.isValid() || ticks >= 30) { cancel(); return; }
                Location turn = target.getLocation();
                turn.setYaw(turn.getYaw() + 90f);
                if (target instanceof Player player) player.teleport(turn);
                else target.teleport(turn);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void rainbowParticles(Player caster, LivingEntity target) {
        if (target instanceof Player) {
            UUID casterId = caster.getUniqueId();
            long shieldTicks = Math.max(1L, Math.min(1200L, config.getLong("rainbow.shield-seconds", 5) * 20L));
            MaceProtection protection = new MaceProtection(target.getUniqueId(), System.currentTimeMillis() + shieldTicks * 50L);
            rainbowMaceShield.put(casterId, protection);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> rainbowMaceShield.remove(casterId, protection), shieldTicks);
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks;
            final Color[] colors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE, Color.FUCHSIA};
            @Override public void run() {
                if (!target.isValid() || ticks >= 50) { cancel(); return; }
                Location at = target.getLocation().add(0, 1, 0);
                for (int i = 0; i < colors.length; i++) {
                    Particle.DustOptions dust = new Particle.DustOptions(colors[(i + ticks / 2) % colors.length], 1.1f);
                    target.getWorld().spawnParticle(Particle.DUST, at, 2, 0.45, 0.65, 0.45, 0, dust);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void cage(LivingEntity target) {
        Location feet = target.getLocation();
        World world = target.getWorld();
        int cx = feet.getBlockX(), cy = feet.getBlockY(), cz = feet.getBlockZ();
        Map<Block, BlockData> replaced = new LinkedHashMap<>();
        // 3x3 dirt cage, two blocks tall, with complete floor and roof.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            addCageBlock(world.getBlockAt(cx + dx, cy - 1, cz + dz), replaced);
            for (int dy = 0; dy <= 2; dy++) {
                boolean wall = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                boolean roof = dy == 2;
                if (wall || roof) addCageBlock(world.getBlockAt(cx + dx, cy + dy, cz + dz), replaced);
            }
        }
        if (replaced.isEmpty()) return;
        world.playSound(feet, Sound.BLOCK_GRAVEL_PLACE, 1.0f, 0.8f);
        world.spawnParticle(Particle.BLOCK, feet, 30, 0.8, 0.7, 0.8, Material.DIRT.createBlockData());
        if (target instanceof Player trappedPlayer) {
            trappedPlayer.sendMessage(color("&6Uwięziono Cię w klatce z dirta!"));
        }
        long duration = Math.max(1, config.getLong("cage.duration-seconds", 5));
        Bukkit.getScheduler().runTaskLater(plugin, () -> replaced.forEach((block, oldData) -> {
            if (block.getType() == Material.DIRT) block.setBlockData(oldData, false);
        }), duration * 20L);
    }

    private void addCageBlock(Block block, Map<Block, BlockData> replaced) {
        Material material = block.getType();
        if (material.isAir() || material == Material.SHORT_GRASS || material == Material.TALL_GRASS
                || material == Material.FERN || material == Material.LARGE_FERN || material == Material.SNOW
                || material == Material.WATER) {
            replaced.put(block, block.getBlockData().clone());
            block.setType(Material.DIRT, false);
        }
    }

    private void updateActionBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = heldItem(player);
            if (!isItem(item)) continue;
            int charge = item.getItemMeta().getPersistentDataContainer().getOrDefault(furyKey, PersistentDataType.INTEGER, 0);
            int max = furyHits();
            if (charge >= max) actionBar(player, "&6Dirtowa Furia &r" + furyBar(charge, max) + " &f" + max + "/" + max + " &c-" + heartsText(furyDamageHearts()) + " serc");
            else actionBar(player, "&6Dirtowa Furia &r" + furyBar(charge, max) + " &f" + charge + "/" + max);
        }
    }

    private int furyHits() {
        return Math.max(1, Math.min(30, config.getInt("fury.hits", 30)));
    }

    private double furyDamageHearts() {
        double configured=config.getDouble("fury.damage-hearts",30.0);
        return Double.isFinite(configured)?Math.max(0,Math.min(1024,configured)):30.0;
    }

    private String heartsText(double hearts) {
        return hearts==(long)hearts?Long.toString((long)hearts):Double.toString(hearts);
    }

    private String furyBar(int charge, int max) {
        int firstStage = (max + 2) / 3;
        int secondStage = (2 * max + 2) / 3;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < max; i++) {
            String stageColor = i < firstStage ? "&6" : i < secondStage ? "&e" : "&c";
            // ASCII pipe has a narrower advance in Minecraft's default font than
            // the box-drawing glyph, keeping the 30-step HUD compact.
            bar.append(i < charge ? stageColor : "&8").append("|");
        }
        return bar.toString();
    }

    private void setFury(ItemStack item, int amount) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(furyKey, PersistentDataType.INTEGER, amount);
        item.setItemMeta(meta);
    }

    private ItemStack heldItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return isItem(main) ? main : player.getInventory().getItemInOffHand();
    }

    private boolean isAbilityHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND && isItem(player.getInventory().getItemInMainHand())) return false;
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        return isItem(held);
    }

    private void replaceHeldItem(Player player, ItemStack item) {
        if (isItem(player.getInventory().getItemInMainHand())) player.getInventory().setItemInMainHand(item);
        else player.getInventory().setItemInOffHand(item);
    }

    private void actionBar(Player player, String text) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(color(text)));
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
