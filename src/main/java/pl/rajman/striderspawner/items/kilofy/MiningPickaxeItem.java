package pl.rajman.striderspawner.items.kilofy;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import pl.rajman.striderspawner.items.spawners.SpawnerItemModule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Built-in mining pickaxe that can bonus-drop stone resources and a configured heart item. */
public final class MiningPickaxeItem {
    private final JavaPlugin plugin;
    private final NamespacedKey key,levelKey,usesKey,areaKey;
    private FileConfiguration config;

    public MiningPickaxeItem(JavaPlugin plugin){this.plugin=plugin;key=new NamespacedKey(plugin,"mining_pickaxe");levelKey=new NamespacedKey(plugin,"mining_pickaxe_level");usesKey=new NamespacedKey(plugin,"mining_pickaxe_uses");areaKey=new NamespacedKey(plugin,"mining_pickaxe_area");}

    public void load(){
        File file=new File(plugin.getDataFolder(),"items/kilofy/config.yml");
        File oldFile=new File(plugin.getDataFolder(),"items/mining_pickaxe/config.yml");
        if(!file.exists()&&oldFile.isFile()){
            file.getParentFile().mkdirs();
            try{Files.copy(oldFile.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);plugin.getLogger().info("Przeniesiono konfigurację kilofa do items/kilofy.");}
            catch(IOException ex){throw new IllegalStateException("Nie można przenieść starej konfiguracji kilofa.",ex);}
        }
        if(!file.exists())plugin.saveResource("items/kilofy/config.yml",false);
        config=YamlConfiguration.loadConfiguration(file);
        try(InputStream stream=plugin.getResource("items/kilofy/config.yml")){
            if(stream!=null){
                YamlConfiguration defaults=YamlConfiguration.loadConfiguration(new InputStreamReader(stream,StandardCharsets.UTF_8));
                config.setDefaults(defaults);config.options().copyDefaults(true);config.save(file);
            }
        }catch(IOException ex){throw new IllegalStateException("Nie można uzupełnić konfiguracji kilofa: "+file,ex);}
    }

    public boolean enabled(){return config.getBoolean("enabled",true);}
    public List<Integer> levels(){
        ConfigurationSection section=config.getConfigurationSection("levels");if(section==null)return List.of();
        return section.getKeys(false).stream().map(value->{try{return Integer.parseInt(value);}catch(NumberFormatException ex){return -1;}})
                .filter(level->level>=1&&level<=1000&&config.getBoolean("levels."+level+".enabled",true)).sorted().toList();
    }
    public ItemStack create(){return create(1);}
    public ItemStack create(int level){
        ConfigurationSection levelConfig=config.getConfigurationSection("levels."+level);
        if(levelConfig==null||!levelConfig.getBoolean("enabled",true))return null;
        int maxArea=Math.max(1,Math.min(15,config.getInt("max-area-size",15)));
        int areaSize=Math.max(1,Math.min(maxArea,levelConfig.getInt("area-size",level)));
        int uses=Math.max(0,levelConfig.getInt("uses",500));
        ItemStack item=new ItemStack(Material.NETHERITE_PICKAXE);ItemMeta meta=item.getItemMeta();
        String defaultName=level==1?"Kilof N1":"Kilof Milionera "+level;
        String titleColor=levelConfig.getString("title-color",level==1?"&9":"&a");
        meta.setDisplayName(color(titleColor+"&l"+levelConfig.getString("name",defaultName)));
        List<String> lore=new java.util.ArrayList<>();lore.add(color("&r"));
        if(areaSize>1){
            lore.add(SpawnerItemModule.styledColor("&fPO ZNISZCZENIU BLOKU NISZCZYSZ"));
            lore.add(SpawnerItemModule.styledColor("&fWOKÓŁ BLOKU OBSZAR "+levelConfig.getString("area-color","&a")+areaSize+"X"+areaSize));
        }else{
            lore.add(SpawnerItemModule.styledColor("&fPODCZAS KOPANIA MASZ SZANSĘ NA"));
            lore.add(SpawnerItemModule.styledColor("&fDROPIENIE Z KAMIENIA &aSUROWCÓW"));
            lore.add(SpawnerItemModule.styledColor("&fORAZ DODATKOWO &cSERCA"));
        }
        if(uses>0&&level!=1){lore.add(color("&r"));lore.add(color("&6✶ &fIlość użyć: &e"+uses));}
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(levelKey,PersistentDataType.INTEGER,level);
        meta.getPersistentDataContainer().set(areaKey,PersistentDataType.INTEGER,areaSize);
        meta.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,uses>0?uses:-1);
        addEnchant(meta,Enchantment.FORTUNE,levelConfig.getInt("fortune",5));
        addEnchant(meta,Enchantment.EFFICIENCY,levelConfig.getInt("efficiency",10));
        addEnchant(meta,Enchantment.UNBREAKING,levelConfig.getInt("unbreaking",5));
        double miningEfficiency=levelConfig.getDouble("mining-efficiency",101.0);
        if(Double.isFinite(miningEfficiency)&&miningEfficiency!=0)meta.addAttributeModifier(Attribute.BLOCK_BREAK_SPEED,new AttributeModifier(new NamespacedKey(plugin,"mining_pickaxe_efficiency_"+level),miningEfficiency,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);return item;
    }

