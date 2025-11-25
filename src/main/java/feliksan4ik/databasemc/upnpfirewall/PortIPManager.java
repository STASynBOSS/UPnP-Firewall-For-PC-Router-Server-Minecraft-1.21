package feliksan4ik.databasemc.upnpfirewall;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.logging.Level;

class PortIPManager implements Listener {

    private final Main plugin;
    private final Map<String, Set<PortIPRule>> portIPRules;
    private final SimpleUPnPManager upnpManager;

    public PortIPManager(Main plugin) {
        this.plugin = plugin;
        this.portIPRules = new HashMap<>();
        this.upnpManager = new SimpleUPnPManager(plugin);
    }

    public void loadPortIPRules() {
        portIPRules.clear();

        if (plugin.config.contains("ports-ip.rules")) {
            for (String ruleName : plugin.config.getConfigurationSection("ports-ip.rules").getKeys(false)) {
                try {
                    String ip = plugin.config.getString("ports-ip.rules." + ruleName + ".ip");
                    int port = plugin.config.getInt("ports-ip.rules." + ruleName + ".port");
                    String protocol = plugin.config.getString("ports-ip.rules." + ruleName + ".protocol", "TCP");
                    String action = plugin.config.getString("ports-ip.rules." + ruleName + ".action", "OPEN");
                    String description = plugin.config.getString("ports-ip.rules." + ruleName + ".description", "No description");

                    if (isValidIP(ip)) {
                        PortIPRule rule = new PortIPRule(ip, port, protocol, action, description, ruleName);
                        portIPRules.computeIfAbsent(ip, k -> new HashSet<>()).add(rule);
                    } else {
                        plugin.getLogger().warning(plugin.getTranslation("portsip.error.invalid_ip_config", ruleName, ip));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning(plugin.getTranslation("portsip.error.invalid_rule_config", ruleName));
                }
            }
        }

        plugin.getLogger().info(plugin.getTranslation("portsip.rules_loaded", getPortIPCount()));
    }

    private boolean isValidIP(String ip) {
        if (ip == null) return false;

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
        String ruleName = rule.ruleName();
        plugin.config.set("ports-ip.rules." + ruleName + ".ip", rule.ip());
        plugin.config.set("ports-ip.rules." + ruleName + ".port", rule.port());
        plugin.config.set("ports-ip.rules." + ruleName + ".protocol", rule.protocol());
        plugin.config.set("ports-ip.rules." + ruleName + ".action", rule.action());
        plugin.config.set("ports-ip.rules." + ruleName + ".description", rule.description());
        plugin.saveConfig();
    }

    private void removePortIPRule(String ip, int port) {
        if (portIPRules.containsKey(ip)) {
            Set<PortIPRule> rules = portIPRules.get(ip);
            PortIPRule ruleToRemove = null;

            for (PortIPRule rule : rules) {
                if (rule.port() == port) {
                    ruleToRemove = rule;
                    break;
                }
            }

            if (ruleToRemove != null) {
                rules.remove(ruleToRemove);
                plugin.config.set("ports-ip.rules." + ruleToRemove.ruleName(), null);
                plugin.saveConfig();

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

        if (portIPRules.containsKey(ip)) {
            Set<PortIPRule> rules = portIPRules.get(ip);
            for (PortIPRule rule : rules) {
                if ("BLOCK".equalsIgnoreCase(rule.action())) {
                    plugin.getLogger().warning(plugin.getTranslation("portsip.login_blocked",
                            ip, player.getName(), rule.port(), rule.protocol()));
                }
            }
        }
    }

    public void manageIPPorts() {
        // Автоматическое управление портами для IP
        for (Set<PortIPRule> rules : portIPRules.values()) {
            for (PortIPRule rule : rules) {
                if ("OPEN".equalsIgnoreCase(rule.action()) && !isPortOpen(rule.port())) {
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
            InetAddress.getByName(ip);
            int port = Integer.parseInt(portStr);

            if (port < 1 || port > 65535) {
                sender.sendMessage(plugin.getTranslation("portsip.error.invalid_range"));
                return;
            }

            if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                sender.sendMessage(plugin.getTranslation("portsip.error.invalid_protocol"));
                return;
            }

            String ruleName = "ip_port_" + System.currentTimeMillis();
            PortIPRule rule = new PortIPRule(ip, port, protocol.toUpperCase(), "OPEN", description, ruleName);

            portIPRules.computeIfAbsent(ip, k -> new HashSet<>()).add(rule);

            if (openPortInternally(rule)) {
                savePortIPRule(rule);

                sender.sendMessage(plugin.getTranslation("portsip.opened", ip, port, protocol, description));
            } else {
                sender.sendMessage(plugin.getTranslation("portsip.error.failed_to_open", ip, port));
            }

        } catch (UnknownHostException e) {
            sender.sendMessage(plugin.getTranslation("portsip.error.invalid_ip", ip));
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getTranslation("portsip.error.invalid_number", portStr));
        }
    }

    public void blockPortForIP(CommandSender sender, String ip, String portStr) {
        blockPortForIP(sender, ip, portStr, "ANY", "Blocked by administrator");
    }

    public void blockPortForIP(CommandSender sender, String ip, String portStr, String protocol, String reason) {
        try {
            InetAddress.getByName(ip);
            int port = Integer.parseInt(portStr);

            if (port < 1 || port > 65535) {
                sender.sendMessage(plugin.getTranslation("portsip.error.invalid_range"));
                return;
            }

            if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol) && !"ANY".equalsIgnoreCase(protocol)) {
                sender.sendMessage(plugin.getTranslation("portsip.error.invalid_protocol"));
                return;
            }

            String ruleName = "block_" + System.currentTimeMillis();
            PortIPRule rule = new PortIPRule(ip, port, protocol, "BLOCK", reason, ruleName);

            portIPRules.computeIfAbsent(ip, k -> new HashSet<>()).add(rule);

            savePortIPRule(rule);

            sender.sendMessage(plugin.getTranslation("portsip.blocked", ip, port, protocol, reason));

        } catch (UnknownHostException e) {
            sender.sendMessage(plugin.getTranslation("portsip.error.invalid_ip", ip));
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getTranslation("portsip.error.invalid_number", portStr));
        }
    }

