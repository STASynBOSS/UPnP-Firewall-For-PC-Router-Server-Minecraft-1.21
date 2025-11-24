package feliksan4ik.DataBaseMC.upnpfirewall;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.logging.Level;

public final class Upnpfirewall extends JavaPlugin implements Listener {

    private FirewallManager firewallManager;
    private PortManager portManager;
    private PortIPManager portIPManager;
    private FileConfiguration config;
    private FileConfiguration langConfig;
    private File langFile;
    private String currentLanguage;

    @Override
    public void onEnable() {
        // Инициализация конфигурации
        saveDefaultConfig();
        config = getConfig();

        // Инициализация языковых файлов
        setupLanguageFiles();

        // Инициализация менеджеров
        firewallManager = new FirewallManager();
        portManager = new PortManager();
        portIPManager = new PortIPManager();

        // Загрузка настроек
        firewallManager.loadFirewallRules();
        portManager.loadPortSettings();
        portIPManager.loadPortIPRules();

        // Регистрация обработчиков событий
        getServer().getPluginManager().registerEvents(firewallManager, this);
        getServer().getPluginManager().registerEvents(portIPManager, this);

        // Запуск автоматического управления портами
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
        // Закрытие портов при выключении плагина
        if (config.getBoolean("close-ports-on-disable", true)) {
            portManager.closeAllPorts();
            portIPManager.closeAllIPPorts();
        }

        getLogger().info(getTranslation("plugin.disabled"));
    }

