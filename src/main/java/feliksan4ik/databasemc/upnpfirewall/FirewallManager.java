package feliksan4ik.databasemc.upnpfirewall;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

class FirewallManager implements Listener {

    private final Main plugin;
    private final Set<FirewallRule> firewallRules;
    private boolean enabled;

    public FirewallManager(Main plugin) {
        this.plugin = plugin;
        this.firewallRules = new HashSet<>();
        this.enabled = true;
    }

    public void loadFirewallRules() {
        firewallRules.clear();

        enabled = plugin.config.getBoolean("firewall.enabled", true);

        if (plugin.config.contains("firewall.rules")) {
            for (String key : plugin.config.getConfigurationSection("firewall.rules").getKeys(false)) {
                String ip = plugin.config.getString("firewall.rules." + key + ".ip");
                String type = plugin.config.getString("firewall.rules." + key + ".type");
                String action = plugin.config.getString("firewall.rules." + key + ".action");

                if (ip != null && type != null && action != null) {
                    firewallRules.add(new FirewallRule(ip, type, action));
                }
            }
        }

        plugin.getLogger().info(plugin.getTranslation("firewall.rules_loaded", firewallRules.size()));
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        String ip = event.getAddress().getHostAddress();

        for (FirewallRule rule : firewallRules) {
            if (rule.matches(ip)) {
                if ("block".equalsIgnoreCase(rule.action())) {
                    if ("blacklist".equalsIgnoreCase(rule.type())) {
                        event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
                        event.setKickMessage(plugin.getTranslation("firewall.kick_blocked"));
                        plugin.getLogger().warning(plugin.getTranslation("firewall.login_blocked", ip, player.getName()));
                        return;
                    }
                } else if ("allow".equalsIgnoreCase(rule.action())) {
                    if ("whitelist".equalsIgnoreCase(rule.type())) {
                        // Разрешить подключение
                        return;
                    }
                }
            }
        }

        // Если включен режим whitelist по умолчанию, блокируем всех не в whitelist
        if (plugin.config.getBoolean("firewall.whitelist-mode", false)) {
            boolean allowed = false;
            for (FirewallRule rule : firewallRules) {
                if ("whitelist".equalsIgnoreCase(rule.type()) && "allow".equalsIgnoreCase(rule.action()) && rule.matches(ip)) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
                event.setKickMessage(plugin.getTranslation("firewall.kick_whitelist"));
                plugin.getLogger().warning(plugin.getTranslation("firewall.whitelist_blocked", ip, player.getName()));
            }
        }
    }

    public void addRule(CommandSender sender, String ip, String type, String action) {
        try {
            InetAddress.getByName(ip);

            if (!"whitelist".equalsIgnoreCase(type) && !"blacklist".equalsIgnoreCase(type)) {
                sender.sendMessage(plugin.getTranslation("firewall.error.invalid_type"));
                return;
            }

            if (!"allow".equalsIgnoreCase(action) && !"block".equalsIgnoreCase(action)) {
                sender.sendMessage(plugin.getTranslation("firewall.error.invalid_action"));
                return;
            }

            FirewallRule rule = new FirewallRule(ip, type, action);
            firewallRules.add(rule);

            String ruleKey = "rule_" + System.currentTimeMillis();
            plugin.config.set("firewall.rules." + ruleKey + ".ip", ip);
            plugin.config.set("firewall.rules." + ruleKey + ".type", type.toLowerCase());
            plugin.config.set("firewall.rules." + ruleKey + ".action", action.toLowerCase());
            plugin.saveConfig();

            sender.sendMessage(plugin.getTranslation("firewall.rule_added", ip));

        } catch (UnknownHostException e) {
            sender.sendMessage(plugin.getTranslation("firewall.error.invalid_ip", ip));
        }
    }

    public void removeRule(CommandSender sender, String ip) {
        boolean removed = firewallRules.removeIf(rule -> rule.ip().equals(ip));

        if (removed) {
            if (plugin.config.contains("firewall.rules")) {
                for (String key : plugin.config.getConfigurationSection("firewall.rules").getKeys(false)) {
                    if (ip.equals(plugin.config.getString("firewall.rules." + key + ".ip"))) {
                        plugin.config.set("firewall.rules." + key, null);
                        break;
                    }
                }
            }
            plugin.saveConfig();

            sender.sendMessage(plugin.getTranslation("firewall.rule_removed", ip));
        } else {
            sender.sendMessage(plugin.getTranslation("firewall.error.rule_not_found", ip));
        }
    }

    public void listRules(CommandSender sender) {
        if (firewallRules.isEmpty()) {
            sender.sendMessage(plugin.getTranslation("firewall.no_rules"));
            return;
        }

        sender.sendMessage(plugin.getTranslation("firewall.rules_header"));
        for (FirewallRule rule : firewallRules) {
            // Красный цвет для блокирующих правил, зеленый для разрешающих
            ChatColor color = "block".equalsIgnoreCase(rule.action()) ? ChatColor.RED : ChatColor.GREEN;
            String actionText = "block".equalsIgnoreCase(rule.action()) ?
                    plugin.getTranslation("firewall.action_block") : plugin.getTranslation("firewall.action_allow");

            sender.sendMessage(color + plugin.getTranslation("firewall.rule_format",
                    rule.ip(), rule.type(), actionText));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getRuleCount() {
        return firewallRules.size();
    }

    private record FirewallRule(String ip, String type, String action) {

        public boolean matches(String checkIp) {
            return ip.equals(checkIp) || ip.equals("*") || checkIp.startsWith(ip.replace(".*", ""));
        }

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
