package pl.rajman.striderspawner;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.OfflinePlayer;
import net.milkbowl.vault.economy.EconomyResponse;
import pl.rajman.striderspawner.items.custom.CustomItemStore;
import pl.rajman.striderspawner.items.boskidirt.BoskiDirtItem;
import pl.rajman.striderspawner.items.n1mc.HeartSwordItem;
import pl.rajman.striderspawner.items.kilofy.MiningPickaxeItem;
import pl.rajman.striderspawner.items.spawners.SpawnerItemModule;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class StriderSpawnerPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private Economy economy;
    private NamespacedKey itemKey, ownerKey, typeKey, levelKey, pendingKey, remainderKey, tickKey;
    private SpawnerItemModule spawnerItems;
    private HeartSwordItem heartSword;
    private MiningPickaxeItem miningPickaxe;
    private CustomItemStore customItems;
    private BoskiDirtItem boskiDirt;
    private final Map<UUID, Location> openMenus = new HashMap<>();
    private final Map<UUID, Integer> openCatalogs = new HashMap<>();
    private final Set<SpawnerLocation> areaMiningBlocks = new HashSet<>();
    private BukkitTask menuRefreshTask;
    private static final DecimalFormat WHOLE_MONEY = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat THOUSANDS_MONEY = new DecimalFormat("#,##0K", DecimalFormatSymbols.getInstance(Locale.US));
    private record SpawnerLocation(UUID worldId,int x,int y,int z) { }

    @Override public void onEnable() {
        migrateLegacyConfig();
        saveDefaultConfig();
        customItems=new CustomItemStore(this); customItems.load();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) { getLogger().severe("Brak ekonomii Vault. Zainstaluj Vault oraz plugin ekonomii (np. EssentialsX). Wyłączam plugin."); getServer().getPluginManager().disablePlugin(this); return; }
        economy = rsp.getProvider();
        spawnerItems=new SpawnerItemModule(this); spawnerItems.load();
        itemKey=spawnerItems.itemKey(); ownerKey=spawnerItems.ownerKey(); typeKey=spawnerItems.typeKey();
        levelKey=spawnerItems.levelKey(); pendingKey=spawnerItems.pendingKey(); remainderKey=spawnerItems.remainderKey(); tickKey=spawnerItems.tickKey();
        heartSword=new HeartSwordItem(this); heartSword.load();
        miningPickaxe=new MiningPickaxeItem(this); miningPickaxe.load();
        boskiDirt=new BoskiDirtItem(this); boskiDirt.load();
        getServer().getPluginManager().registerEvents(boskiDirt, this);
        registerCombineRecipe();
        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand command=Objects.requireNonNull(getCommand("striderspawner"));
        command.setExecutor(this);
        command.setTabCompleter(this);
        PluginCommand itemyCommand=Objects.requireNonNull(getCommand("itemy"));
        itemyCommand.setExecutor(this);
        itemyCommand.setTabCompleter(this);
        PluginCommand itemCommand=Objects.requireNonNull(getCommand("item"));
        itemCommand.setExecutor(this);
        itemCommand.setTabCompleter(this);
    }

    @Override public void onDisable() {
        Bukkit.removeRecipe(new NamespacedKey(this,"combine_spawners"));
        Bukkit.removeRecipe(new NamespacedKey(this,"combine_spawners_two"));
        if(boskiDirt!=null)boskiDirt.shutdown();
        openMenus.clear();
        openCatalogs.clear();
        areaMiningBlocks.clear();
        if(menuRefreshTask!=null){menuRefreshTask.cancel();menuRefreshTask=null;}
    }

    private void migrateLegacyConfig() {
        File newConfig=new File(getDataFolder(),"config.yml");
        File legacyConfig=new File(new File(getDataFolder().getParentFile(),"StriderSpawner"),"config.yml");
        if (newConfig.exists() || !legacyConfig.isFile()) return;
        try {
            getDataFolder().mkdirs();
            Files.copy(legacyConfig.toPath(),newConfig.toPath(),StandardCopyOption.COPY_ATTRIBUTES);
            getLogger().info("Przeniesiono config.yml ze starego folderu StriderSpawner.");
        } catch (java.io.IOException ex) {
            getLogger().severe("Nie udało się przenieść starego config.yml: "+ex.getMessage());
        }
    }

    private ItemStack spawnerItem(EntityType type,int level,UUID owner){return spawnerItems.create(type,level,owner);}
    private ItemStack spawnerItem(EntityType type,int level,UUID owner,double pending){return spawnerItems.create(type,level,owner,pending);}
    private ItemStack menuSpawnerItem(EntityType type,int level,UUID owner,double pending){return spawnerItems.menuItem(type,level,owner,pending);}
    private boolean isSpawnerItem(ItemStack item){return spawnerItems.isItem(item);}
    private UUID itemOwner(ItemStack item,UUID fallback){return spawnerItems.owner(item,fallback);}
    private EntityType itemType(ItemStack item){return spawnerItems.type(item);}
    private int itemLevel(ItemStack item){return spawnerItems.level(item);}
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private boolean configured(EntityType type) { return spawnerItems.configured(type); }
    private int maxLevel(EntityType type) { return spawnerItems.maxLevel(type); }
    private double income(EntityType type, int level) { return spawnerItems.income(type,level); }
    org.bukkit.configuration.file.FileConfiguration spawnerConfig() { return spawnerItems.config(); }
    org.bukkit.configuration.file.FileConfiguration heartSwordConfig() { return heartSword.config(); }
    private String wholeMoney(double d) { return WHOLE_MONEY.format(Math.max(0,d)); }
    private String compactMoney(double d) { double amount=Math.floor(Math.max(0,d)); return amount>=1000?THOUSANDS_MONEY.format(Math.floor(amount/1000)):wholeMoney(amount); }
    private String wholeDollars(double d) { return WHOLE_MONEY.format(Math.floor(Math.max(0,d))); }
    private UUID storedOwner(String value,UUID fallback){try{return value==null?fallback:UUID.fromString(value);}catch(IllegalArgumentException ex){return fallback;}}
    private EntityType storedType(String value){try{return value==null?EntityType.STRIDER:EntityType.valueOf(value);}catch(IllegalArgumentException ex){return EntityType.STRIDER;}}
    private void registerCombineRecipe() {
        NamespacedKey twoKey=new NamespacedKey(this,"combine_spawners_two");
        NamespacedKey threeKey=new NamespacedKey(this,"combine_spawners");
        Bukkit.removeRecipe(twoKey);
        Bukkit.removeRecipe(threeKey);
        ShapelessRecipe two=new ShapelessRecipe(twoKey,new ItemStack(Material.SPAWNER));
        two.addIngredient(Material.SPAWNER);
        two.addIngredient(Material.SPAWNER);
        Bukkit.addRecipe(two);
        ShapelessRecipe three=new ShapelessRecipe(threeKey,new ItemStack(Material.SPAWNER));
        three.addIngredient(Material.SPAWNER);
        three.addIngredient(Material.SPAWNER);
        three.addIngredient(Material.SPAWNER);
        Bukkit.addRecipe(three);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("itemy")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(color("&cTę komendę może wykonać tylko gracz.")); return true; }
            if(!player.hasPermission("striderspawner.admin")){player.sendMessage(color("&cNie masz uprawnień do katalogu itemów."));return true;}
            openCatalog(player,0); return true;
        }
        if (command.getName().equalsIgnoreCase("item")) return handleItemCommand(sender,args);
        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("striderspawner.admin")) {
            Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { sender.sendMessage(color("&cGracz musi być online.")); return true; }
            EntityType type=EntityType.STRIDER; int level=1;
            if (args.length>2) try { type=EntityType.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { sender.sendMessage(color("&cNieznany typ moba. Użyj podpowiedzi komendy /ss give.")); return true; }
            if (!configured(type)) { sender.sendMessage(color("&cTen typ nie jest włączony w items/spawners/config.yml.")); return true; }
            if (args.length>3) try { level=Integer.parseInt(args[3]); } catch(NumberFormatException ignored) { sender.sendMessage(color("&cPoziom musi być liczbą całkowitą.")); return true; }
            if (level<1 || level>maxLevel(type)) { sender.sendMessage(color("&cPoziom musi być z zakresu 1–"+maxLevel(type)+".")); return true; }
            Map<Integer,ItemStack> leftovers=target.getInventory().addItem(spawnerItem(type, level, target.getUniqueId()));
            leftovers.values().forEach(leftover->target.getWorld().dropItemNaturally(target.getLocation(),leftover));
            sender.sendMessage(color("&aNadano spawner graczowi " + target.getName())); return true;
        }
        sender.sendMessage(color("&e/ss give <gracz> [typ] [poziom]"));
        sender.sendMessage(color("&e/item <id> set &7- dodaj lub zmień item w /itemy"));
        sender.sendMessage(color("&e/item heart set &7- ustaw item dropiący jako serce z Kilofa N1"));
        return true;
    }

    private boolean handleItemCommand(CommandSender sender,String[] args) {
        if(!sender.hasPermission("striderspawner.admin")){
            sender.sendMessage(color("&cNie masz uprawnień do zarządzania itemami."));return true;
        }
        if(args.length==3&&args[0].equalsIgnoreCase("boski-dirt")&&args[1].equalsIgnoreCase("give")){
            if(!boskiDirt.enabled()){sender.sendMessage(color("&cBoski Dirt jest wyłączony w konfiguracji."));return true;}
            Player target=Bukkit.getPlayerExact(args[2]);
            if(target==null){sender.sendMessage(color("&cGracz musi być online."));return true;}
            Map<Integer,ItemStack> leftovers=target.getInventory().addItem(boskiDirt.create());
            leftovers.values().forEach(leftover->target.getWorld().dropItemNaturally(target.getLocation(),leftover));
            sender.sendMessage(color("&aNadano Boski Dirt graczowi &e"+target.getName()+"&a."));return true;
        }
        if(args.length!=2){
            sender.sendMessage(color("&e/item <id> set|remove"));
            sender.sendMessage(color("&e/item boski-dirt give <gracz>"));
            sender.sendMessage(color("&e/item heart set &7- ustaw item dropiący jako serce z Kilofa N1"));
            return true;
        }
        String id=args[0].toLowerCase(Locale.ROOT),action=args[1].toLowerCase(Locale.ROOT);
        if(!(sender instanceof Player player)){
            sender.sendMessage(color("&cTej komendy musi użyć gracz, trzymając item."));return true;
        }
        if(id.equals("heart")){
            if(!action.equals("set")){sender.sendMessage(color("&cPoprawna komenda: /item heart set"));return true;}
            ItemStack held=player.getInventory().getItemInMainHand();
            if(held.getType().isAir()||held.getAmount()!=1){sender.sendMessage(color("&cTrzymaj w głównej ręce dokładnie 1 sztukę itemu."));return true;}
            if(!miningPickaxe.setHeartDropItem(held)){sender.sendMessage(color("&cNie udało się ustawić itemu dropiącego jako serce."));return true;}
            sender.sendMessage(color("&aUstawiono item dropiący jako serce z Kilofa N1."));return true;
        }
        if(!id.matches("[a-z0-9_-]{1,32}")||id.startsWith("default_")){
            sender.sendMessage(color("&cID może zawierać 1–32 znaki: a-z, 0-9, _ lub -. Prefiks default_ jest zarezerwowany."));return true;
        }
        if(action.equals("set")){
            ItemStack held=player.getInventory().getItemInMainHand();
            if(held.getType().isAir()||held.getAmount()!=1){sender.sendMessage(color("&cTrzymaj w głównej ręce dokładnie 1 sztukę itemu."));return true;}
            if(!customItems.set(id,held)){sender.sendMessage(color("&cNie udało się zapisać itemu &e"+id+"&c."));return true;}
            sender.sendMessage(color("&aDodano lub zaktualizowano &e"+id+"&a w katalogu /itemy."));return true;
        }
        if(action.equals("remove")){
            if(!customItems.remove(id)){sender.sendMessage(color("&cNie ma własnego itemu o ID &e"+id+"&c."));return true;}
            sender.sendMessage(color("&aUsunięto item &e"+id+"&a z /itemy."));return true;
        }
        sender.sendMessage(color("&e/item <id> set|remove"));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("itemy")) return List.of();
        if (command.getName().equalsIgnoreCase("item")) {
            if (!sender.hasPermission("striderspawner.admin")) return List.of();
            if (args.length==1) {
                List<String> ids=new ArrayList<>(customItems.ids());ids.addAll(List.of("heart","serce","boski-dirt"));
                return matching(ids,args[0]);
            }
            if (args.length==2) {
                if(args[0].equalsIgnoreCase("heart"))return matching(List.of("set"),args[1]);
                if(args[0].equalsIgnoreCase("boski-dirt"))return matching(List.of("give"),args[1]);
                return matching(List.of("set","remove"),args[1]);
            }
            if(args.length==3&&args[0].equalsIgnoreCase("boski-dirt")&&args[1].equalsIgnoreCase("give"))
                return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[2]);
            return List.of();
        }
        if (!sender.hasPermission("striderspawner.admin")) return List.of();
        if (args.length==1) return matching(List.of("give","item"),args[0]);
        if (args[0].equalsIgnoreCase("item")) {
            if (args.length==2) return matching(List.of("add","remove"),args[1]);
            if (args.length==3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("usun") || args[1].equalsIgnoreCase("usuń"))) {
                return matching(customItems.ids(),args[2]);
            }
            return List.of();
        }
        if (!args[0].equalsIgnoreCase("give")) return List.of();
        if (args.length==2) {
            return matching(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[1]);
        }
        if (args.length==3) {
            List<String> types=spawnerItems.types().stream()
                    .filter(spawnerItems::enabled)
                    .map(name->name.toUpperCase(Locale.ROOT)).sorted().toList();
            return matching(types,args[2]);
        }
        if (args.length==4) {
            try {
                EntityType type=EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
                if (!configured(type)) return List.of();
                List<String> levels=new ArrayList<>();
                for (int level=1;level<=maxLevel(type);level++) levels.add(Integer.toString(level));
                return matching(levels,args[3]);
            } catch (IllegalArgumentException ignored) { return List.of(); }
        }
        return List.of();
    }

    private List<String> matching(Collection<String> options,String input) {
        String prefix=input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option->option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    @EventHandler(ignoreCancelled=true) public void place(BlockPlaceEvent e) {
        if (!isSpawnerItem(e.getItemInHand())) return;
        if (!(e.getBlockPlaced().getState() instanceof CreatureSpawner sp)) return;
        ItemStack item=e.getItemInHand(); var pdc=sp.getPersistentDataContainer();
        UUID owner=itemOwner(item,e.getPlayer().getUniqueId());
        pdc.set(itemKey,PersistentDataType.BYTE,(byte)1); pdc.set(ownerKey,PersistentDataType.STRING,owner.toString());
        pdc.set(typeKey,PersistentDataType.STRING,itemType(item).name()); pdc.set(levelKey,PersistentDataType.INTEGER,itemLevel(item));
        double savedPending=item.getItemMeta().getPersistentDataContainer().getOrDefault(pendingKey,PersistentDataType.DOUBLE,0d);
        double savedRemainder=item.getItemMeta().getPersistentDataContainer().getOrDefault(remainderKey,PersistentDataType.DOUBLE,0d);
        if(!Double.isFinite(savedPending)||savedPending<0)savedPending=0;
        if(!Double.isFinite(savedRemainder)||savedRemainder<0)savedRemainder=0;
        double savedTotal=savedPending+savedRemainder;
        if(!Double.isFinite(savedTotal)||savedTotal<0)savedTotal=0;
        double wholeSaved=Math.floor(savedTotal);
        pdc.set(pendingKey,PersistentDataType.DOUBLE,wholeSaved);
        pdc.set(remainderKey,PersistentDataType.DOUBLE,savedTotal-wholeSaved);
        pdc.set(tickKey,PersistentDataType.LONG,System.currentTimeMillis());
        sp.setSpawnedType(itemType(item));
        sp.setMaxNearbyEntities(0);
        sp.setRequiredPlayerRange(0);
        sp.update();
    }

    private SpawnerLocation spawnerLocation(Location location){return new SpawnerLocation(location.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ());}
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEvent e) {
        if (e.getAction()!=Action.RIGHT_CLICK_BLOCK || e.getClickedBlock()==null) return;
        if (e.getClickedBlock().getState() instanceof CreatureSpawner sp && sp.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            if(e.getHand()!=EquipmentSlot.HAND)return;
            String ownerText=sp.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING);
            if (ownerText!=null && !ownerText.equals(e.getPlayer().getUniqueId().toString()) && !e.getPlayer().hasPermission("striderspawner.admin")) {
                e.getPlayer().sendMessage(color("&cTen spawner należy do innego gracza.")); return;
            }
            accrue(sp); openMenus.put(e.getPlayer().getUniqueId(), e.getClickedBlock().getLocation()); showMenu(e.getPlayer(),sp); startMenuRefresh();
        }
    }
    void accrue(CreatureSpawner sp) {
        var p=sp.getPersistentDataContainer(); long now=System.currentTimeMillis(); long last=p.getOrDefault(tickKey,PersistentDataType.LONG,now);
        double pending=p.getOrDefault(pendingKey,PersistentDataType.DOUBLE,0d);
        double remainder=p.getOrDefault(remainderKey,PersistentDataType.DOUBLE,0d);
        if(!Double.isFinite(pending)||pending<0)pending=0;
        if(!Double.isFinite(remainder)||remainder<0)remainder=0;
        double balance=pending+remainder;
        if(!Double.isFinite(balance)||balance<0)balance=0;
        EntityType type=storedType(p.get(typeKey,PersistentDataType.STRING));
        int lvl=Math.max(1,Math.min(maxLevel(type),p.getOrDefault(levelKey,PersistentDataType.INTEGER,1)));
        p.set(typeKey,PersistentDataType.STRING,type.name());
        p.set(levelKey,PersistentDataType.INTEGER,lvl);
        long elapsed=Math.max(0,now-last);
        long elapsedSeconds=elapsed/1000L;
        double generated=elapsedSeconds*income(type,lvl);
        if(Double.isFinite(generated)&&Double.isFinite(balance+generated))balance+=generated;
        double wholeBalance=Math.floor(balance);
        p.set(pendingKey,PersistentDataType.DOUBLE,wholeBalance);
        p.set(remainderKey,PersistentDataType.DOUBLE,balance-wholeBalance);
        p.set(tickKey,PersistentDataType.LONG,last+elapsedSeconds*1000L);
        // Zero nearby entities prevents vanilla spawner mechanics while preserving its block appearance.
        sp.setMaxNearbyEntities(0);
        sp.setRequiredPlayerRange(0);
        sp.update();
    }
    private void startMenuRefresh() {
        if(menuRefreshTask==null||menuRefreshTask.isCancelled())
            menuRefreshTask=getServer().getScheduler().runTaskTimer(this,this::refreshOpenMenus,20L,20L);
    }
    void refreshOpenMenus() {
        Iterator<Map.Entry<UUID,Location>> iterator=openMenus.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            Player player=Bukkit.getPlayer(entry.getKey()); Location location=entry.getValue();
            if (player==null || !player.isOnline() || !ChatColor.stripColor(player.getOpenInventory().getTitle()).trim().equalsIgnoreCase("SPAWNER")) {
                iterator.remove(); continue;
            }
            if (!location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4)) continue;
            if (!(location.getBlock().getState() instanceof CreatureSpawner sp) || !sp.getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE)) {
                player.closeInventory(); iterator.remove(); continue;
            }
            var data=sp.getPersistentDataContainer();
            int level=data.getOrDefault(levelKey,PersistentDataType.INTEGER,1);
            EntityType type=storedType(data.get(typeKey,PersistentDataType.STRING));
            setBalanceItems(player.getOpenInventory().getTopInventory(),projectedBalance(data,type,level),type,level);
        }
        if(openMenus.isEmpty()&&menuRefreshTask!=null){menuRefreshTask.cancel();menuRefreshTask=null;}
    }
    private void showMenu(Player p, CreatureSpawner sp) {
        var data=sp.getPersistentDataContainer(); int lvl=data.getOrDefault(levelKey,PersistentDataType.INTEGER,1);
        EntityType type=storedType(data.get(typeKey,PersistentDataType.STRING));
        double pending=storedBalance(data);
        Inventory inv=Bukkit.createInventory(null,InventoryType.HOPPER,color("&8                 SPAWNER"));
        inv.setItem(0,menuSpawnerItem(type,lvl,storedOwner(data.get(ownerKey,PersistentDataType.STRING),p.getUniqueId()),pending));
        setBalanceItems(inv,pending,type,lvl);
        p.openInventory(inv);
    }
    private record CatalogEntry(ItemStack stack,boolean purchase) { }
    private List<CatalogEntry> catalogEntries(Player viewer) {
        List<CatalogEntry> entries=new ArrayList<>();
        for (String name:spawnerItems.types()) {
            if (!spawnerItems.enabled(name)) continue;
            try {
                EntityType type=EntityType.valueOf(name.toUpperCase(Locale.ROOT));
                entries.add(new CatalogEntry(spawnerItem(type,1,null),false));
            } catch (IllegalArgumentException ignored) { }
        }
        if (heartSword.enabled()) entries.add(new CatalogEntry(heartSword.shopItem(viewer),true));
        if (boskiDirt.enabled()) entries.add(new CatalogEntry(boskiDirt.create(),false));
        if (miningPickaxe.enabled()) {
            for(int level:miningPickaxe.levels()){
                ItemStack pickaxe=miningPickaxe.create(level);
                if(pickaxe!=null)entries.add(new CatalogEntry(pickaxe,false));
            }
        }
        for(ItemStack stack:customItems.all().values())entries.add(new CatalogEntry(stack,false));
        return entries;
    }
    private void openCatalog(Player player,int requestedPage) {
        List<CatalogEntry> entries=catalogEntries(player);
        boolean paginated=entries.size()>54;
        int pageSize=paginated?52:54;
        int page=Math.max(0,Math.min(requestedPage,Math.max(0,(entries.size()-1)/pageSize)));
        Inventory inventory=Bukkit.createInventory(null,54,color("&b&lQC &e&lITEMY"));
        int start=page*pageSize;
        for (int index=0;index<pageSize && start+index<entries.size();index++) {
            int slot=paginated && index>=45 ? index+1 : index;
            inventory.setItem(slot,entries.get(start+index).stack().clone());
        }
        if (paginated) {
            if (page>0) inventory.setItem(45,namedItem(Material.RED_DYE,"&c&lWSTECZ"));
            if (start+pageSize<entries.size()) inventory.setItem(53,namedItem(Material.GREEN_DYE,"&a&lDALEJ — NASTĘPNA STRONA"));
        }
        openCatalogs.put(player.getUniqueId(),page);
        player.openInventory(inventory);
    }
    private ItemStack namedItem(Material material,String displayName) {
        ItemStack item=new ItemStack(material); ItemMeta meta=item.getItemMeta(); meta.setDisplayName(color(displayName)); item.setItemMeta(meta); return item;
    }
    private void clickCatalog(InventoryClickEvent event,Player player) {
        event.setCancelled(true);
        if (event.getClickedInventory()==null || event.getClickedInventory()!=event.getView().getTopInventory()) return;
        int slot=event.getRawSlot(); int page=openCatalogs.getOrDefault(player.getUniqueId(),0);
        List<CatalogEntry> entries=catalogEntries(player);
        boolean paginated=entries.size()>54;
        int pageSize=paginated?52:54;
        if (paginated && slot==45) { if(page>0) openCatalog(player,page-1); return; }
        if (paginated && slot==53) { if((page+1)*pageSize<entries.size()) openCatalog(player,page+1); return; }
        if (slot<0 || slot>=54 || (paginated && slot==45)) return;
        int pageIndex=paginated && slot>45 ? slot-1 : slot;
        int index=page*pageSize+pageIndex; if(index>=entries.size()) return;
        CatalogEntry selected=entries.get(index);
        if (selected.purchase()) { heartSword.purchase(player); openCatalog(player,page); return; }
        ItemStack reward=selected.stack().clone();
        Map<Integer,ItemStack> leftovers=player.getInventory().addItem(reward);
        leftovers.values().forEach(leftover->player.getWorld().dropItemNaturally(player.getLocation(),leftover));
        player.sendMessage(color("&aOtrzymano item: &e"+ChatColor.stripColor(reward.getItemMeta().hasDisplayName()?reward.getItemMeta().getDisplayName():reward.getType().name())));
    }
    private void setBalanceItems(Inventory inv,double pending,EntityType type,int level) {
        String amount=compactMoney(pending);
        ItemStack gold=new ItemStack(Material.GOLD_INGOT); ItemMeta gm=gold.getItemMeta(); gm.setDisplayName(SpawnerItemModule.styledColor("&6&lSALDO &f&l| &e&l" + amount + " &6&l$")); gm.setLore(List.of(SpawnerItemModule.styledColor("&8▸ &fZGROMADZONE PIENIĄDZE:"),SpawnerItemModule.styledColor("&8▸ &e" + amount + " &6$"),SpawnerItemModule.styledColor("&r"),SpawnerItemModule.styledColor("&8▸ &fDochód: &a"+wholeDollars(income(type,level))+" &a$/s"),SpawnerItemModule.styledColor("&r"),SpawnerItemModule.styledColor("&8▸ &fPIENIĄDZE CZEKAJĄ TUTAJ"),SpawnerItemModule.styledColor("&8▸ &fNA &6&nWYPŁATĘ"))); gold.setItemMeta(gm); inv.setItem(2,gold);
        ItemStack emerald=new ItemStack(Material.EMERALD); ItemMeta em=emerald.getItemMeta(); em.setDisplayName(SpawnerItemModule.styledColor("&a&lWYPŁAĆ PIENIĄDZE")); em.setLore(List.of(SpawnerItemModule.styledColor("&8▸ &fDO WYPŁATY: &e"+amount+" &6$"),SpawnerItemModule.styledColor("&r"),SpawnerItemModule.styledColor("&8▸ &a&nKLIKNIJ&r&f, ABY ODEBRAĆ"),SpawnerItemModule.styledColor("&8▸ &fZGROMADZONE PIENIĄDZE"))); emerald.setItemMeta(em); inv.setItem(4,emerald);
    }
    private double storedBalance(org.bukkit.persistence.PersistentDataContainer data) {
        double pending=data.getOrDefault(pendingKey,PersistentDataType.DOUBLE,0d);
        double remainder=data.getOrDefault(remainderKey,PersistentDataType.DOUBLE,0d);
        if(!Double.isFinite(pending)||pending<0)pending=0;
        if(!Double.isFinite(remainder)||remainder<0)remainder=0;
        return Math.floor(pending+remainder);
    }
    private double projectedBalance(org.bukkit.persistence.PersistentDataContainer data,EntityType type,int level) {
        double balance=storedBalance(data);
        long now=System.currentTimeMillis();
        long last=data.getOrDefault(tickKey,PersistentDataType.LONG,now);
        long elapsedSeconds=Math.max(0,now-last)/1000L;
        double generated=elapsedSeconds*income(type,level);
        return Double.isFinite(balance+generated)?balance+generated:balance;
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e instanceof CraftItemEvent craft && craft.getRawSlot()==0
                && (craft.getInventory().getType()==InventoryType.CRAFTING || craft.getInventory().getType()==InventoryType.WORKBENCH)) {
            craft.setCancelled(true);
            ItemStack result=craft.getInventory().getResult();
            if(result==null||result.getType()==Material.AIR)return;
            ItemStack[] matrix=craft.getInventory().getMatrix();
            int remaining=0;
            for(ItemStack ingredient:matrix)if(isSpawnerItem(ingredient))remaining+=ingredient.getAmount();
            int needed=remaining;
            for(ItemStack ingredient:matrix)if(isSpawnerItem(ingredient)){needed=spawnerItems.level(result)/Math.max(1,itemLevel(ingredient));break;}
            remaining=needed;
            for(int i=0;i<matrix.length&&remaining>0;i++){
                ItemStack ingredient=matrix[i];
                if(ingredient==null||!isSpawnerItem(ingredient))continue;
                int consumed=Math.min(remaining,ingredient.getAmount());
                remaining-=consumed;
                if(ingredient.getAmount()==consumed)matrix[i]=null;
                else {matrix[i]=ingredient.clone();matrix[i].setAmount(ingredient.getAmount()-consumed);}
            }
            if(remaining!=0)return;
            craft.getInventory().setMatrix(matrix);
            Map<Integer,ItemStack> leftovers=p.getInventory().addItem(result.clone());
            leftovers.values().forEach(leftover->p.getWorld().dropItemNaturally(p.getLocation(),leftover));
            p.updateInventory();
            return;
        }
        if (openCatalogs.containsKey(p.getUniqueId())) { clickCatalog(e,p); return; }
        if (!openMenus.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        Location loc=openMenus.get(p.getUniqueId()); if (loc.getBlock().getState() instanceof CreatureSpawner sp && sp.getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE)) {
            if (e.getRawSlot()==0) {
                p.sendMessage(color("&ePołącz trzy takie same spawnery w stole rzemieślniczym.")); return;
            }
            if (e.getRawSlot()!=4) return;
            accrue(sp); var d=sp.getPersistentDataContainer(); double amount=storedBalance(d);
            if (amount>0) {
                UUID owner=storedOwner(d.get(ownerKey,PersistentDataType.STRING),p.getUniqueId());
                OfflinePlayer account=Bukkit.getOfflinePlayer(owner);
                EconomyResponse response=economy.depositPlayer(account,amount);
                if (response.transactionSuccess()) {
                    d.set(pendingKey,PersistentDataType.DOUBLE,0d); d.set(remainderKey,PersistentDataType.DOUBLE,0d); sp.update();
                    p.sendMessage(color("&aWypłacono &e"+wholeMoney(amount)+" $&a na konto właściciela."));
                } else p.sendMessage(color("&cVault nie zrealizował wypłaty. Pieniądze pozostały w spawnerze."));
            } else p.sendMessage(color("&cNie masz jeszcze pieniędzy do wypłaty."));
            showMenu(p,sp);
        }
    }
    @EventHandler public void prepareCraft(PrepareItemCraftEvent e) {
        if (!(e.getRecipe() instanceof ShapelessRecipe recipe)) return;
        NamespacedKey recipeKey=recipe.getKey();
        if (!recipeKey.equals(new NamespacedKey(this,"combine_spawners"))
                && !recipeKey.equals(new NamespacedKey(this,"combine_spawners_two"))) return;
        ItemStack[] matrix=e.getInventory().getMatrix();
        List<ItemStack> ingredients=Arrays.stream(matrix).filter(Objects::nonNull).filter(i->i.getType()!=Material.AIR).toList();
        int unitCount=ingredients.stream().mapToInt(ItemStack::getAmount).sum();
        if (ingredients.stream().anyMatch(i->!isSpawnerItem(i)) || (unitCount!=2&&unitCount!=3)) { e.getInventory().setResult(null); return; }
        List<ItemStack> units=new ArrayList<>();
        for(ItemStack ingredient:ingredients)for(int i=0;i<ingredient.getAmount();i++)units.add(ingredient);
        ItemStack first=units.get(0); EntityType type=itemType(first); int level=itemLevel(first);
        if (units.stream().anyMatch(i->itemType(i)!=type || itemLevel(i)!=level)) { e.getInventory().setResult(null); return; }
        int newLevel;
        try { newLevel=SpawnerProgression.combineLevels(units.stream().map(this::itemLevel).toList(),maxLevel(type)); }
        catch (IllegalArgumentException ex) { e.getInventory().setResult(null); return; }
        UUID fallback=e.getView().getPlayer() instanceof Player player ? player.getUniqueId() : null;
        UUID owner=itemOwner(first,fallback);
        if(units.stream().anyMatch(item->!Objects.equals(owner,itemOwner(item,fallback)))){e.getInventory().setResult(null);return;}
        double carried=units.stream().mapToDouble(i->i.getItemMeta().getPersistentDataContainer().getOrDefault(pendingKey,PersistentDataType.DOUBLE,0d)).sum();
        double remainder=units.stream().mapToDouble(i->i.getItemMeta().getPersistentDataContainer().getOrDefault(remainderKey,PersistentDataType.DOUBLE,0d)).sum();
        e.getInventory().setResult(spawnerItems.create(type,newLevel,owner,carried,remainder));
    }
    @EventHandler public void close(org.bukkit.event.inventory.InventoryCloseEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            if (!(e.getPlayer() instanceof Player catalogPlayer) || !catalogPlayer.isOnline()
                    || !ChatColor.stripColor(catalogPlayer.getOpenInventory().getTitle()).trim().equalsIgnoreCase("QC ITEMY")) openCatalogs.remove(id);
            if (!(e.getPlayer() instanceof Player player) || !player.isOnline()
                    || !ChatColor.stripColor(player.getOpenInventory().getTitle()).trim().equalsIgnoreCase("SPAWNER")) openMenus.remove(id);
        });
    }
    @EventHandler(ignoreCancelled=true) public void breakSpawner(BlockBreakEvent e) {
        if (!(e.getBlock().getState() instanceof CreatureSpawner sp) || !sp.getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE)) return;
        var d=sp.getPersistentDataContainer(); UUID owner=storedOwner(d.get(ownerKey,PersistentDataType.STRING),e.getPlayer().getUniqueId());
        if (!owner.equals(e.getPlayer().getUniqueId()) && !e.getPlayer().hasPermission("striderspawner.admin")) { e.setCancelled(true); e.getPlayer().sendMessage(color("&cTen spawner należy do innego gracza.")); return; }
        EntityType type=storedType(d.get(typeKey,PersistentDataType.STRING));
        accrue(sp); d=sp.getPersistentDataContainer(); int level=d.getOrDefault(levelKey,PersistentDataType.INTEGER,1);
        double pending=d.getOrDefault(pendingKey,PersistentDataType.DOUBLE,0d);
        double remainder=d.getOrDefault(remainderKey,PersistentDataType.DOUBLE,0d);
        e.setDropItems(false); e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(),spawnerItems.create(type,level,owner,pending,remainder));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void miningPickaxeDrops(BlockBreakEvent e) {
        ItemStack held=e.getPlayer().getInventory().getItemInMainHand();
        if(!miningPickaxe.enabled()||!miningPickaxe.isPickaxe(held))return;
        SpawnerLocation current=spawnerLocation(e.getBlock().getLocation());
        boolean areaEvent=areaMiningBlocks.contains(current);
        int areaSize=miningPickaxe.areaSize(held);
        if(!areaEvent){
            int uses=miningPickaxe.uses(held);
            if(uses==0){e.setCancelled(true);e.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));e.getPlayer().sendMessage(color("&cKilof Milionera się zużył."));return;}
            ItemStack updated=held.clone();
            if(uses>0){miningPickaxe.setUses(updated,uses-1);e.getPlayer().getInventory().setItemInMainHand(updated);}
            if(areaSize>1)mineArea(e,updated,areaSize);
            if(uses==1)Bukkit.getScheduler().runTask(this,()->{
                ItemStack currentTool=e.getPlayer().getInventory().getItemInMainHand();
                if(miningPickaxe.isPickaxe(currentTool)&&miningPickaxe.uses(currentTool)==0)
                    e.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            });
        }
        if(!MiningPickaxeItem.canDropStoneRewards(miningPickaxe.level(held),areaSize)||e.getBlock().getType()!=Material.STONE)return;
        Location dropLocation=e.getBlock().getLocation().add(0.5,0.5,0.5);
        if(miningPickaxe.dropsResource())e.getBlock().getWorld().dropItemNaturally(dropLocation,new ItemStack(miningPickaxe.randomResource()));
        if(miningPickaxe.dropsHeart()){
            ItemStack heart=miningPickaxe.heartDropItem();
            if(heart!=null){heart.setAmount(1);e.getBlock().getWorld().dropItemNaturally(dropLocation,heart);}
        }
    }
    private void mineArea(BlockBreakEvent source,ItemStack tool,int areaSize){
        Block center=source.getBlock();Material targetType=center.getType();Player player=source.getPlayer();
        int firstOffset=-((areaSize-1)/2),lastOffset=firstOffset+areaSize-1;
        boolean horizontalView=Math.abs(player.getLocation().getPitch())<60.0f;
        BlockFace facing=player.getFacing();
        for(int a=firstOffset;a<=lastOffset;a++)for(int b=firstOffset;b<=lastOffset;b++){
            if(a==0&&b==0)continue;
            Block target;
            if(!horizontalView)target=center.getWorld().getBlockAt(center.getX()+a,center.getY(),center.getZ()+b);
            else if(facing==BlockFace.NORTH||facing==BlockFace.SOUTH)target=center.getWorld().getBlockAt(center.getX()+a,center.getY()+b,center.getZ());
            else target=center.getWorld().getBlockAt(center.getX(),center.getY()+b,center.getZ()+a);
            if(target.getType()!=targetType||target.getState() instanceof TileState)continue;
            SpawnerLocation key=spawnerLocation(target.getLocation());
            if(!areaMiningBlocks.add(key))continue;
            BlockBreakEvent extra=new BlockBreakEvent(target,player);
            try{Bukkit.getPluginManager().callEvent(extra);}finally{areaMiningBlocks.remove(key);}
            if(extra.isCancelled())continue;
            Collection<ItemStack> drops=extra.isDropItems()?target.getDrops(tool,player):List.of();
            target.setType(Material.AIR,false);
            for(ItemStack drop:drops)target.getWorld().dropItemNaturally(target.getLocation(),drop);
            if(extra.getExpToDrop()>0)target.getWorld().spawn(target.getLocation(),org.bukkit.entity.ExperienceOrb.class,orb->orb.setExperience(extra.getExpToDrop()));
        }
    }
    @EventHandler public void mobDrop(EntityDeathEvent e) {
        Player killer=e.getEntity().getKiller(); if (killer==null) return;
        for (String name:spawnerItems.types()) {
            if (!spawnerItems.enabled(name)) continue;
            EntityType spawnerType;
            try { spawnerType=EntityType.valueOf(name.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { continue; }
            if (!SpawnerProgression.mobDropMatches(spawnerType,e.getEntityType())) continue;
            double chance=spawnerItems.dropChance(spawnerType);
            if (Math.random()>=chance) continue;
            int min=spawnerItems.dropMin(spawnerType);
            int max=spawnerItems.dropMax(spawnerType);
            int amount=min+(int)(Math.random()*(max-min+1));
            while(amount>0){ItemStack drop=spawnerItem(spawnerType,1,killer.getUniqueId());int stackAmount=Math.min(amount,drop.getMaxStackSize());drop.setAmount(stackAmount);e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(),drop);amount-=stackAmount;}
        }
    }
    @EventHandler(ignoreCancelled=true)
    public void heartSwordAbility(EntityDamageByEntityEvent event) {
        heartSword.onDamage(event);
    }
}