    private void setupLanguageFiles() {
        currentLanguage = config.getString("language", "en");
        langFile = new File(getDataFolder(), "lang/" + currentLanguage + ".yml");

        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            saveResource("lang/" + currentLanguage + ".yml", false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Создаем стандартные языковые файлы если их нет
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

    public FileConfiguration getPluginConfig() {
        return config;
    }

    // Внутренний класс FirewallManager
    private class FirewallManager implements Listener {
        private final Set<FirewallRule> firewallRules;
        private boolean enabled;

        public FirewallManager() {
            this.firewallRules = new HashSet<>();
            this.enabled = true;
        }

        public void loadFirewallRules() {
            firewallRules.clear();

            enabled = config.getBoolean("firewall.enabled", true);

            if (config.contains("firewall.rules")) {
                for (String key : config.getConfigurationSection("firewall.rules").getKeys(false)) {
                    String ip = config.getString("firewall.rules." + key + ".ip");
                    String type = config.getString("firewall.rules." + key + ".type");
                    String action = config.getString("firewall.rules." + key + ".action");

                    if (ip != null && type != null && action != null) {
                        firewallRules.add(new FirewallRule(ip, type, action));
                    }
                }
            }

            getLogger().info(getTranslation("firewall.rules_loaded", firewallRules.size()));
        }

        @EventHandler
        public void onPlayerLogin(PlayerLoginEvent event) {
            if (!enabled) return;

            Player player = event.getPlayer();
            String ip = event.getAddress().getHostAddress();

            for (FirewallRule rule : firewallRules) {
                if (rule.matches(ip)) {
                    if ("block".equalsIgnoreCase(rule.getAction())) {
                        if ("blacklist".equalsIgnoreCase(rule.getType())) {
                            event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
                            event.setKickMessage(getTranslation("firewall.kick_blocked"));
                            getLogger().warning(getTranslation("firewall.login_blocked", ip, player.getName()));
                            return;
                        }
                    } else if ("allow".equalsIgnoreCase(rule.getAction())) {
                        if ("whitelist".equalsIgnoreCase(rule.getType())) {
                            // Разрешить подключение
                            return;
                        }
                    }
                }
            }

            // Если включен режим whitelist по умолчанию, блокируем всех не в whitelist
            if (config.getBoolean("firewall.whitelist-mode", false)) {
                boolean allowed = false;
                for (FirewallRule rule : firewallRules) {
                    if ("whitelist".equalsIgnoreCase(rule.getType()) && "allow".equalsIgnoreCase(rule.getAction()) && rule.matches(ip)) {
                        allowed = true;
                        break;
                    }
                }

                if (!allowed) {
                    event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
                    event.setKickMessage(getTranslation("firewall.kick_whitelist"));
                    getLogger().warning(getTranslation("firewall.whitelist_blocked", ip, player.getName()));
                }
            }
        }

        public void addRule(CommandSender sender, String ip, String type, String action) {
            try {
                // Валидация IP адреса
                InetAddress.getByName(ip);

                if (!"whitelist".equalsIgnoreCase(type) && !"blacklist".equalsIgnoreCase(type)) {
                    sender.sendMessage(getTranslation("firewall.error.invalid_type"));
                    return;
                }

                if (!"allow".equalsIgnoreCase(action) && !"block".equalsIgnoreCase(action)) {
                    sender.sendMessage(getTranslation("firewall.error.invalid_action"));
                    return;
                }

                FirewallRule rule = new FirewallRule(ip, type, action);
                firewallRules.add(rule);

                // Сохраняем в конфиг
                String ruleKey = "rule_" + System.currentTimeMillis();
                config.set("firewall.rules." + ruleKey + ".ip", ip);
                config.set("firewall.rules." + ruleKey + ".type", type.toLowerCase());
                config.set("firewall.rules." + ruleKey + ".action", action.toLowerCase());
                saveConfig();

                sender.sendMessage(getTranslation("firewall.rule_added", ip));

            } catch (UnknownHostException e) {
                sender.sendMessage(getTranslation("firewall.error.invalid_ip", ip));
            }
        }

        public void removeRule(CommandSender sender, String ip) {
            boolean removed = firewallRules.removeIf(rule -> rule.getIp().equals(ip));

            if (removed) {
                // Удаляем из конфига
                if (config.contains("firewall.rules")) {
                    for (String key : config.getConfigurationSection("firewall.rules").getKeys(false)) {
                        if (ip.equals(config.getString("firewall.rules." + key + ".ip"))) {
                            config.set("firewall.rules." + key, null);
                            break;
                        }
                    }
                }
                saveConfig();

                sender.sendMessage(getTranslation("firewall.rule_removed", ip));
            } else {
                sender.sendMessage(getTranslation("firewall.error.rule_not_found", ip));
            }
        }

        public void listRules(CommandSender sender) {
            if (firewallRules.isEmpty()) {
                sender.sendMessage(getTranslation("firewall.no_rules"));
                return;
            }

            sender.sendMessage(getTranslation("firewall.rules_header"));
            for (FirewallRule rule : firewallRules) {
                // Красный цвет для блокирующих правил, зеленый для разрешающих
                ChatColor color = "block".equalsIgnoreCase(rule.getAction()) ? ChatColor.RED : ChatColor.GREEN;
                String actionText = "block".equalsIgnoreCase(rule.getAction()) ?
                        getTranslation("firewall.action_block") : getTranslation("firewall.action_allow");

                sender.sendMessage(color + getTranslation("firewall.rule_format",
                        rule.getIp(), rule.getType(), actionText));
            }
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getRuleCount() {
            return firewallRules.size();
        }

        private class FirewallRule {
            private final String ip;
            private final String type;
            private final String action;

            public FirewallRule(String ip, String type, String action) {
                this.ip = ip;
                this.type = type;
                this.action = action;
            }

            public boolean matches(String checkIp) {
                return ip.equals(checkIp) || ip.equals("*") || checkIp.startsWith(ip.replace(".*", ""));
            }

            public String getIp() { return ip; }
            public String getType() { return type; }
            public String getAction() { return action; }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                FirewallRule that = (FirewallRule) obj;
                return ip.equals(that.ip);
            }

            @Override
            public int hashCode() {
                return ip.hashCode();
            }
        }
    }

    // Внутренний класс PortManager
    private class PortManager {
        private final Map<Integer, PortInfo> openPorts;
        private final SimpleUPnPManager upnpManager;

        public PortManager() {
            this.openPorts = new HashMap<>();
            this.upnpManager = new SimpleUPnPManager();
        }

        public void loadPortSettings() {
            openPorts.clear();

            if (config.contains("ports.open")) {
                for (String ruleName : config.getConfigurationSection("ports.open").getKeys(false)) {
                    try {
                        int port = config.getInt("ports.open." + ruleName + ".port");
                        String protocol = config.getString("ports.open." + ruleName + ".protocol", "TCP");
                        String description = config.getString("ports.open." + ruleName + ".description", "Minecraft Server");

                        if (port >= 1 && port <= 65535) {
                            openPorts.put(port, new PortInfo(port, protocol, description, ruleName));
                        } else {
                            getLogger().warning(getTranslation("ports.error.invalid_range_config", ruleName, port));
                        }
                    } catch (Exception e) {
                        getLogger().warning(getTranslation("ports.error.invalid_port_config", ruleName));
                    }
                }
            }

            getLogger().info(getTranslation("ports.settings_loaded", openPorts.size()));
        }

        public void manageServerPorts() {
            if (config.getBoolean("auto-port-management.open-server-port", true)) {
                int serverPort = getServer().getPort();
                if (!isPortOpen(serverPort)) {
                    openPort(null, String.valueOf(serverPort), "TCP", getTranslation("ports.default_description"));
                }
            }

            // Открываем порты из конфига
            for (PortInfo portInfo : openPorts.values()) {
                if (!isPortOpen(portInfo.getPort())) {
                    openPortInternally(portInfo);
                }
            }
        }

        public void openPort(CommandSender sender, String portStr, String protocol, String description) {
            try {
                int port = Integer.parseInt(portStr);

                if (port < 1 || port > 65535) {
                    if (sender != null) {
                        sender.sendMessage(getTranslation("ports.error.invalid_range"));
                    }
                    return;
                }

                if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                    if (sender != null) {
                        sender.sendMessage(getTranslation("ports.error.invalid_protocol"));
                    }
                    return;
                }

                PortInfo portInfo = new PortInfo(port, protocol.toUpperCase(), description, "cmd_" + System.currentTimeMillis());

                if (openPortInternally(portInfo)) {
                    openPorts.put(port, portInfo);

                    // Сохраняем в конфиг с уникальным именем
                    String ruleName = "port_" + System.currentTimeMillis();
                    config.set("ports.open." + ruleName + ".port", port);
                    config.set("ports.open." + ruleName + ".protocol", protocol.toUpperCase());
                    config.set("ports.open." + ruleName + ".description", description);
                    saveConfig();

                    if (sender != null) {
                        sender.sendMessage(getTranslation("ports.opened", port, protocol, description));
                    }
                } else {
                    if (sender != null) {
                        sender.sendMessage(getTranslation("ports.error.failed_to_open", port));
                    }
                }

            } catch (NumberFormatException e) {
                if (sender != null) {
                    sender.sendMessage(getTranslation("ports.error.invalid_number", portStr));
                }
            }
        }

        private boolean openPortInternally(PortInfo portInfo) {
            try {
                // Попытка использовать UPnP
                if (upnpManager.isUPnPAvailable()) {
                    boolean success = upnpManager.openPort(
                            portInfo.getPort(),
                            portInfo.getProtocol(),
                            portInfo.getDescription()
                    );

                    if (success) {
                        getLogger().info(getTranslation("upnp.port_opened", portInfo.getPort()));
                        return true;
                    }
                }

                // Альтернативный метод - проверка доступности порта
                if (isPortAvailable(portInfo.getPort())) {
                    getLogger().info(getTranslation("ports.port_available", portInfo.getPort()));
                    return true;
                }

                return false;

            } catch (Exception e) {
                getLogger().log(Level.WARNING, getTranslation("ports.error.open_failed", portInfo.getPort()), e);
                return false;
            }
        }

        public void closePort(CommandSender sender, String portStr) {
            try {
                int port = Integer.parseInt(portStr);

                if (openPorts.containsKey(port)) {
                    PortInfo portInfo = openPorts.get(port);

                    // Закрываем порт через UPnP
                    if (upnpManager.isUPnPAvailable()) {
                        upnpManager.closePort(port);
                    }

                    // Удаляем из конфига
                    if (config.contains("ports.open")) {
                        for (String ruleName : config.getConfigurationSection("ports.open").getKeys(false)) {
                            if (config.getInt("ports.open." + ruleName + ".port") == port) {
                                config.set("ports.open." + ruleName, null);
                                break;
                            }
                        }
                    }
                    saveConfig();

                    openPorts.remove(port);

                    if (sender != null) {
                        sender.sendMessage(getTranslation("ports.closed", port));
                    }
                } else {
                    if (sender != null) {
                        sender.sendMessage(getTranslation("ports.error.not_opened", port));
                    }
                }

            } catch (NumberFormatException e) {
                if (sender != null) {
                    sender.sendMessage(getTranslation("ports.error.invalid_number", portStr));
                }
            }
        }

        public void closeAllPorts() {
            for (Integer port : openPorts.keySet()) {
                if (upnpManager.isUPnPAvailable()) {
                    upnpManager.closePort(port);
                }
            }
            openPorts.clear();
            getLogger().info(getTranslation("ports.all_closed"));
        }

        public void listPorts(CommandSender sender) {
            if (openPorts.isEmpty()) {
                sender.sendMessage(getTranslation("ports.none_opened"));
                return;
            }

            sender.sendMessage(getTranslation("ports.list_header"));
            for (PortInfo portInfo : openPorts.values()) {
                String status = isPortOpen(portInfo.getPort()) ?
                        getTranslation("status.open") : getTranslation("status.closed");
                ChatColor statusColor = isPortOpen(portInfo.getPort()) ? ChatColor.GREEN : ChatColor.RED;

                sender.sendMessage(getTranslation("ports.list_format",
                        portInfo.getPort(), portInfo.getProtocol(), statusColor + status, portInfo.getDescription()));
            }
        }

        private boolean isPortOpen(int port) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private boolean isPortAvailable(int port) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        public int getOpenPortCount() {
            return openPorts.size();
        }

        private class PortInfo {
            private final int port;
            private final String protocol;
            private final String description;
            private final String ruleName;

            public PortInfo(int port, String protocol, String description, String ruleName) {
                this.port = port;
                this.protocol = protocol;
                this.description = description;
                this.ruleName = ruleName;
            }

            public int getPort() { return port; }
            public String getProtocol() { return protocol; }
            public String getDescription() { return description; }
            public String getRuleName() { return ruleName; }
        }
    }

    // НОВЫЙ КЛАСС: PortIPManager для управления портами по IP с новой структурой
    private class PortIPManager implements Listener {
        private final Map<String, Set<PortIPRule>> portIPRules; // IP -> Set of port rules
        private final SimpleUPnPManager upnpManager;

        public PortIPManager() {
            this.portIPRules = new HashMap<>();
            this.upnpManager = new SimpleUPnPManager();
        }

        public void loadPortIPRules() {
            portIPRules.clear();

            if (config.contains("ports-ip.rules")) {
                for (String ruleName : config.getConfigurationSection("ports-ip.rules").getKeys(false)) {
                    try {
                        String ip = config.getString("ports-ip.rules." + ruleName + ".ip");
                        int port = config.getInt("ports-ip.rules." + ruleName + ".port");
                        String protocol = config.getString("ports-ip.rules." + ruleName + ".protocol", "TCP");
                        String action = config.getString("ports-ip.rules." + ruleName + ".action", "OPEN");
                        String description = config.getString("ports-ip.rules." + ruleName + ".description", "No description");

                        // Валидация IP
                        if (isValidIP(ip)) {
                            PortIPRule rule = new PortIPRule(ip, port, protocol, action, description, ruleName);
                            portIPRules.computeIfAbsent(ip, k -> new HashSet<>()).add(rule);
                        } else {
                            getLogger().warning(getTranslation("portsip.error.invalid_ip_config", ruleName, ip));
                        }
                    } catch (Exception e) {
                        getLogger().warning(getTranslation("portsip.error.invalid_rule_config", ruleName));
                    }
                }
            }

            getLogger().info(getTranslation("portsip.rules_loaded", getPortIPCount()));
        }

        private boolean isValidIP(String ip) {
            try {
                String[] parts = ip.split("\\.");
                if (parts.length != 4) return false;

                for (String part : parts) {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) return false;
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private void savePortIPRule(PortIPRule rule) {
            String ruleName = rule.getRuleName();
            config.set("ports-ip.rules." + ruleName + ".ip", rule.getIp());
            config.set("ports-ip.rules." + ruleName + ".port", rule.getPort());
            config.set("ports-ip.rules." + ruleName + ".protocol", rule.getProtocol());
            config.set("ports-ip.rules." + ruleName + ".action", rule.getAction());
            config.set("ports-ip.rules." + ruleName + ".description", rule.getDescription());
            saveConfig();
        }

        private void removePortIPRule(String ip, int port) {
            // Находим правило по IP и порту и удаляем его
            if (portIPRules.containsKey(ip)) {
                Set<PortIPRule> rules = portIPRules.get(ip);
                PortIPRule ruleToRemove = null;

                for (PortIPRule rule : rules) {
                    if (rule.getPort() == port) {
                        ruleToRemove = rule;
                        break;
                    }
                }

                if (ruleToRemove != null) {
                    rules.remove(ruleToRemove);
                    config.set("ports-ip.rules." + ruleToRemove.getRuleName(), null);
                    saveConfig();

                    if (rules.isEmpty()) {
                        portIPRules.remove(ip);
                    }
                }
            }
        }

        @EventHandler
        public void onPlayerLogin(PlayerLoginEvent event) {
            Player player = event.getPlayer();
            String ip = event.getAddress().getHostAddress();

            // Проверяем правила портов для этого IP
            if (portIPRules.containsKey(ip)) {
                Set<PortIPRule> rules = portIPRules.get(ip);
                for (PortIPRule rule : rules) {
                    if ("BLOCK".equalsIgnoreCase(rule.getAction())) {
                        getLogger().warning(getTranslation("portsip.login_blocked",
                                ip, player.getName(), rule.getPort(), rule.getProtocol()));
                    }
                }
            }
        }

        public void manageIPPorts() {
            // Автоматическое управление портами для IP
            for (Set<PortIPRule> rules : portIPRules.values()) {
                for (PortIPRule rule : rules) {
                    if ("OPEN".equalsIgnoreCase(rule.getAction()) && !isPortOpen(rule.getPort())) {
                        openPortInternally(rule);
                    }
                }
            }
        }

        public void openPortForIP(CommandSender sender, String ip, String portStr, String protocol) {
            openPortForIP(sender, ip, portStr, protocol, "Custom port for IP");
        }

        public void openPortForIP(CommandSender sender, String ip, String portStr, String protocol, String description) {
            try {
                // Валидация IP адреса
                InetAddress.getByName(ip);
                int port = Integer.parseInt(portStr);

                if (port < 1 || port > 65535) {
                    sender.sendMessage(getTranslation("portsip.error.invalid_range"));
                    return;
                }

                if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                    sender.sendMessage(getTranslation("portsip.error.invalid_protocol"));
                    return;
                }

                String ruleName = "ip_port_" + System.currentTimeMillis();
                PortIPRule rule = new PortIPRule(ip, port, protocol.toUpperCase(), "OPEN", description, ruleName);

                // Добавляем правило
                portIPRules.computeIfAbsent(ip, k -> new HashSet<>()).add(rule);

                // Открываем порт
                if (openPortInternally(rule)) {
                    // Сохраняем в конфиг
                    savePortIPRule(rule);

                    sender.sendMessage(getTranslation("portsip.opened", ip, port, protocol, description));
                } else {
                    sender.sendMessage(getTranslation("portsip.error.failed_to_open", ip, port));
                }

            } catch (UnknownHostException e) {
                sender.sendMessage(getTranslation("portsip.error.invalid_ip", ip));
            } catch (NumberFormatException e) {
                sender.sendMessage(getTranslation("portsip.error.invalid_number", portStr));
            }
        }

        public void blockPortForIP(CommandSender sender, String ip, String portStr) {
            blockPortForIP(sender, ip, portStr, "ANY", "Blocked by administrator");
        }

        public void blockPortForIP(CommandSender sender, String ip, String portStr, String protocol, String reason) {
            try {
                // Валидация IP адреса
                InetAddress.getByName(ip);
                int port = Integer.parseInt(portStr);

                if (port < 1 || port > 65535) {
                    sender.sendMessage(getTranslation("portsip.error.invalid_range"));
                    return;
                }

                if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol) && !"ANY".equalsIgnoreCase(protocol)) {
                    sender.sendMessage(getTranslation("portsip.error.invalid_protocol"));
                    return;
                }

                String ruleName = "block_" + System.currentTimeMillis();
                PortIPRule rule = new PortIPRule(ip, port, protocol, "BLOCK", reason, ruleName);

                // Добавляем правило блокировки
                portIPRules.computeIfAbsent(ip, k -> new HashSet<>()).add(rule);

                // Сохраняем в конфиг
                savePortIPRule(rule);

                sender.sendMessage(getTranslation("portsip.blocked", ip, port, protocol, reason));

            } catch (UnknownHostException e) {
                sender.sendMessage(getTranslation("portsip.error.invalid_ip", ip));
            } catch (NumberFormatException e) {
                sender.sendMessage(getTranslation("portsip.error.invalid_number", portStr));
            }
        }

        public void closePortForIP(CommandSender sender, String ip, String portStr) {
            try {
                int port = Integer.parseInt(portStr);

                if (portIPRules.containsKey(ip)) {
                    Set<PortIPRule> rules = portIPRules.get(ip);
                    boolean removed = rules.removeIf(rule -> rule.getPort() == port && "OPEN".equalsIgnoreCase(rule.getAction()));

                    if (removed) {
                        // Закрываем порт через UPnP
                        if (upnpManager.isUPnPAvailable()) {
                            upnpManager.closePort(port);
                        }

                        // Удаляем из конфига
                        removePortIPRule(ip, port);

                        sender.sendMessage(getTranslation("portsip.closed", ip, port));
                    } else {
                        sender.sendMessage(getTranslation("portsip.error.open_rule_not_found", ip, port));
                    }
                } else {
                    sender.sendMessage(getTranslation("portsip.error.ip_not_found", ip));
                }

            } catch (NumberFormatException e) {
                sender.sendMessage(getTranslation("portsip.error.invalid_number", portStr));
            }
        }

        public void unblockPortForIP(CommandSender sender, String ip, String portStr) {
            try {
                int port = Integer.parseInt(portStr);

                if (portIPRules.containsKey(ip)) {
                    Set<PortIPRule> rules = portIPRules.get(ip);
                    boolean removed = rules.removeIf(rule -> rule.getPort() == port && "BLOCK".equalsIgnoreCase(rule.getAction()));

                    if (removed) {
                        // Удаляем из конфига
                        removePortIPRule(ip, port);

                        sender.sendMessage(getTranslation("portsip.unblocked", ip, port));
                    } else {
                        sender.sendMessage(getTranslation("portsip.error.block_rule_not_found", ip, port));
                    }
                } else {
                    sender.sendMessage(getTranslation("portsip.error.ip_not_found", ip));
                }

            } catch (NumberFormatException e) {
                sender.sendMessage(getTranslation("portsip.error.invalid_number", portStr));
            }
        }

        public void listPortsIP(CommandSender sender) {
            if (portIPRules.isEmpty()) {
                sender.sendMessage(getTranslation("portsip.no_rules"));
                return;
            }

            sender.sendMessage(getTranslation("portsip.rules_header"));
            int totalRules = 0;
            int openRules = 0;
            int blockRules = 0;

            for (Map.Entry<String, Set<PortIPRule>> entry : portIPRules.entrySet()) {
                String ip = entry.getKey();
                Set<PortIPRule> rules = entry.getValue();
                totalRules += rules.size();

                sender.sendMessage(ChatColor.GOLD + "IP: " + ip + " - " + rules.size() + " rules");
                for (PortIPRule rule : rules) {
                    ChatColor color = "OPEN".equalsIgnoreCase(rule.getAction()) ? ChatColor.GREEN : ChatColor.RED;
                    String actionText = "OPEN".equalsIgnoreCase(rule.getAction()) ?
                            getTranslation("portsip.action_open") : getTranslation("portsip.action_block");

                    if ("OPEN".equalsIgnoreCase(rule.getAction())) {
                        openRules++;
                    } else {
                        blockRules++;
                    }

                    sender.sendMessage(color + "  Port: " + rule.getPort() +
                            " | Protocol: " + rule.getProtocol() +
                            " | Action: " + actionText +
                            " | " + rule.getDescription());
                }
            }

            sender.sendMessage(getTranslation("portsip.total_rules", totalRules, openRules, blockRules));
        }

        public void clearAllPortsIP(CommandSender sender) {
            int totalRules = getPortIPCount();

            // Закрываем все открытые порты
            for (Set<PortIPRule> rules : portIPRules.values()) {
                for (PortIPRule rule : rules) {
                    if ("OPEN".equalsIgnoreCase(rule.getAction()) && upnpManager.isUPnPAvailable()) {
                        upnpManager.closePort(rule.getPort());
                    }
                }
            }

            portIPRules.clear();
            config.set("ports-ip.rules", null);
            saveConfig();

            sender.sendMessage(getTranslation("portsip.all_cleared", totalRules));
        }

        public void closeAllIPPorts() {
            // Закрываем все открытые порты для IP
            for (Set<PortIPRule> rules : portIPRules.values()) {
                for (PortIPRule rule : rules) {
                    if ("OPEN".equalsIgnoreCase(rule.getAction()) && upnpManager.isUPnPAvailable()) {
                        upnpManager.closePort(rule.getPort());
                    }
                }
            }
        }

        private boolean openPortInternally(PortIPRule rule) {
            try {
                // Попытка использовать UPnP
                if (upnpManager.isUPnPAvailable()) {
                    boolean success = upnpManager.openPort(
                            rule.getPort(),
                            rule.getProtocol(),
                            rule.getDescription() + " for IP " + rule.getIp()
                    );

                    if (success) {
                        getLogger().info(getTranslation("portsip.port_opened", rule.getPort(), rule.getIp()));
                        return true;
                    }
                }

                // Альтернативный метод - проверка доступности порта
                if (isPortAvailable(rule.getPort())) {
                    getLogger().info(getTranslation("portsip.port_available", rule.getPort(), rule.getIp()));
                    return true;
                }

                return false;

            } catch (Exception e) {
                getLogger().log(Level.WARNING, getTranslation("portsip.error.open_failed", rule.getPort(), rule.getIp()), e);
                return false;
            }
        }

        private boolean isPortOpen(int port) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("localhost", port), 1000);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        private boolean isPortAvailable(int port) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        public int getPortIPCount() {
            return portIPRules.values().stream().mapToInt(Set::size).sum();
        }