    public void closePortForIP(CommandSender sender, String ip, String portStr) {
        try {
            int port = Integer.parseInt(portStr);

            if (portIPRules.containsKey(ip)) {
                Set<PortIPRule> rules = portIPRules.get(ip);
                boolean removed = rules.removeIf(rule -> rule.port() == port && "OPEN".equalsIgnoreCase(rule.action()));

                if (removed) {
                    if (upnpManager.isUPnPAvailable()) {
                        upnpManager.closePort(port);
                    }

                    removePortIPRule(ip, port);

                    sender.sendMessage(plugin.getTranslation("portsip.closed", ip, port));
                } else {
                    sender.sendMessage(plugin.getTranslation("portsip.error.open_rule_not_found", ip, port));
                }
            } else {
                sender.sendMessage(plugin.getTranslation("portsip.error.ip_not_found", ip));
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getTranslation("portsip.error.invalid_number", portStr));
        }
    }

    public void unblockPortForIP(CommandSender sender, String ip, String portStr) {
        try {
            int port = Integer.parseInt(portStr);

            if (portIPRules.containsKey(ip)) {
                Set<PortIPRule> rules = portIPRules.get(ip);
                boolean removed = rules.removeIf(rule -> rule.port() == port && "BLOCK".equalsIgnoreCase(rule.action()));

                if (removed) {
                    removePortIPRule(ip, port);

                    sender.sendMessage(plugin.getTranslation("portsip.unblocked", ip, port));
                } else {
                    sender.sendMessage(plugin.getTranslation("portsip.error.block_rule_not_found", ip, port));
                }
            } else {
                sender.sendMessage(plugin.getTranslation("portsip.error.ip_not_found", ip));
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getTranslation("portsip.error.invalid_number", portStr));
        }
    }