    public boolean isPickaxe(ItemStack item){return item!=null&&item.getType()==Material.NETHERITE_PICKAXE&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.BYTE);}
    public int level(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey,PersistentDataType.INTEGER,1);}
    public int areaSize(ItemStack item){
        int maxArea=Math.max(1,Math.min(15,config.getInt("max-area-size",15)));
        int fallback=Math.max(1,Math.min(maxArea,level(item)));
        return Math.max(1,Math.min(maxArea,item.getItemMeta().getPersistentDataContainer().getOrDefault(areaKey,PersistentDataType.INTEGER,fallback)));
    }
    public int uses(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(usesKey,PersistentDataType.INTEGER,-1);}
    public void setUses(ItemStack item,int uses){
        ItemMeta meta=item.getItemMeta();meta.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,Math.max(0,uses));
        int itemLevel=meta.getPersistentDataContainer().getOrDefault(levelKey,PersistentDataType.INTEGER,1);
        List<String> lore=meta.getLore();if(itemLevel!=1&&lore!=null&&!lore.isEmpty())lore.set(lore.size()-1,color("&6✶ &fIlość użyć: &e"+Math.max(0,uses)));
        if(lore!=null)meta.setLore(lore);item.setItemMeta(meta);
    }
    public boolean dropsResource(){return ThreadLocalRandom.current().nextDouble()<chance("resource-drop-chance",0.10);}
    public boolean dropsHeart(){return ThreadLocalRandom.current().nextDouble()<chance("heart-drop-chance",0.01);}
    public ItemStack heartDropItem(){
        ItemStack item=config.getItemStack("heart-drop-item");
        return item==null?null:item.clone();
    }
    public boolean setHeartDropItem(ItemStack item){
        if(item==null||item.getType().isAir()||item.getAmount()!=1)return false;
        config.set("heart-drop-item",item.clone());
        try{config.save(new File(plugin.getDataFolder(),"items/kilofy/config.yml"));return true;}
        catch(IOException ex){plugin.getLogger().severe("Nie można zapisać itemu dropiącego jako serce: "+ex.getMessage());return false;}
    }
    public static boolean canDropStoneRewards(int level,int areaSize){return level==1&&areaSize<=1;}
    public Material randomResource(){
        List<Material> available=config.getStringList("resource-drops").stream().map(Material::matchMaterial)
                .filter(material->material!=null&&material.isItem()).toList();
        if(available.isEmpty())return Material.COAL;
        return available.get(ThreadLocalRandom.current().nextInt(available.size()));
    }
    private double chance(String path,double fallback){double value=config.getDouble(path,fallback);return Double.isFinite(value)?Math.max(0,Math.min(1,value)):fallback;}
    private static void addEnchant(ItemMeta meta,Enchantment enchantment,int level){if(level>0)meta.addEnchant(enchantment,Math.min(255,level),true);}
    private static String color(String value){return ChatColor.translateAlternateColorCodes('&',value);}
}
