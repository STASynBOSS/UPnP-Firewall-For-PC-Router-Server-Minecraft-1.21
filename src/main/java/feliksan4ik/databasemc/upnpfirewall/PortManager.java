package feliksan4ik.databasemc.upnpfirewall;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

class PortManager {
    private final Main plugin;
    private final Map<Integer, PortInfo> openPorts;
    private final SimpleUPnPManager upnpManager;

    public PortManager(Main plugin) {
        this.plugin = plugin;
        this.openPorts = new HashMap<>();
        this.upnpManager = new SimpleUPnPManager(plugin);
    }

    public void loadPortSettings() {
        openPorts.clear();

        if (plugin.config.contains("ports.open")) {
            for (String ruleName : plugin.config.getConfigurationSection("ports.open").getKeys(false)) {
                try {
                    int port = plugin.config.getInt("ports.open." + ruleName + ".port");
                    String protocol = plugin.config.getString("ports.open." + ruleName + ".protocol", "TCP");
                    String description = plugin.config.getString("ports.open." + ruleName + ".description", "Minecraft Server");

                    if (port >= 1 && port <= 65535) {
                        openPorts.put(port, new PortInfo(port, protocol, description, ruleName));
                    } else {
                        plugin.getLogger().warning(plugin.getTranslation("ports.error.invalid_range_config", ruleName, port));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning(plugin.getTranslation("ports.error.invalid_port_config", ruleName));
                }
            }
        }

        plugin.getLogger().info(plugin.getTranslation("ports.settings_loaded", openPorts.size()));
    }

    public void manageServerPorts() {
        if (plugin.config.getBoolean("auto-port-management.open-server-port", true)) {
            int serverPort = plugin.getServer().getPort();
            if (!isPortOpen(serverPort)) {
                openPort(null, String.valueOf(serverPort), "TCP", plugin.getTranslation("ports.default_description"));
            }
        }

        for (PortInfo portInfo : openPorts.values()) {
            if (!isPortOpen(portInfo.port())) {
                openPortInternally(portInfo);
            }
        }
    }

    public void openPort(CommandSender sender, String portStr, String protocol, String description) {
        try {
            int port = Integer.parseInt(portStr);

            if (port < 1 || port > 65535) {
                if (sender != null) {
                    sender.sendMessage(plugin.getTranslation("ports.error.invalid_range"));
                }
                return;
            }

            if (!"TCP".equalsIgnoreCase(protocol) && !"UDP".equalsIgnoreCase(protocol)) {
                if (sender != null) {
                    sender.sendMessage(plugin.getTranslation("ports.error.invalid_protocol"));
                }
                return;
            }

            PortInfo portInfo = new PortInfo(port, protocol.toUpperCase(), description, "cmd_" + System.currentTimeMillis());

            if (openPortInternally(portInfo)) {
                openPorts.put(port, portInfo);

                String ruleName = "port_" + System.currentTimeMillis();
                plugin.config.set("ports.open." + ruleName + ".port", port);
                plugin.config.set("ports.open." + ruleName + ".protocol", protocol.toUpperCase());
                plugin.config.set("ports.open." + ruleName + ".description", description);
                plugin.saveConfig();

                if (sender != null) {
                    sender.sendMessage(plugin.getTranslation("ports.opened", port, protocol, description));
                }
            } else {
                if (sender != null) {
                    sender.sendMessage(plugin.getTranslation("ports.error.failed_to_open", port));
                }
            }

        } catch (NumberFormatException e) {
            if (sender != null) {
                sender.sendMessage(plugin.getTranslation("ports.error.invalid_number", portStr));
            }
        }
    }

    private boolean openPortInternally(PortInfo portInfo) {
        try {
            if (upnpManager.isUPnPAvailable()) {
                boolean success = upnpManager.openPort(
                        portInfo.port(),
                        portInfo.protocol(),
                        portInfo.description()
                );

                if (success) {
                    plugin.getLogger().info(plugin.getTranslation("upnp.port_opened", portInfo.port()));
                    return true;
                }
            }

            if (isPortAvailable(portInfo.port())) {
                plugin.getLogger().info(plugin.getTranslation("ports.port_available", portInfo.port()));
                return true;
            }

            return false;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, plugin.getTranslation("ports.error.open_failed", portInfo.port()), e);
            return false;
        }
    }

    public void closePort(CommandSender sender, String portStr) {
        try {
            int port = Integer.parseInt(portStr);

            if (openPorts.containsKey(port)) {
//                PortInfo portInfo = openPorts.get(port);

                if (upnpManager.isUPnPAvailable()) {
                    upnpManager.closePort(port);
                }

                if (plugin.config.contains("ports.open")) {
                    for (String ruleName : plugin.config.getConfigurationSection("ports.open").getKeys(false)) {
                        if (plugin.config.getInt("ports.open." + ruleName + ".port") == port) {
                            plugin.config.set("ports.open." + ruleName, null);
                            break;
                        }
                    }
                }
                plugin.saveConfig();

                openPorts.remove(port);

                if (sender != null) {
                    sender.sendMessage(plugin.getTranslation("ports.closed", port));
                }
            } else {
                if (sender != null) {
                    sender.sendMessage(plugin.getTranslation("ports.error.not_opened", port));
                }
            }

        } catch (NumberFormatException e) {
            if (sender != null) {
                sender.sendMessage(plugin.getTranslation("ports.error.invalid_number", portStr));
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
        plugin.getLogger().info(plugin.getTranslation("ports.all_closed"));
    }

    public void listPorts(CommandSender sender) {
        if (openPorts.isEmpty()) {
            sender.sendMessage(plugin.getTranslation("ports.none_opened"));
            return;
        }

        sender.sendMessage(plugin.getTranslation("ports.list_header"));
        for (PortInfo portInfo : openPorts.values()) {
            String status = isPortOpen(portInfo.port()) ?
                    plugin.getTranslation("status.open") : plugin.getTranslation("status.closed");
            ChatColor statusColor = isPortOpen(portInfo.port()) ? ChatColor.GREEN : ChatColor.RED;

            sender.sendMessage(plugin.getTranslation("ports.list_format",
                    portInfo.port(), portInfo.protocol(), statusColor + status, portInfo.description()));
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

    public int getOpenPortCount() {
        return openPorts.size();
    }

}
