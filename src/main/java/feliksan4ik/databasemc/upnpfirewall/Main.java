package feliksan4ik.databasemc.upnpfirewall;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;

public final class Main extends JavaPlugin implements Listener {

    private FirewallManager firewallManager;
    private PortManager portManager;
    private PortIPManager portIPManager;
    public FileConfiguration config;
    private FileConfiguration langConfig;
    private String currentLanguage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        setupLanguageFiles();

        firewallManager = new FirewallManager(this);
        portManager = new PortManager(this);
        portIPManager = new PortIPManager(this);

        firewallManager.loadFirewallRules();
        portManager.loadPortSettings();
        portIPManager.loadPortIPRules();

        getServer().getPluginManager().registerEvents(firewallManager, this);
        getServer().getPluginManager().registerEvents(portIPManager, this);

        if (config.getBoolean("auto-port-management.enabled", true)) {
            startAutoPortManagement();
        }

        getLogger().info(getTranslation("plugin.enabled"));
        getLogger().info(getTranslation("auto.management.status") +
                (config.getBoolean("auto-port-management.enabled") ?
                        getTranslation("enabled") : getTranslation("disabled")));
    }

    @Override
    public void onDisable() {
        if (config.getBoolean("close-ports-on-disable", true)) {
            portManager.closeAllPorts();
            portIPManager.closeAllIPPorts();
        }

        getLogger().info(getTranslation("plugin.disabled"));
    }

    private void setupLanguageFiles() {
        currentLanguage = config.getString("language", "en");
        File langFile = new File(getDataFolder(), "lang/" + currentLanguage + ".yml");

        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            saveResource("lang/" + currentLanguage + ".yml", false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        createDefaultLanguageFiles();
    }

    private void createDefaultLanguageFiles() {
        String[] languages = {"en", "ru"};
        for (String lang : languages) {
            File langFile = new File(getDataFolder(), "lang/" + lang + ".yml");
            if (!langFile.exists()) {
                saveResource("lang/" + lang + ".yml", false);
            }
        }
    }

    public String getTranslation(String path) {
        String translation = langConfig.getString(path);
        if (translation == null) {
            getLogger().warning("Missing translation: " + path);
            return path;
        }
        return ChatColor.translateAlternateColorCodes('&', translation);
    }

    public String getTranslation(String path, Object... args) {
        String translation = getTranslation(path);
        return String.format(translation, args);
    }

    private void startAutoPortManagement() {
        new BukkitRunnable() {
            @Override
            public void run() {
                portManager.manageServerPorts();
                portIPManager.manageIPPorts();
            }
        }.runTaskTimer(this, 0L, 6000L); // Запуск каждые 5 минут
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("firewall")) {
            return false;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                sendStatus(sender);
                break;

            case "addrule":
                if (args.length >= 4) {
                    firewallManager.addRule(sender, args[1], args[2], args[3]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.addrule"));
                }
                break;

            case "removerule":
                if (args.length >= 2) {
                    firewallManager.removeRule(sender, args[1]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.removerule"));
                }
                break;

            case "listrules":
                firewallManager.listRules(sender);
                break;

            case "openport":
                if (args.length >= 4) {
                    portManager.openPort(sender, args[1], args[2], args[3]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.openport"));
                }
                break;

            case "closeport":
                if (args.length >= 2) {
                    portManager.closePort(sender, args[1]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.closeport"));
                }
                break;

            case "listports":
                portManager.listPorts(sender);
                break;

            // Новые команды для управления портами по IP
            case "openportip":
                if (args.length >= 4) {
                    portIPManager.openPortForIP(sender, args[1], args[2], args[3]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.openportip"));
                }
                break;

            case "closeportip":
                if (args.length >= 3) {
                    portIPManager.closePortForIP(sender, args[1], args[2]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.closeportip"));
                }
                break;

            case "blockportip":
                if (args.length >= 3) {
                    portIPManager.blockPortForIP(sender, args[1], args[2]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.blockportip"));
                }
                break;

            case "unblockportip":
                if (args.length >= 3) {
                    portIPManager.unblockPortForIP(sender, args[1], args[2]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.unblockportip"));
                }
                break;

            case "listportsip":
                portIPManager.listPortsIP(sender);
                break;

            case "clearportsip":
                portIPManager.clearAllPortsIP(sender);
                break;

            case "reload":
                reloadConfig();
                config = getConfig();
                setupLanguageFiles();
                firewallManager.loadFirewallRules();
                portManager.loadPortSettings();
                portIPManager.loadPortIPRules();
                sender.sendMessage(getTranslation("config.reloaded"));
                break;

            case "setlang":
                if (args.length >= 2) {
                    setLanguage(sender, args[1]);
                } else {
                    sender.sendMessage(getTranslation("commands.usage.setlang"));
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void setLanguage(CommandSender sender, String language) {
        File newLangFile = new File(getDataFolder(), "lang/" + language + ".yml");
        if (!newLangFile.exists()) {
            sender.sendMessage(getTranslation("language.not_found", language));
            return;
        }

        config.set("language", language);
        saveConfig();
        setupLanguageFiles();

        sender.sendMessage(getTranslation("language.changed", language));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(getTranslation("help.header"));
        for (int i = 1; i <= 18; i++) {
            String helpLine = getTranslation("help.line" + i);
            if (!helpLine.equals("help.line" + i)) {
                sender.sendMessage(helpLine);
            }
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(getTranslation("status.header"));
        sender.sendMessage(getTranslation("status.firewall",
                firewallManager.isEnabled() ? getTranslation("enabled") : getTranslation("disabled")));
        sender.sendMessage(getTranslation("status.rules_count", firewallManager.getRuleCount()));
        sender.sendMessage(getTranslation("status.ports_count", portManager.getOpenPortCount()));
        sender.sendMessage(getTranslation("status.ports_ip_count", portIPManager.getPortIPCount()));
        sender.sendMessage(getTranslation("status.auto_management",
                config.getBoolean("auto-port-management.enabled") ? getTranslation("enabled") : getTranslation("disabled")));
        sender.sendMessage(getTranslation("status.current_language", currentLanguage));
    }

}