    public void listPortsIP(CommandSender sender) {
        if (portIPRules.isEmpty()) {
            sender.sendMessage(plugin.getTranslation("portsip.no_rules"));
            return;
        }

        sender.sendMessage(plugin.getTranslation("portsip.rules_header"));
        int totalRules = 0;
        int openRules = 0;
        int blockRules = 0;

        for (Map.Entry<String, Set<PortIPRule>> entry : portIPRules.entrySet()) {
            String ip = entry.getKey();
            Set<PortIPRule> rules = entry.getValue();
            totalRules += rules.size();

            sender.sendMessage(ChatColor.GOLD + "IP: " + ip + " - " + rules.size() + " rules");
            for (PortIPRule rule : rules) {
                ChatColor color = "OPEN".equalsIgnoreCase(rule.action()) ? ChatColor.GREEN : ChatColor.RED;
                String actionText = "OPEN".equalsIgnoreCase(rule.action()) ?
                        plugin.getTranslation("portsip.action_open") : plugin.getTranslation("portsip.action_block");

                if ("OPEN".equalsIgnoreCase(rule.action())) {
                    openRules++;
                } else {
                    blockRules++;
                }

                sender.sendMessage(color + "  Port: " + rule.port() +
                        " | Protocol: " + rule.protocol() +
                        " | Action: " + actionText +
                        " | " + rule.description());
            }
        }

        sender.sendMessage(plugin.getTranslation("portsip.total_rules", totalRules, openRules, blockRules));
    }

    public void clearAllPortsIP(CommandSender sender) {
        int totalRules = getPortIPCount();

        for (Set<PortIPRule> rules : portIPRules.values()) {
            for (PortIPRule rule : rules) {
                if ("OPEN".equalsIgnoreCase(rule.action()) && upnpManager.isUPnPAvailable()) {
                    upnpManager.closePort(rule.port());
                }
            }
        }

        portIPRules.clear();
        plugin.config.set("ports-ip.rules", null);
        plugin.saveConfig();

        sender.sendMessage(plugin.getTranslation("portsip.all_cleared", totalRules));
    }

    public void closeAllIPPorts() {
        for (Set<PortIPRule> rules : portIPRules.values()) {
            for (PortIPRule rule : rules) {
                if ("OPEN".equalsIgnoreCase(rule.action()) && upnpManager.isUPnPAvailable()) {
                    upnpManager.closePort(rule.port());
                }
            }
        }
    }

    private boolean openPortInternally(PortIPRule rule) {
        try {
            if (upnpManager.isUPnPAvailable()) {
                boolean success = upnpManager.openPort(
                        rule.port(),
                        rule.protocol(),
                        rule.description() + " for IP " + rule.ip()
                );

                if (success) {
                    plugin.getLogger().info(plugin.getTranslation("portsip.port_opened", rule.port(), rule.ip()));
                    return true;
                }
            }

            if (isPortAvailable(rule.port())) {
                plugin.getLogger().info(plugin.getTranslation("portsip.port_available", rule.port(), rule.ip()));
                return true;
            }

            return false;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, plugin.getTranslation("portsip.error.open_failed", rule.port(), rule.ip()), e);
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
        try (ServerSocket ignored = new ServerSocket(port)) {
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
                if (rule.port() == port &&
                        ("ANY".equalsIgnoreCase(rule.protocol()) || rule.protocol().equalsIgnoreCase(protocol))) {
                    return "OPEN".equalsIgnoreCase(rule.action());
                }
            }
        }
        return true;
    }

    private record PortIPRule(String ip, int port, String protocol, String action, String description,
                              String ruleName) {

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