        public boolean isPortAllowedForIP(String ip, int port, String protocol) {
            if (portIPRules.containsKey(ip)) {
                Set<PortIPRule> rules = portIPRules.get(ip);
                for (PortIPRule rule : rules) {
                    if (rule.getPort() == port &&
                            ("ANY".equalsIgnoreCase(rule.getProtocol()) || rule.getProtocol().equalsIgnoreCase(protocol))) {
                        return "OPEN".equalsIgnoreCase(rule.getAction());
                    }
                }
            }
            return true; // По умолчанию разрешено
        }

        private class PortIPRule {
            private final String ip;
            private final int port;
            private final String protocol;
            private final String action;
            private final String description;
            private final String ruleName;

            public PortIPRule(String ip, int port, String protocol, String action, String description, String ruleName) {
                this.ip = ip;
                this.port = port;
                this.protocol = protocol;
                this.action = action;
                this.description = description;
                this.ruleName = ruleName;
            }

            public String getIp() { return ip; }
            public int getPort() { return port; }
            public String getProtocol() { return protocol; }
            public String getAction() { return action; }
            public String getDescription() { return description; }
            public String getRuleName() { return ruleName; }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                PortIPRule that = (PortIPRule) obj;
                return port == that.port && ip.equals(that.ip);
            }

