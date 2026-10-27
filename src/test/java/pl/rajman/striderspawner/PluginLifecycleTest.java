package pl.rajman.striderspawner;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class PluginLifecycleTest {
    private ServerMock server;
    private StriderSpawnerPlugin plugin;
    private PlayerMock player;
    private double depositedAmount;
    private double withdrawnAmount;
    private UUID depositedPlayer;
    private EconomyResponse.ResponseType transactionResult=EconomyResponse.ResponseType.SUCCESS;

    @BeforeEach void startPluginWithVaultEconomy() {
        server=MockBukkit.mock();
        var vault=MockBukkit.createMockPlugin("Vault");
        Economy economy=(Economy)Proxy.newProxyInstance(Economy.class.getClassLoader(),new Class<?>[]{Economy.class},(proxy,method,args)->switch(method.getName()) {
            case "depositPlayer" -> {
                depositedPlayer=((OfflinePlayer)args[0]).getUniqueId(); depositedAmount=(double)args[1];
                yield new EconomyResponse(depositedAmount,depositedAmount,transactionResult,null);
            }
            case "withdrawPlayer" -> {withdrawnAmount=(double)args[1];yield new EconomyResponse(withdrawnAmount,0.0,EconomyResponse.ResponseType.SUCCESS,null);}
            case "getBalance" -> 1000.0;
            case "getName" -> "TestEconomy";
            case "isEnabled" -> true;
            case "hasBankSupport" -> false;
            case "fractionalDigits" -> 2;
            case "format" -> String.valueOf(args[0]);
            case "currencyNameSingular" -> "dollar";
            case "currencyNamePlural" -> "dollars";
            case "toString" -> "TestEconomy";
            default -> method.getReturnType()==boolean.class ? false : method.getReturnType()==int.class ? 0 : method.getReturnType()==double.class ? 0.0 : null;
        });
        server.getServicesManager().register(Economy.class,economy,vault,ServicePriority.Normal);
        plugin=MockBukkit.load(StriderSpawnerPlugin.class);
        player=server.addPlayer("PigHunter");
    }

    @AfterEach void stopServer() { MockBukkit.unmock(); }

    @Test void pluginStartsAndRegistersItsCommandAndCraftingRecipe() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("striderspawner"));
        assertTrue(server.getRecipesFor(new org.bukkit.inventory.ItemStack(Material.SPAWNER)).stream()
                .anyMatch(recipe->recipe.getResult().getType()==Material.SPAWNER));
    }

    @Test void giveCommandCreatesConfiguredMobSpawnerWithLevelInName() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"ss give PigHunter PIG 1"));
        var item=player.getInventory().getItem(0);
        assertNotNull(item);
        assertEquals(Material.SPAWNER,item.getType());
        assertEquals(org.bukkit.inventory.ItemRarity.EPIC,item.getItemMeta().getRarity());
        assertTrue(item.getItemMeta().getDisplayName().startsWith(org.bukkit.ChatColor.AQUA+"§l("+org.bukkit.ChatColor.DARK_AQUA+"§lPIG"+org.bukkit.ChatColor.AQUA+"§l) "+org.bukkit.ChatColor.YELLOW+"§lSPAWNER "+org.bukkit.ChatColor.GOLD+"§l★"+org.bukkit.ChatColor.YELLOW+"§l1"));
        assertTrue(item.getItemMeta().getDisplayName().contains("PIG"));
        assertTrue(item.getItemMeta().getDisplayName().contains("1"));
        assertTrue(item.getItemMeta().getLore().get(0).startsWith(org.bukkit.ChatColor.DARK_GRAY+"§l▶ "+org.bukkit.ChatColor.GRAY+"§lTYP: "+org.bukkit.ChatColor.AQUA+"§l"));
        assertTrue(item.getItemMeta().getLore().get(1).startsWith(org.bukkit.ChatColor.DARK_GRAY+"§l▶ "+org.bukkit.ChatColor.GRAY+"§lPOZIOM: "+org.bukkit.ChatColor.YELLOW+"§l"));
        String itemLore=org.bukkit.ChatColor.stripColor(String.join("\n",item.getItemMeta().getLore()));
        assertTrue(itemLore.contains("ZGROMADZONE: 0 $"));
        assertTrue(itemLore.contains("POŁĄCZ W CRAFTINGU"));
        assertTrue(item.getItemMeta().getLore().get(2).isEmpty());
        assertEquals(org.bukkit.ChatColor.DARK_GRAY+"Kliknięcie jajem przywołania:", item.getItemMeta().getLore().get(9));
        assertFalse(itemLore.contains("WŁAŚCICIEL:"));
    }

    @Test void striderSpawnerTitleAndLoreMatchTheInventoryAndMenuReferences(){
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"ss give PigHunter STRIDER 3"));
        ItemStack held=player.getInventory().getItem(0);
        assertEquals("(STRIDER) SPAWNER ★3",org.bukkit.ChatColor.stripColor(held.getItemMeta().getDisplayName()));
        assertEquals(List.of("▶ TYP: STRIDER","▶ POZIOM: 3","","▶ DOCHÓD: 15 $/s","▶ ZGROMADZONE: 0 $","",
                        "▶ POŁĄCZ W CRAFTINGU","▶ LUB KLIKNIJ SWÓJ SPAWNER","","Kliknięcie jajem przywołania:","Ustawia typ moba"),
                held.getItemMeta().getLore().stream().map(org.bukkit.ChatColor::stripColor).toList());

        Block block=new Location(player.getWorld(),13,65,13).getBlock();block.setType(Material.SPAWNER);
        plugin.place(new BlockPlaceEvent(block,block.getState(),block.getRelative(BlockFace.DOWN),held,player,true,EquipmentSlot.HAND));
        PlayerInteractEvent interact=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
        plugin.interact(interact);
        ItemStack menuItem=player.getOpenInventory().getTopInventory().getItem(0);
        assertEquals("SPAWNER ★3",org.bukkit.ChatColor.stripColor(menuItem.getItemMeta().getDisplayName()));
        assertEquals(List.of("▶ WŁAŚCICIEL: PigHunter","▶ TYP: STRIDER","▶ POZIOM: 3","","▶ DOCHÓD: 15 $/s","",
                        "▶ POŁĄCZ Z INNYM SPAWNEREM","▶ TEGO SAMEGO TYPU, ABY","▶ ZWIĘKSZYĆ JEGO POZIOM","",
                        "Kliknięcie jajem przywołania:","Ustawia typ moba"),
                menuItem.getItemMeta().getLore().stream().map(org.bukkit.ChatColor::stripColor).toList());
    }

    @Test void itemCatalogKeepsBuiltinsAndSupportsAddingAndRemovingCustomItems() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        ItemStack custom=new ItemStack(Material.DIAMOND);
        var customMeta=custom.getItemMeta(); customMeta.setDisplayName("QC Diamond"); custom.setItemMeta(customMeta);
        player.getInventory().setItemInMainHand(custom);
        assertTrue(server.dispatchCommand(player,"ss item add qc_diamond"));
        assertTrue(server.dispatchCommand(player,"ss item remove default_pig"));
        assertTrue(server.dispatchCommand(player,"itemy"));

        Inventory catalog=player.getOpenInventory().getTopInventory();
        assertEquals("QC ITEMY",org.bukkit.ChatColor.stripColor(player.getOpenInventory().getTitle()).trim());
        assertEquals(54,catalog.getSize());
        assertEquals(Material.SPAWNER,catalog.getItem(0).getType());
        assertEquals(Material.DIAMOND,catalog.getItem(4).getType());
        assertEquals("QC Diamond",catalog.getItem(4).getItemMeta().getDisplayName());
        assertFalse(Arrays.stream(catalog.getContents()).filter(Objects::nonNull).anyMatch(i->i.getType()==Material.GREEN_DYE || i.getType()==Material.RED_DYE));

        assertTrue(server.dispatchCommand(player,"ss item remove qc_diamond"));
        player.closeInventory();
        assertTrue(server.dispatchCommand(player,"itemy"));
        catalog=player.getOpenInventory().getTopInventory();
        assertEquals(Material.SPAWNER,catalog.getItem(0).getType(),"A built-in spawner must remain after remove attempts");
        assertFalse(Arrays.stream(catalog.getContents()).filter(Objects::nonNull).anyMatch(i->i.getType()==Material.DIAMOND));
    }

    @Test void itemCatalogAddsNavigationOnlyAboveDoubleChestCapacity() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        ItemStack custom=new ItemStack(Material.PAPER);
        for (int i=0;i<50;i++) {
            var meta=custom.getItemMeta(); meta.setDisplayName("Custom "+i); custom.setItemMeta(meta);
            player.getInventory().setItemInMainHand(custom);
            assertTrue(server.dispatchCommand(player,"ss item add item_"+i));
        }
        assertTrue(server.dispatchCommand(player,"itemy"));
        Inventory catalog=player.getOpenInventory().getTopInventory();
        assertFalse(Arrays.stream(catalog.getContents()).filter(Objects::nonNull).anyMatch(i->i.getType()==Material.GREEN_DYE || i.getType()==Material.RED_DYE),"54 items fit without navigation buttons");

        var meta=custom.getItemMeta(); meta.setDisplayName("Custom 50"); custom.setItemMeta(meta);
        player.getInventory().setItemInMainHand(custom);
        assertTrue(server.dispatchCommand(player,"ss item add item_50"));
        player.closeInventory();
        assertTrue(server.dispatchCommand(player,"itemy"));
        catalog=player.getOpenInventory().getTopInventory();
        assertEquals(Material.GREEN_DYE,catalog.getItem(53).getType(),"Pagination starts at 55 items");
        plugin.click(new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,53,ClickType.LEFT,InventoryAction.PICKUP_ALL));
        catalog=player.getOpenInventory().getTopInventory();
        assertEquals(Material.RED_DYE,catalog.getItem(45).getType());
        assertEquals(Material.PAPER,catalog.getItem(0).getType());
        assertNull(catalog.getItem(53),"The final page has no next button");
    }

    @Test void adminCatalogGivesHeartSwordForFree() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"itemy"));

        Inventory catalog=player.getOpenInventory().getTopInventory();
        int swordSlot=-1;
        for (int slot=0;slot<catalog.getSize();slot++) if (catalog.getItem(slot)!=null && catalog.getItem(slot).getType()==Material.NETHERITE_SWORD) swordSlot=slot;
        assertTrue(swordSlot>=0,"The heart sword should be a built-in shop item");
        ItemStack preview=catalog.getItem(swordSlot).clone();
        assertEquals("Miecz N1",org.bukkit.ChatColor.stripColor(preview.getItemMeta().getDisplayName()));
        assertFalse(String.join(" ",preview.getItemMeta().getLore()).contains("Koszt:"),"The admin catalog sword is free");
        plugin.click(new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,swordSlot,ClickType.LEFT,InventoryAction.PICKUP_ALL));
        ItemStack purchasedSword=Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull)
                .filter(i->i.getType()==Material.NETHERITE_SWORD && i.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin,"heart_sword"),PersistentDataType.BYTE))
                .findFirst().orElseThrow();
        assertEquals(9.0,purchasedSword.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE).iterator().next().getAmount());
        assertEquals(1.6,purchasedSword.getItemMeta().getAttributeModifiers(Attribute.ATTACK_SPEED).iterator().next().getAmount());
        assertEquals(preview.getItemMeta().getDisplayName(),purchasedSword.getItemMeta().getDisplayName());
        assertEquals(preview.getItemMeta().getLore(),purchasedSword.getItemMeta().getLore());
        assertEquals(0.0,withdrawnAmount,"Free catalog item must not charge money");
        plugin.heartSwordConfig().set("strength-chance",1.0);
        player.getInventory().setItemInMainHand(purchasedSword);
        LivingEntity target=(LivingEntity)player.getWorld().spawnEntity(new Location(player.getWorld(),4,65,4),EntityType.PIG);
        plugin.heartSwordAbility(new EntityDamageByEntityEvent(player,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,1.0));
        assertTrue(player.hasPotionEffect(PotionEffectType.STRENGTH),"The purchased sword must apply its configured ability");
    }

    @Test void regularPlayersCannotOpenAdminCatalog(){
        player.setOp(false);
        assertTrue(server.dispatchCommand(player,"itemy"));
        assertFalse(org.bukkit.ChatColor.stripColor(player.getOpenInventory().getTitle()).trim().equals("QC ITEMY"));
    }

    @Test void placedSpawnerOpensMenuAndPaysTheKillerOwnerThroughVault() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"ss give PigHunter PIG 1"));
        ItemStack item=player.getInventory().getItem(0);
        PlayerMock builder=server.addPlayer("Builder");
        Location location=new Location(player.getWorld(),5,65,5);
        player.getWorld().loadChunk(0,0);
        Block block=location.getBlock(); block.setType(Material.SPAWNER);
        BlockPlaceEvent place=new BlockPlaceEvent(block,block.getState(),block.getRelative(BlockFace.DOWN),item,builder,true,EquipmentSlot.HAND);
        plugin.place(place);

        CreatureSpawner state=(CreatureSpawner)block.getState();
        var pdc=state.getPersistentDataContainer();
        assertEquals(0,state.getMaxNearbyEntities(),"A plugin spawner must not spawn mobs");
        assertEquals(0,state.getRequiredPlayerRange(),"A plugin spawner must remain passive");
        pdc.set(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE,12.0);
        assertEquals(player.getUniqueId().toString(),pdc.get(new NamespacedKey(plugin,"owner"),PersistentDataType.STRING));
        state.update();

        PlayerInteractEvent interact=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,block,BlockFace.UP,EquipmentSlot.HAND);
        plugin.interact(interact);
        assertTrue(interact.isCancelled());
        assertEquals("SPAWNER",org.bukkit.ChatColor.stripColor(player.getOpenInventory().getTitle()).trim());
        assertEquals(InventoryType.HOPPER,player.getOpenInventory().getTopInventory().getType());
        assertEquals(5,player.getOpenInventory().getTopInventory().getSize());
        assertEquals(Material.SPAWNER,player.getOpenInventory().getTopInventory().getItem(0).getType());
        assertEquals("SPAWNER ★1",org.bukkit.ChatColor.stripColor(player.getOpenInventory().getTopInventory().getItem(0).getItemMeta().getDisplayName()));
        String menuLore=org.bukkit.ChatColor.stripColor(String.join("\n",player.getOpenInventory().getTopInventory().getItem(0).getItemMeta().getLore()));
        assertTrue(menuLore.contains("WŁAŚCICIEL: PigHunter"));
        assertTrue(menuLore.contains("DOCHÓD: 5 $/s"));
        assertTrue(menuLore.contains("POŁĄCZ Z INNYM SPAWNEREM"));
        assertFalse(menuLore.contains("ZGROMADZONE:"));
        assertTrue(menuLore.endsWith("Kliknięcie jajem przywołania:\nUstawia typ moba"),"The vanilla hint should be placed after the custom lore");
        assertEquals(Material.GOLD_INGOT,player.getOpenInventory().getTopInventory().getItem(2).getType());
        String saldoTitle=player.getOpenInventory().getTopInventory().getItem(2).getItemMeta().getDisplayName();
        String strippedSaldoTitle=org.bukkit.ChatColor.stripColor(saldoTitle);
        assertTrue(strippedSaldoTitle.matches("SALDO \\| 12 \\$"), "Saldo title: '"+saldoTitle+"', stripped: '"+strippedSaldoTitle+"'");
        assertTrue(saldoTitle.startsWith(org.bukkit.ChatColor.GOLD+"§lSALDO "+org.bukkit.ChatColor.WHITE+"§l| "+org.bukkit.ChatColor.YELLOW+"§l"));

        CreatureSpawner ticking=(CreatureSpawner)block.getState();
        ticking.getPersistentDataContainer().set(new NamespacedKey(plugin,"last_tick"),PersistentDataType.LONG,System.currentTimeMillis()-5000);
        ticking.update();
        plugin.accrue(ticking);
        plugin.refreshOpenMenus();
        String refreshed=player.getOpenInventory().getTopInventory().getItem(2).getItemMeta().getDisplayName();
        double storedAfterTick=((CreatureSpawner)block.getState()).getPersistentDataContainer().get(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE);
        assertEquals(37.0,storedAfterTick,"Balance stores whole dollars only");
        assertTrue(org.bukkit.ChatColor.stripColor(refreshed).contains("37 $"),"Open menu was '"+refreshed+"', block balance was "+storedAfterTick);

        plugin.click(new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,4,ClickType.LEFT,InventoryAction.PICKUP_ALL));
        assertEquals(37.0,depositedAmount,"Only whole dollars should be paid out");
        assertEquals(player.getUniqueId(),depositedPlayer);
        CreatureSpawner paid=(CreatureSpawner)block.getState();
        assertEquals(0.0,paid.getPersistentDataContainer().get(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE));

        var paidData=paid.getPersistentDataContainer(); paidData.set(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE,4.0); paid.update();
        transactionResult=EconomyResponse.ResponseType.FAILURE;
        plugin.click(new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,4,ClickType.LEFT,InventoryAction.PICKUP_ALL));
        CreatureSpawner afterFailure=(CreatureSpawner)block.getState();
        assertTrue(afterFailure.getPersistentDataContainer().get(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE)>=4.0,
                "A failed Vault transaction must leave the balance in the spawner");
    }

    @Test void spawnEggCannotChangeTheConfiguredSpawnerType() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"ss give PigHunter PIG 1"));
        ItemStack spawner=player.getInventory().getItem(0);
        Block block=new Location(player.getWorld(),7,65,7).getBlock(); block.setType(Material.SPAWNER);
        plugin.place(new BlockPlaceEvent(block,block.getState(),block.getRelative(BlockFace.DOWN),spawner,player,true,EquipmentSlot.HAND));
        ItemStack egg=new ItemStack(Material.COW_SPAWN_EGG);
        player.getInventory().setItemInMainHand(egg);
        PlayerInteractEvent interact=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,egg,block,BlockFace.UP,EquipmentSlot.HAND);
        plugin.interact(interact);
        assertEquals(1,player.getInventory().getItemInMainHand().getAmount(),"The egg must not be consumed");
        CreatureSpawner state=(CreatureSpawner)block.getState();
        assertEquals("PIG",state.getPersistentDataContainer().get(new NamespacedKey(plugin,"mob_type"),PersistentDataType.STRING));
        player.getInventory().setItemInOffHand(egg);
        PlayerInteractEvent offhandInteract=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,egg,block,BlockFace.UP,EquipmentSlot.OFF_HAND);
        plugin.interact(offhandInteract);
        assertTrue(offhandInteract.isCancelled(),"Offhand spawn eggs must not change a plugin spawner either");
        assertEquals("PIG",((CreatureSpawner)block.getState()).getPersistentDataContainer().get(new NamespacedKey(plugin,"mob_type"),PersistentDataType.STRING));
    }

    @Test void fractionalSpawnerBalanceIsPreservedBetweenTicks() {
        plugin.spawnerConfig().set("spawners.PIG.income-per-second-per-level",0.5);
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"ss give PigHunter PIG 1"));
        ItemStack spawner=player.getInventory().getItem(0);
        var meta=spawner.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE,0.25);spawner.setItemMeta(meta);
        Block block=new Location(player.getWorld(),9,65,9).getBlock(); block.setType(Material.SPAWNER);
        plugin.place(new BlockPlaceEvent(block,block.getState(),block.getRelative(BlockFace.DOWN),spawner,player,true,EquipmentSlot.HAND));
        CreatureSpawner state=(CreatureSpawner)block.getState();
        state.getPersistentDataContainer().set(new NamespacedKey(plugin,"last_tick"),PersistentDataType.LONG,System.currentTimeMillis()-2000L);
        state.update(); plugin.accrue(state);
        double pending=((CreatureSpawner)block.getState()).getPersistentDataContainer().get(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE);
        double remainder=((CreatureSpawner)block.getState()).getPersistentDataContainer().get(new NamespacedKey(plugin,"income_remainder"),PersistentDataType.DOUBLE);
        assertEquals(1.0,pending,"Visible balance contains whole dollars only");
        assertTrue(remainder>=0.24 && remainder<=0.26,"Fractional earned amount is retained internally: "+remainder);
    }

    @Test void cancelledSpawnerBreakDoesNotDropAnItem(){
        player.addAttachment(plugin,"striderspawner.admin",true);
        assertTrue(server.dispatchCommand(player,"ss give PigHunter PIG 1"));
        ItemStack item=player.getInventory().getItem(0);
        Block block=new Location(player.getWorld(),11,65,11).getBlock();block.setType(Material.SPAWNER);
        server.getPluginManager().callEvent(new BlockPlaceEvent(block,block.getState(),block.getRelative(BlockFace.DOWN),item,player,true,EquipmentSlot.HAND));
        BlockBreakEvent breakEvent=new BlockBreakEvent(block,player);breakEvent.setCancelled(true);
        server.getPluginManager().callEvent(breakEvent);
        assertTrue(breakEvent.isDropItems(),"A cancelled break should not be modified by the spawner handler");
        assertTrue(player.getWorld().getEntitiesByClass(Item.class).isEmpty(),"A cancelled break must not duplicate the spawner");
    }

    @Test void pigSpawnerDropsFromPigAndKeepsTheKillerAsOwner() {
        plugin.spawnerConfig().set("spawners.PIG.drop.chance",1.0);
        plugin.spawnerConfig().set("spawners.COW.drop.chance",0.0);
        LivingEntity pig=(LivingEntity)player.getWorld().spawnEntity(new Location(player.getWorld(),2,65,2),EntityType.PIG);
        pig.setKiller(player);
        plugin.mobDrop(new EntityDeathEvent(pig,null,new ArrayList<>()));
        var drops=player.getWorld().getEntitiesByClass(Item.class);
        assertEquals(1,drops.size());
        Item droppedEntity=drops.iterator().next();
        ItemStack dropped=droppedEntity.getItemStack();
        assertTrue(dropped.getItemMeta().getDisplayName().contains("PIG"));
        assertEquals(player.getUniqueId().toString(),dropped.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"owner"),PersistentDataType.STRING));

        droppedEntity.remove();
        LivingEntity cow=(LivingEntity)player.getWorld().spawnEntity(new Location(player.getWorld(),3,65,3),EntityType.COW);
        cow.setKiller(player);
        plugin.mobDrop(new EntityDeathEvent(cow,null,new ArrayList<>()));
        assertTrue(player.getWorld().getEntitiesByClass(Item.class).isEmpty(),"A cow must not drop a PIG spawner");
    }

    @Test void craftingThreeMatchingSpawnersSumsLevelAndStoredIncome() {
        player.addAttachment(plugin,"striderspawner.admin",true);
        for (int i=0;i<3;i++) assertTrue(server.dispatchCommand(player,"ss give PigHunter PIG 1"));
        ItemStack[] matrix=new ItemStack[9];
        ItemStack stacked=player.getInventory().getItem(0);
        assertNotNull(stacked);
        for (int i=0;i<3;i++) {
            matrix[i]=stacked.clone(); matrix[i].setAmount(1);
            var meta=matrix[i].getItemMeta();
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE,i+1.0);
            matrix[i].setItemMeta(meta);
        }
        Inventory inventory=Bukkit.createInventory(player,InventoryType.WORKBENCH);
        InventoryView view=player.openInventory(inventory);
        CraftingInventory crafting=(CraftingInventory)view.getTopInventory();
        crafting.setMatrix(matrix);
        Recipe recipe=server.getRecipe(new NamespacedKey(plugin,"combine_spawners"));
        assertNotNull(recipe,"The custom three-spawner recipe must be registered");
        plugin.prepareCraft(new org.bukkit.event.inventory.PrepareItemCraftEvent(crafting,view,false) {
            @Override public Recipe getRecipe() { return recipe; }
        });
        ItemStack result=crafting.getResult();
        assertNotNull(result);
        assertTrue(result.getItemMeta().getDisplayName().contains("3"));
        assertEquals(6.0,result.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"pending"),PersistentDataType.DOUBLE));

        var differentOwner=matrix[2].getItemMeta();
        differentOwner.getPersistentDataContainer().set(new NamespacedKey(plugin,"owner"),PersistentDataType.STRING,UUID.randomUUID().toString());
        matrix[2].setItemMeta(differentOwner);crafting.setMatrix(matrix);
        plugin.prepareCraft(new org.bukkit.event.inventory.PrepareItemCraftEvent(crafting,view,false) {
            @Override public Recipe getRecipe() { return recipe; }
        });
        assertNull(crafting.getResult(),"Spawners from different owners cannot be merged");
    }
}
