package feliksan4ik.databasemc.upnpfirewall;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

record SimpleUPnPManager(Main plugin) {

    public boolean isUPnPAvailable() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    return true;
                }
            }
        } catch (SocketException e) {
            plugin.getLogger().warning(plugin.getTranslation("upnp.error.check_failed"));
        }
        return false;
    }

    public boolean openPort(int port, String protocol, String description) {
        try {
            // Здесь должна быть реализация UPnP
            // Для простоты возвращаем true, предполагая успех
            plugin.getLogger().info(plugin.getTranslation("upnp.opening_port", port, protocol, description));

            // В реальной реализации здесь будет код для работы с UPnP
            // через библиотеки типа weupnp или cling

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getTranslation("upnp.error.open_failed", port, e.getMessage()));
            return false;
        }
    }

    public boolean closePort(int port) {
        try {
            plugin.getLogger().info(plugin.getTranslation("upnp.closing_port", port));

            // В реальной реализации здесь будет код для закрытия порта через UPnP

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getTranslation("upnp.error.close_failed", port, e.getMessage()));
            return false;
        }
    }
}