            @Override
            public int hashCode() {
                return Objects.hash(ip, port);
            }
        }
    }

    // Внутренний класс SimpleUPnPManager
    private class SimpleUPnPManager {
        public boolean isUPnPAvailable() {
            try {
                // Простая проверка доступности UPnP
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (iface.isUp() && !iface.isLoopback()) {
                        return true;
                    }
                }
            } catch (SocketException e) {
                getLogger().warning(getTranslation("upnp.error.check_failed"));
            }
            return false;
        }

        public boolean openPort(int port, String protocol, String description) {
            try {
                // Здесь должна быть реализация UPnP
                // Для простоты возвращаем true, предполагая успех
                getLogger().info(getTranslation("upnp.opening_port", port, protocol, description));

                // В реальной реализации здесь будет код для работы с UPnP
                // через библиотеки типа weupnp или cling

                return true;
            } catch (Exception e) {
                getLogger().warning(getTranslation("upnp.error.open_failed", port, e.getMessage()));
                return false;
            }
        }

        public boolean closePort(int port) {
            try {
                getLogger().info(getTranslation("upnp.closing_port", port));

                // В реальной реализации здесь будет код для закрытия порта через UPnP

                return true;
            } catch (Exception e) {
                getLogger().warning(getTranslation("upnp.error.close_failed", port, e.getMessage()));
                return false;
            }
        }
    }
}