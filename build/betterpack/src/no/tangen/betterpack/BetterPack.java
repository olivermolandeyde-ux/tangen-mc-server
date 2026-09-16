package no.tangen.betterpack;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterPack extends JavaPlugin implements Listener {

    private String url;
    private byte[] hash;
    private String prompt;
    private int delayTicks;
    private boolean respectDecline;
    private final Set<UUID> optedOut = new HashSet<>();
    private File optedOutFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        url = getConfig().getString("url", "");
        hash = hexToBytes(getConfig().getString("sha1", ""));
        prompt = getConfig().getString("prompt", "");
        delayTicks = Math.max(0, getConfig().getInt("delay-seconds", 2)) * 20;
        respectDecline = getConfig().getBoolean("respect-decline", true);
        optedOutFile = new File(getDataFolder(), "opted-out.yml");
        loadOptedOut();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("BetterPack aktivert - sender pakke etter join (aldri tvunget).");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (respectDecline && optedOut.contains(player.getUniqueId())) {
                return;
            }
            player.setResourcePack(url, hash, prompt, false);
        }, delayTicks);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!respectDecline) {
            return;
        }
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.DISCARDED) {
            optedOut.add(event.getPlayer().getUniqueId());
            saveOptedOut();
        }
    }

    private void loadOptedOut() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(optedOutFile);
        for (String value : config.getStringList("uuids")) {
            try {
                optedOut.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveOptedOut() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(optedOutFile);
        config.set("uuids", optedOut.stream().map(UUID::toString).toList());
        try {
            config.save(optedOutFile);
        } catch (IOException e) {
            getLogger().warning("Kunne ikke lagre opted-out.yml: " + e.getMessage());
        }
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}