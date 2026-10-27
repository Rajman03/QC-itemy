package pl.rajman.striderspawner.items.n1mc;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.rajman.striderspawner.items.spawners.SpawnerItemModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** One self-contained built-in item: catalog icon, attributes and combat ability. */
public final class HeartSwordItem {
    private final JavaPlugin plugin;
    private final NamespacedKey swordKey;
    private final Map<UUID,Long> cooldowns=new HashMap<>();
    private FileConfiguration config;
    private File configFile;

    public HeartSwordItem(JavaPlugin plugin){
        this.plugin=plugin;
        swordKey=new NamespacedKey(plugin,"heart_sword");
    }

    public void load(){
        configFile=new File(plugin.getDataFolder(),"items/n1mc/config.yml");
        File oldFile=new File(plugin.getDataFolder(),"items/heart_sword/config.yml");
        if(!configFile.exists()&&oldFile.isFile()){
            configFile.getParentFile().mkdirs();
            try{Files.copy(oldFile.toPath(),configFile.toPath(),StandardCopyOption.REPLACE_EXISTING);plugin.getLogger().info("Przeniesiono konfigurację miecza do items/n1mc.");}
            catch(IOException ex){throw new IllegalStateException("Nie można przenieść starej konfiguracji miecza.",ex);}
        }
        boolean firstRun=!configFile.exists();
        if(firstRun)plugin.saveResource("items/n1mc/config.yml",false);
        config=YamlConfiguration.loadConfiguration(configFile);
        if(firstRun){
            ConfigurationSection legacy=plugin.getConfig().getConfigurationSection("heart-sword");
            if(legacy!=null){for(String key:legacy.getKeys(true))if(!key.equals("hearts-cost")&&!key.equals("money-cost")&&legacy.get(key)!=null&&!(legacy.get(key) instanceof ConfigurationSection))config.set(key,legacy.get(key));save(configFile,config);plugin.getLogger().info("Przeniesiono konfigurację Miecza N1 do items/n1mc/config.yml.");}
        }
    }

    private void save(File file,FileConfiguration data){try{data.save(file);}catch(IOException ex){throw new IllegalStateException("Nie można zapisać konfiguracji miecza: "+file,ex);}}

    public boolean enabled(){return config.getBoolean("enabled",true);}
    public FileConfiguration config(){return config;}
    public NamespacedKey key(){return swordKey;}
    public boolean setTemplate(ItemStack template){
        if(template==null||template.getType().isAir()||template.getAmount()!=1)return false;
        config.set("template",template.clone());
        save(configFile,config);
        return true;
    }
    private int strengthLevel(){return Math.min(256,Math.max(1,config.getInt("strength-level",2)));}
    private int durationSeconds(){return Math.min(Integer.MAX_VALUE/20,Math.max(1,config.getInt("strength-duration-seconds",5)));}
    private int cooldownSeconds(){return Math.max(0,config.getInt("cooldown-seconds",120));}
    private double chance(){double value=config.getDouble("strength-chance",0.15);return Double.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private Material material(){Material value=Material.matchMaterial(config.getString("material","NETHERITE_SWORD"));return value!=null&&value.isItem()?value:Material.NETHERITE_SWORD;}
    public ItemStack shopItem(Player viewer){return create();}
    public ItemStack purchasedItem(){return create();}
    private ItemStack create(){
        ItemStack template=config.getItemStack("template");
        ItemStack item=template==null?new ItemStack(material()):template.clone();
        item.setAmount(1);
        ItemMeta meta=item.getItemMeta();
        if(template==null){
            meta.setLore(List.of(color("&r"),SpawnerItemModule.styledColor("&fPODCZAS WALKI ISTNIEJE SZANSA NA"),
                    SpawnerItemModule.styledColor("&fNADANIE CI EFEKTU &cSIŁY "+strengthLevel()+" &fNA &9"+durationSeconds()+" SEKUND")));
        }
        meta.setDisplayName(color("&9&lMiecz N1"));
        meta.getPersistentDataContainer().set(swordKey,PersistentDataType.BYTE,(byte)1);
        meta.removeEnchant(Enchantment.SHARPNESS);meta.removeEnchant(Enchantment.FIRE_ASPECT);meta.removeEnchant(Enchantment.UNBREAKING);
        meta.addEnchant(Enchantment.SHARPNESS,6,true);meta.addEnchant(Enchantment.FIRE_ASPECT,2,true);meta.addEnchant(Enchantment.UNBREAKING,3,true);
        double damage=configuredAttribute("attack-damage",9.0,2048.0);double speed=configuredAttribute("attack-speed",1.6,1024.0);
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,new AttributeModifier(new NamespacedKey(plugin,"heart_sword_damage"),damage,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,new AttributeModifier(new NamespacedKey(plugin,"heart_sword_speed"),speed,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);return item;
    }

    public void purchase(Player player){
        int empty=player.getInventory().firstEmpty();if(empty<0){player.sendMessage(color("&cZwolnij miejsce w ekwipunku, aby odebrać miecz."));return;}
        player.getInventory().setItem(empty,purchasedItem());
        player.sendMessage(color("&aOtrzymano &9Miecz N1 &aza darmo."));
    }

    public void onDamage(EntityDamageByEntityEvent event){
        if(!(event.getDamager() instanceof Player player))return;ItemStack weapon=player.getInventory().getItemInMainHand();
        if(weapon.getType().isAir()||!weapon.hasItemMeta()||!weapon.getItemMeta().getPersistentDataContainer().has(swordKey,PersistentDataType.BYTE))return;
        long now=System.currentTimeMillis();if(now<cooldowns.getOrDefault(player.getUniqueId(),0L)||Math.random()>=chance())return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,durationSeconds()*20,strengthLevel()-1,false,true,true));
        cooldowns.put(player.getUniqueId(),now+cooldownSeconds()*1000L);
        player.sendMessage(color("&9Miecz N1 &auaktywnił efekt Siły "+strengthLevel()+" na "+durationSeconds()+" sekund!"));
    }

    public boolean ownsSword(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(swordKey,PersistentDataType.BYTE);}
    private String cooldownText(){int seconds=cooldownSeconds();return seconds%60==0?(seconds/60)+" minut"+(seconds/60==1?"ę":"y"):seconds+" sekund";}
    private double configuredAttribute(String path,double fallback,double max){double value=config.getDouble(path,fallback);return Double.isFinite(value)?Math.max(0,Math.min(max,value)):fallback;}
    private static String color(String text){return ChatColor.translateAlternateColorCodes('&',text);}
}
