package me.lokka30.noportaltrap;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import me.lokka30.noportaltrap.debug.DebugCategory;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NoPortalTrap extends JavaPlugin implements Listener {

    boolean enabled = true;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        new Metrics(this, 14330);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        // don't process if they haven't actually changed the block they're on
        if(locationsSimilar(event.getFrom(), event.getTo())) return;

        checkPortal(event.getPlayer());
    }

    /**
     * K: UUID of the player that is currently being checked.
     * V: how many seconds they have been in a portal for.
     */
    public HashMap<UUID, Integer> checking = new HashMap<>();

    public boolean checkPortal(final Player player) {
        // make sure the player is standing in a portal
        final boolean portalAtHead = isPortal(player.getEyeLocation().getBlock().getType());
        final boolean portalAtFeet = isPortal(player.getLocation().getBlock().getType());
        if(!(portalAtHead || portalAtFeet)) return false;

        // make sure the player has the permission
        if(!player.hasPermission("noportaltrap.detect")) return false;

        if(!checking.containsKey(player.getUniqueId())) {
            checking.put(player.getUniqueId(), 0);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if(isDebugCategoryEnabled(DebugCategory.PORTAL_CHECK_TASK)) {
                        getLogger().info(String.format(
                            "[DEBUG - %s] Checking for %s.",
                            DebugCategory.PORTAL_CHECK_TASK,
                            player.getUniqueId()
                        ));
                    }

                    if(!player.isOnline() ||
                        !checking.containsKey(player.getUniqueId()) ||
                        !checkPortal(player)
                    ) {
                        cancel();
                        checking.remove(player.getUniqueId());
                        return;
                    }

                    final int secondsInPortal = checking.get(player.getUniqueId()) + 1;
                    checking.put(player.getUniqueId(), secondsInPortal);

                    final ConfigurationSection section = getConfig()
                        .getConfigurationSection("seconds-in-portal." + secondsInPortal);

                    if(section != null) {
                        if(section.contains("send-chat-message")) {
                            section.getStringList("send-chat-message").forEach(msg -> player.sendMessage(colorize(msg)));
                        }

                        if(section.contains("send-action-bar-message")) {
                            section.getStringList("send-action-bar-message").forEach(msg ->
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(msg)
                                )));
                        }

                        if(section.getBoolean("teleport-to-world-spawn", false)) {
                            player.teleport(player.getWorld().getSpawnLocation());
                        }
                    }

                    if(secondsInPortal == getConfig().getInt("seconds-in-portal.max-duration", 15)) {
                        cancel();
                    }
                }
            }.runTaskTimer(this, 20L, 20L);
        }

        return true;
    }

    private boolean isPortal(final Material material) {
        return getConfig().getStringList("portal-materials").contains(material.toString());
    }

    private boolean locationsSimilar(final Location loc1, final Location loc2) {
        Objects.requireNonNull(loc1, "loc1");
        Objects.requireNonNull(loc2, "loc2");

        return loc1.getWorld().getName().equals(loc2.getWorld().getName()) &&
            loc1.getBlockX() == loc2.getBlockX() &&
            loc1.getBlockY() == loc2.getBlockY() &&
            loc1.getBlockZ() == loc2.getBlockZ();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!requireCmdPerm(sender, "noportaltrap.command.noportaltrap")) return true;

        if(args.length == 0) {
            sender.sendMessage(prefix("Please specify a subcommand. For help, run '&b/" + label + " help&7'."));
            return true;
        }

        switch(args[0].toUpperCase(Locale.ROOT)) {
            case "HELP":
                if(!requireCmdPerm(sender, "noportaltrap.command.noportaltrap.help")) return true;

                if(args.length != 1) {
                    sender.sendMessage(prefix("Invalid usage - too many arguments."));
                    sender.sendMessage(prefix("Usage: &b/" + label + " help"));
                    return true;
                }

                sender.sendMessage(colorize("&8&n+----------------------------+"));
                sender.sendMessage(prefix("Available subcommands:"));
                sender.sendMessage(colorize("&8&n+----------------------------+"));
                sender.sendMessage(colorize("&8 &m->&b /" + label + " help &8~ &7list of the plugin's commands"));
                sender.sendMessage(colorize("&8 &m->&b /" + label + " toggle &8~ &7toggle the plugin"));
                sender.sendMessage(colorize("&8 &m->&b /" + label + " reload &8~ &7reload the configuration"));
                sender.sendMessage(colorize("&8 &m->&b /" + label + " info &8~ &7view info about the plugin"));
                sender.sendMessage(colorize("&8&n+----------------------------+"));
                return true;
            case "TOGGLE":
                if(!requireCmdPerm(sender, "noportaltrap.command.noportaltrap.toggle")) return true;

                if(args.length != 1) {
                    sender.sendMessage(prefix("Invalid usage - too many arguments."));
                    sender.sendMessage(prefix("Usage: &b/" + label + " toggle"));
                    return true;
                }

                enabled = !enabled;
                getConfig().set("enabled", enabled);
                sender.sendMessage(prefix("Portal trap detection is now " + (enabled ? "&aenabled" : "&cdisabled") + "&7."));
                return true;
            case "RELOAD":
                if(!requireCmdPerm(sender, "noportaltrap.command.noportaltrap.reload")) return true;

                sender.sendMessage(prefix("Reloading configuration..."));
                reloadConfig();
                sender.sendMessage(prefix("Reload complete."));
                return true;
            case "INFO":
                if(!requireCmdPerm(sender, "noportaltrap.command.noportaltrap.info")) return true;

                Arrays.asList(
                    "&8&n+----------------------------+",
                    "&b&l%name%&b v%version%",
                    "&7Authors: &8[&b%authors%&8]",
                    "&7Commands: &8[&b%commands%&8]",
                    "&7More info @ &8&n%website%",
                    "&8&n+----------------------------+"
                ).forEach(msg -> sender.sendMessage(msg
                    .replace("%name%", getDescription().getName())
                    .replace("%version%", getDescription().getVersion())
                    .replace("%authors%", String.join("&7, &b", getDescription().getAuthors()))
                    .replace("%commands%", String.join("&7, &b", getDescription().getCommands().keySet()))
                    .replace("%website%", getDescription().getWebsite())
                ));
                return true;
            default:
                sender.sendMessage(prefix("Invalid usage - unknown subcommand '&f" + args[0] + "&7'."));
                sender.sendMessage(prefix("For help, run '&b/" + label + " help&7'."));
                return true;
        }
    }

    private boolean requireCmdPerm(final CommandSender sender, final String permission) {
        if (sender.hasPermission(permission)) return true;
        sender.sendMessage(prefix("You don't have access to that."));
        return false;
    }

    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if(args.length == 0) {
            return Arrays.asList("help", "toggle", "reload", "info");
        } else {
            return Collections.emptyList();
        }
    }

    private String prefix(final String msg) {
        return colorize("&b&lNoPortalTrap:&7 " + msg);
    }

    private String colorize(final String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public boolean isDebugCategoryEnabled(final DebugCategory cat) {
        return getConfig().getStringList("debug").contains(cat.toString());
    }

}
