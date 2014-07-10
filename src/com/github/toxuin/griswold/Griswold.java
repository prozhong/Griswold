package com.github.toxuin.griswold;

import com.github.toxuin.griswold.npcs.GriswoldNPC;
import com.github.toxuin.griswold.professions.Repairer;
import com.github.toxuin.griswold.util.ClassProxy;
import com.github.toxuin.griswold.util.Metrics;
import net.milkbowl.vault.economy.Economy;

import net.minecraft.server.v1_7_R3.EntitySnowman;
import net.minecraft.server.v1_7_R3.World;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.toxuin.griswold.util.Metrics.Graph;
import com.github.toxuin.griswold.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class Griswold extends JavaPlugin implements Listener {
	public static File directory;
	public static boolean debug = false;
	public static int timeout = 5000;
    public static Logger log;
	
	private static FileConfiguration config = null;
	private static File configFile = null;
    private Map<GriswoldNPC, Pair> npcChunks = new HashMap<GriswoldNPC, Pair>();
    //Interactor interactor;

    public static Economy economy = null;
    
    public static double version;
    public static String apiVersion;
    static String lang = "en_US";
    public static boolean namesVisible = true;

    public void onEnable() {
        log = this.getLogger();
		directory = this.getDataFolder();
		PluginDescriptionFile pdfFile = this.getDescription();
		version = Double.parseDouble(pdfFile.getVersion());
        apiVersion = this.getServer().getClass().getPackage().getName().substring(
                     this.getServer().getClass().getPackage().getName().lastIndexOf('.') + 1);

        // CHECK IF USING THE WRONG PLUGIN VERSION
        if (ClassProxy.getClass("entity.CraftVillager") == null) {
            log.severe("PLUGIN NOT LOADED!!!");
            log.severe("ERROR: YOU ARE USING THE WRONG VERSION OF THIS PLUGIN.");
            log.severe("GO TO http://dev.bukkit.org/bukkit-plugins/griswold/");
            log.severe("YOUR SERVER VERSION IS " + this.getServer().getBukkitVersion());
            log.severe("PLUGIN NOT LOADED!!!");
            this.getPluginLoader().disablePlugin(this);
            return;
        }

        this.getServer().getPluginManager().registerEvents(new EventListener(this), this);
        getCommand("blacksmith").setExecutor(new CommandListener(this));

		this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Starter(), 20);

        //interactor = new Interactor();

        try {
		    Metrics metrics = new Metrics(this);
		    Graph graph = metrics.createGraph("Number of NPCs");
		    graph.addPlotter(new Metrics.Plotter("Total") {
		        @Override
		        public int getValue() {
		            return npcChunks.keySet().size();
		        }
		    });
		    metrics.start();
		} catch (IOException e) {
		    if (debug) log.info("ERROR: failed to submit stats to MCStats");
		}
		
		log.info("Enabled! Version: " + version);
	}

	public void onDisable() {
        //interactor = null;
        despawnAll();
		log.info("Disabled.");
	}

	public void reloadPlugin() {
		despawnAll();
		readConfig();
	}
	
	public void createRepairman(String name, Location loc) {
        createRepairman(name, loc, "all", "1");
	}
	
	public void createRepairman(String name, Location loc, String type, String cost) {
		boolean found = false;
        Set<GriswoldNPC> npcs = npcChunks.keySet();
		for (GriswoldNPC rep : npcs) {
			if (rep.name.equalsIgnoreCase(name)) found = true;
		}
		if (found) {
			log.info(String.format(Lang.repairman_exists, name));
			return;
		}
			
		config.set("repairmen."+name+".world", loc.getWorld().getName());
		config.set("repairmen."+name+".X", loc.getX());
		config.set("repairmen."+name+".Y", loc.getY());
		config.set("repairmen."+name+".Z", loc.getZ());
        config.set("repairmen."+name+".sound", "mob.villager.haggle");
		config.set("repairmen."+name+".type", type);
		config.set("repairmen."+name+".cost", Double.parseDouble(cost));
    	
    	try {
    		config.save(configFile);
    	} catch (Exception e) {
    		log.info(Lang.error_config);
    		e.printStackTrace();
    	}


        //ClassProxy.listMethods(EntitySnowman.class);

        // SAFE TO USE SO FAR:
        //    IronGolem, Snowman, Villager, Whitch
    	GriswoldNPC npc = new GriswoldNPC(name, loc, new Repairer(), Witch.class);
        registerRepairman(npc);
	}
	
	public void removeRepairman(String name) {
		if (config.isConfigurationSection("repairmen."+name)){
			config.set("repairmen."+name, null);
			try {
				config.save(configFile); 
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
		} else {
			log.info(Lang.error_remove);
			return;
		}
		reloadPlugin();
	}
	
	public void listRepairmen(CommandSender sender) {
		String result = "";
        Set<GriswoldNPC> npcs = npcChunks.keySet();
		for (GriswoldNPC rep : npcs) {
			result = result + rep.name + ", ";
		}
		if (!result.equals("")) {
			sender.sendMessage(ChatColor.GREEN+Lang.repairman_list);
			sender.sendMessage(result);
		}
	}
	
	public void despawnAll() {
        Set<GriswoldNPC> npcs = npcChunks.keySet();
		for (GriswoldNPC rep : npcs) {
			rep.remove();
		}
        npcChunks.clear();
	}

    public void toggleNames() {
        namesVisible = !namesVisible;
        Set<GriswoldNPC> npcs = npcChunks.keySet();
        for (GriswoldNPC rep : npcs) {
            rep.setNameVisible(namesVisible);
        }

        config.set("ShowNames", namesVisible);
        try {
            config.save(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	public void registerRepairman(GriswoldNPC squidward) {
        if (!npcChunks.containsKey(squidward)) npcChunks.put(squidward, new Pair(squidward.loc.getChunk().getX(), squidward.loc.getChunk().getZ()));
	}

    public Map<GriswoldNPC, Pair> getNpcChunks() {
        return this.npcChunks;
    }
	
	private void readConfig() {

    	Lang.createLangFile();
		
		configFile = new File(directory, "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        
        npcChunks.clear();
        
        if (configFile.exists()) {
        	debug = config.getBoolean("Debug");
        	timeout = config.getInt("Timeout");
        	lang = config.getString("Language");
            namesVisible = config.getBoolean("ShowNames");
        	
        	if (Double.parseDouble(config.getString("Version")) < version) {
        		updateConfig(config.getString("Version"));
        	} else if (Double.parseDouble(config.getString("Version")) == 0) {
        		log.info("ERROR! ERROR! ERROR! ERROR! ERROR! ERROR! ERROR!");
        		log.info("ERROR! YOUR CONFIG FILE IS CORRUPT!!! ERROR!");
        		log.info("ERROR! ERROR! ERROR! ERROR! ERROR! ERROR! ERROR!");
        	}

        	Lang.checkLangVersion(lang);
			Lang.init();

	        /*Interactor.basicArmorPrice = config.getDouble("BasicArmorPrice");
	        Interactor.basicToolsPrice = config.getDouble("BasicToolPrice");
	        Interactor.enchantmentPrice = config.getDouble("BasicEnchantmentPrice");
	        Interactor.addEnchantmentPrice = config.getDouble("PriceToAddEnchantment");
	        Interactor.clearEnchantments = config.getBoolean("ClearOldEnchantments");
	        Interactor.maxEnchantBonus = config.getInt("EnchantmentBonus");

	        Interactor.enableEnchants = config.getBoolean("UseEnchantmentSystem");
             */
	        if (config.isConfigurationSection("repairmen")) {
        		Set<String> repairmen = config.getConfigurationSection("repairmen").getKeys(false);
	        	for (String repairman : repairmen) {

	        		Location loc = new Location(this.getServer().getWorld(config.getString("repairmen."+repairman+".world")),
	        									config.getDouble("repairmen."+repairman+".X"),
	        									config.getDouble("repairmen."+repairman+".Y"),
	        									config.getDouble("repairmen."+repairman+".Z"));
                    String sound = config.getString("repairmen." + repairman + ".sound");
	        		//squidward.type = config.getString("repairmen."+repairman+".type");
	        		//squidward.cost = config.getDouble("repairmen."+repairman+".cost");

                    GriswoldNPC squidward = new GriswoldNPC(repairman, loc, new Repairer(), Villager.class);
                    squidward.setSound(sound);

	        		registerRepairman(squidward);
	        	}
        	}
	        log.info(Lang.config_loaded);

        	if (debug) {
        		log.info(String.format(Lang.debug_loaded, npcChunks.keySet().size()));
        	}
        } else {
        	config.set("Timeout", 5000);
        	config.set("Language", "en_US");
            config.set("ShowNames", true);
        	config.set("BasicArmorPrice", 10.0);
        	config.set("BasicToolPrice", 10.0);
        	config.set("BasicEnchantmentPrice", 30.0);
	        config.set("UseEnchantmentSystem", true);
        	config.set("PriceToAddEnchantment", 50.0);
        	config.set("ClearOldEnchantments", true);
        	config.set("EnchantmentBonus", 5);
        	config.set("Debug", false);
        	config.set("Version", this.getDescription().getVersion());
        	try {
        		config.save(configFile);
        		log.info(Lang.default_config);
        	} catch (Exception e) {
        		log.info(Lang.error_create_config);
        		e.printStackTrace();
        	}
        }
	}
	
	private void updateConfig(String oldVersion) {
		if (Double.parseDouble(oldVersion) < 0.05d) {
			// ADDED IN 0.05
			log.info("UPDATING CONFIG "+config.getName()+" FROM VERSION OLDER THAN 0.5");

			config.set("PriceToAddEnchantment", 50.0);
	        config.set("ClearOldEnchantments", true);
	        config.set("EnchantmentBonus", 5);

	        config.set("Version", 0.05d);
	        try {
	            config.save(configFile);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
		}

		if (Double.parseDouble(oldVersion) == 0.05d) {
			log.info("UPDATING CONFIG "+config.getName()+" FROM VERSION 0.5");
			// ADDED IN 0.051
			config.set("UseEnchantmentSystem", true);

			config.set("Version", 0.051d);
			try {
				config.save(configFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

        if (Double.parseDouble(oldVersion) == 0.06d || Double.parseDouble(oldVersion) == 0.051d) {
            log.info("UPDATING CONFIG "+config.getName()+" FROM VERSION 0.51/0.6");
            // ADDED IN 0.07
            config.set("ShowNames", true);

            config.set("Version", 0.07d);
            try {
                config.save(configFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (Double.parseDouble(oldVersion) == 0.07d) {
            log.info("UPDATING CONFIG "+config.getName()+" FROM VERSION 0.7*");
            if (config.isConfigurationSection("repairmen")) {
                Set<String> repairmen = config.getConfigurationSection("repairmen").getKeys(false);
                for (String repairman : repairmen) {
                    if (config.getString("repairmen." + repairman + ".sound").equals("mob.villager.haggle")) {
                        config.set("repairmen." + repairman + ".sound", "VILLAGER_HAGGLE");
                    }
                }
            }
            config.set("Version", 0.08d);

            try {
                config.save(configFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}

    public GriswoldNPC getNPCByName(String name) {
        return null;
    }

    private class Starter implements Runnable {
		@Override
		public void run() {
			reloadPlugin();
			if (!setupEconomy()) log.info(Lang.economy_not_found);
		}
		
	}

    private boolean setupEconomy() {
    	if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        return (economy != null);
    }
}
