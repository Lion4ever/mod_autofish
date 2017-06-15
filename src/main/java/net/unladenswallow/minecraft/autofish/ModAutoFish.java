package net.unladenswallow.minecraft.autofish;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ModAutoFish.MODID, useMetadata = true, acceptedMinecraftVersions="[1.11,1.12)", acceptableRemoteVersions="[1.11,1.12)",
guiFactory = "net.unladenswallow.minecraft.autofish.AutoFishGuiFactory")
public class ModAutoFish {
    public static final String MODID = "mod_autofish";
    
    public static Configuration configFile;
    public static boolean config_autofish_enable;
    public static final boolean CONFIG_DEFAULT_AUTOFISH_ENABLE = true;
    public static boolean config_autofish_multirod;
    public static final boolean CONFIG_DEFAULT_AUTOFISH_MULTIROD = false;
    public static boolean config_autofish_preventBreak;
    public static final boolean CONFIG_DEFAULT_AUTOFISH_PREVENTBREAK = false;
    public static boolean config_autofish_drop_junk;
    public static final boolean CONFIG_DEFAULT_AUTOFISH_DROP_JUNK = false;
    public static String[] config_autofish_junk;
    public static final String[] CONFIG_DEFAULT_AUTOFISH_JUNK = new String[] { "minecraft:bowl", "minecraft:leather",
			"minecraft:leather_boots", "minecraft:rotten_flesh", "minecraft:stick",
			"minecraft:potion", "minecraft:bone", "minecraft:tripwire_hook" };
    
    @SidedProxy(clientSide="net.unladenswallow.minecraft.autofish.ClientProxy", serverSide="net.unladenswallow.minecraft.autofish.ServerProxy")
    public static CommonProxy proxy;
    
    public static AutoFishEventHandler eventHandler = new AutoFishEventHandler();
    
    public static ModAutoFish instance = new ModAutoFish();
            
    @EventHandler
    public void preInit(FMLPreInitializationEvent preInitEvent) {
        ModAutoFish.proxy.preInit(preInitEvent);
        configFile = new Configuration(preInitEvent.getSuggestedConfigurationFile());
        syncConfig();
    }
    
    @EventHandler
    public void init (FMLInitializationEvent event) {
        AutoFishLogger.info("Initializing " + ModAutoFish.MODID);
        ModAutoFish.proxy.init(event);
    }

    public static void syncConfig() {
        config_autofish_enable = configFile.getBoolean("Enable AutoFish", Configuration.CATEGORY_GENERAL, CONFIG_DEFAULT_AUTOFISH_ENABLE, "Automatically reel in and re-cast when a fish nibbles the hook.");
        config_autofish_multirod = configFile.getBoolean("Enable MultiRod", Configuration.CATEGORY_GENERAL, CONFIG_DEFAULT_AUTOFISH_MULTIROD, "Automatically switch to a new fishing rod when the current rod breaks, if one is available in the hotbar.");
        config_autofish_preventBreak = configFile.getBoolean("Enable Break Protection", Configuration.CATEGORY_GENERAL, CONFIG_DEFAULT_AUTOFISH_PREVENTBREAK, "Stop fishing or switch to a new rod before the current rod breaks.");
        config_autofish_drop_junk = configFile.getBoolean("Enable Junk Drop", Configuration.CATEGORY_GENERAL, CONFIG_DEFAULT_AUTOFISH_DROP_JUNK, "Look through the inventory and drop unimportant items occasionally.");
        config_autofish_junk = configFile.getStringList("Junk Item List", Configuration.CATEGORY_GENERAL, CONFIG_DEFAULT_AUTOFISH_JUNK, "Choose what items should be sorted out.");
        
        if (configFile.hasChanged()) {
            configFile.save();
        }
    }

}
