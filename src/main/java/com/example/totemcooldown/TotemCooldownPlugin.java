package com.example.totemcooldown;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class TotemCooldownPlugin extends JavaPlugin implements Listener {
    private static final long COOLDOWN_TICKS = 20L * 24_000L;
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "cooldowns.yml");
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("totemtimer") != null) {
            getCommand("totemtimer").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by a player.");
                    return true;
                }
                if (player.hasPermission("totemcooldown.bypass")) {
                    player.sendMessage(ChatColor.GREEN + "You bypass the totem cooldown.");
                    return true;
                }
                long remaining = remainingTicks(player.getUniqueId());
                if (remaining <= 0) {
                    player.sendMessage(ChatColor.GREEN + "Your totem is ready to use.");
                } else {
                    long totalSeconds = (remaining + 19L) / 20L;
                    long gameDays = (remaining + 24_000L - 1L) / 24_000L;
                    long hours = totalSeconds / 3600L;
                    long minutes = (totalSeconds % 3600L) / 60L;
                    long seconds = totalSeconds % 60L;
                    player.sendMessage(ChatColor.YELLOW + "Totem cooldown: " + gameDays
                            + " in-game day(s) remaining (about " + hours + "h "
                            + minutes + "m " + seconds + "s of server tick time).");
                }
                return true;
            });
        }
    }

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.hasPermission("totemcooldown.bypass")) return;

        UUID uuid = player.getUniqueId();
        long remaining = remainingTicks(uuid);

        if (remaining > 0) {
            // Cancelling resurrection means the totem cannot save the player.
            event.setCancelled(true);
            long days = (remaining + 24_000L - 1L) / 24_000L;
            player.sendMessage(ChatColor.RED + "Your totem is on cooldown! About "
                    + days + " in-game day(s) remain. Use /totemtimer for details.");
            return;
        }

        // Start cooldown only when a totem successfully triggers.
        long now = currentWorldTime();
        data.set(uuid.toString(), now + COOLDOWN_TICKS);
        saveData();
        player.sendMessage(ChatColor.GOLD + "Totem used! You can use another after 20 in-game days.");
    }

    private long remainingTicks(UUID uuid) {
        long until = data.getLong(uuid.toString(), 0L);
        return Math.max(0L, until - currentWorldTime());
    }

    private long currentWorldTime() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return world == null ? 0L : world.getFullTime();
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save cooldown data: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (data != null) saveData();
    }
}
