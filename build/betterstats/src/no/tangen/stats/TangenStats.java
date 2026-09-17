package no.tangen.stats;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class TangenStats extends JavaPlugin implements Listener {

    enum Role {
        OWNER("owner", "\u00A79Owner", 1000, "&9[Owner] "),
        LAERER("laerer", "\u00A7eL\u00E6rer", 900, "&e[L\u00E6rer] "),
        MOD("mod", "\u00A7cModerator", 800, "&c[Moderator] "),
        ELEV("elev", "\u00A7aElev", 100, "&aElev ");

        final String group;
        final String display;
        final String prefix;
        final int weight;

        Role(String group, String display, int weight, String prefix) {
            this.group = group;
            this.display = display;
            this.prefix = prefix;
            this.weight = weight;
        }

        static Role byGroup(String name) {
            for (Role r : values()) {
                if (r.group.equalsIgnoreCase(name)) {
                    return r;
                }
            }
            return null;
        }

        static List<String> names() {
            List<String> out = new ArrayList<>();
            for (Role r : values()) {
                out.add(r.group);
            }
            return out;
        }
    }

    private static final String PERM_ROLE = "tangen.stats.role";

    private static final Map<Role, List<String>> ROLE_PERMS = Map.of(
            Role.OWNER, List.of("*"),
            Role.LAERER, List.of("tangen.stats.role",
                    "minecraft.command.kick", "minecraft.command.ban", "minecraft.command.unban",
                    "minecraft.command.ban-ip", "minecraft.command.unban-ip", "minecraft.command.tp",
                    "essentials.kick", "essentials.ban", "essentials.unban", "essentials.tempban",
                    "essentials.mute", "essentials.unmute", "essentials.warn",
                    "essentials.tp", "essentials.tp.self", "essentials.tp.others", "essentials.tp.coordinates",
                    "essentials.spawn", "essentials.sethome", "essentials.home", "essentials.delhome",
                    "essentials.homes", "essentials.rtp"),
            Role.MOD, List.of("minecraft.command.kick",
                    "essentials.kick", "essentials.mute", "essentials.unmute",
                    "essentials.warn", "essentials.tempban",
                    "essentials.tp", "essentials.tp.self", "essentials.tp.others", "essentials.tp.coordinates",
                    "essentials.spawn", "essentials.sethome", "essentials.home", "essentials.delhome",
                    "essentials.homes", "essentials.rtp"),
            Role.ELEV, List.of(
                    "minecraft.command.tp", "essentials.tp", "essentials.tp.self", "essentials.tp.others",
                    "essentials.tpa", "essentials.tpahere", "essentials.tpaccept", "essentials.tpdeny",
                    "essentials.tpacancel",
                    "essentials.spawn", "essentials.sethome", "essentials.home", "essentials.delhome",
                    "essentials.homes", "essentials.rtp", "essentials.back", "essentials.back.ondeath"));

    private static final Map<Role, List<String>> ROLE_DENY = Map.of(
            Role.OWNER, List.of("essentials.keepinv"));

    private static final String TITLE = "\u00A7b\u00A7lTangen Stats";
    private static final int TEAM_COUNT = 16;
    private static final String TEAM_PREFIX = "ts";
    private static final String COLORS = "0123456789abcdef";

    private final Map<UUID, String> sidebarSig = new HashMap<>();

    private boolean antiRadarEnabled = true;
    private int antiRadarDistance = 64;
    private final Map<UUID, Set<UUID>> hiddenFrom = new HashMap<>();

    private final Map<String, TeamData> teams = new HashMap<>();
    private final Map<UUID, Boolean> teamChatMode = new HashMap<>();
    private File teamsFile;

    private final Set<UUID> nightVision = new HashSet<>();
    private File nightVisionFile;

    private final Set<UUID> starterGiven = new HashSet<>();
    private File starterFile;

    private boolean treeFallEnabled = true;

    private boolean safeZoneEnabled = true;
    private boolean safeBorderOn = false;

    private final Map<String, HologramData> holoData = new java.util.LinkedHashMap<>();
    private final Map<String, TextDisplay> holoDisplays = new HashMap<>();

    private static final class HologramData {
        String world;
        double x;
        double y;
        double z;
        final List<String> lines;

        HologramData(String world, double x, double y, double z, List<String> lines) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lines = lines;
        }
    }
    private int safeChunkWidth = 2;
    private World safeWorld;
    private int safeMinX, safeMinZ, safeMaxX, safeMaxZ;
    private final Map<UUID, Integer> borderTasks = new HashMap<>();

    private LuckPerms luckPerms;
    private YamlConfiguration stats;
    private File statsFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        treeFallEnabled = getConfig().getBoolean("tree-fall.enabled", true);
        loadSafeZone();
        loadHolograms();
        antiRadarEnabled = getConfig().getBoolean("anti-radar.enabled", true);
        antiRadarDistance = getConfig().getInt("anti-radar.distance", 64);
        teamsFile = new File(getDataFolder(), "teams.yml");
        loadTeams();

        nightVisionFile = new File(getDataFolder(), "nightvision.yml");
        loadNightVision();

        starterFile = new File(getDataFolder(), "starter.yml");
        loadStarter();

        statsFile = new File(getDataFolder(), "stats.yml");
        stats = YamlConfiguration.loadConfiguration(statsFile);
        if (!stats.isConfigurationSection("players")) {
            stats.createSection("players");
        }

        luckPerms = LuckPermsProvider.get();
        ensureRoles();

        Bukkit.getPluginManager().registerEvents(this, this);

        getCommand("topp").setExecutor(this);
        getCommand("stats").setExecutor(this);
        getCommand("role").setExecutor(this);
        getCommand("sone").setExecutor(this);
        getCommand("rtp").setExecutor(this);
        getCommand("spawn").setExecutor(this);
        getCommand("hologram").setExecutor(this);
        getCommand("team").setExecutor(this);
        getCommand("createteam").setExecutor(this);
        getCommand("nightvision").setExecutor(this);
        getCommand("role").setTabCompleter((s, c, a, l) -> {
            if (l.length == 1) {
                return List.of("give");
            }
            if (l.length == 2 && "give".equalsIgnoreCase(l[0])) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    names.add(p.getName());
                }
                return names;
            }
            if (l.length == 3 && "give".equalsIgnoreCase(l[0])) {
                return Role.names();
            }
            return List.of();
        });

        getCommand("team").setTabCompleter((sender, command, alias, args) -> {
            if (args.length == 1) {
                return List.of("add", "remove", "leave", "disband", "list", "info", "chat");
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sub.equals("add") || sub.equals("remove")) {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        names.add(p.getName());
                    }
                    return names;
                }
                if (sub.equals("info")) {
                    return new ArrayList<>(teams.keySet());
                }
                if (sub.equals("chat")) {
                    return List.of("on", "off", "toggle");
                }
            }
            return List.of();
        });

        getCommand("createteam").setTabCompleter((sender, command, alias, args) -> List.of());

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateSidebar(p);
                updateTab(p);
                updateListName(p);
            }
        }, 60L, 60L);

        Bukkit.getScheduler().runTaskTimer(this, this::evictMobsFromZone, 40L, 20L);
        if (antiRadarEnabled) {
            Bukkit.getScheduler().runTaskTimer(this, this::updateVisibility, 40L, 20L);
        }

        getLogger().info("TangenStats aktivert - sidepanel, tab-lederboard og roller klare.");
    }

    @Override
    public void onDisable() {
        saveStatsSync();
    }

    // ------------------------------------------------------------------
    // Roller via LuckPerms
    // ------------------------------------------------------------------

    private void ensureRoles() {
        Group oldAdmin = luckPerms.getGroupManager().getGroup("admin");
        if (oldAdmin != null) {
            luckPerms.getGroupManager().deleteGroup(oldAdmin).exceptionally(e -> null);
            getLogger().info("Fjernet gammel 'admin'-gruppe (erstattet av 'laerer').");
        }
        for (Role role : Role.values()) {
            Group g = luckPerms.getGroupManager().getGroup(role.group);
            if (g != null) {
                configureGroup(g, role);
                continue;
            }
            luckPerms.getGroupManager().createAndLoadGroup(role.group).thenAccept(group -> {
                configureGroup(group, role);
                getLogger().info("Opprettet rolle '" + role.group + "'.");
            }).exceptionally(e -> {
                getLogger().severe("Kunne ikke opprette rolle '" + role.group + "': " + e.getMessage());
                return null;
            });
        }
    }

    private void configureGroup(Group group, Role role) {
        for (net.luckperms.api.node.Node n : group.data().toCollection()) {
            if (n instanceof PrefixNode) {
                group.data().remove(n);
            }
        }
        group.data().add(PrefixNode.builder(role.prefix, role.weight).build());

        Set<String> allowed = new HashSet<>(ROLE_PERMS.get(role));
        Set<String> managed = ROLE_PERMS.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        for (net.luckperms.api.node.Node n : group.data().toCollection()) {
            if (!(n instanceof PermissionNode pn)) {
                continue;
            }
            String perm = pn.getPermission();
            if (managed.contains(perm) && !allowed.contains(perm)) {
                group.data().remove(n);
            }
        }
        for (String perm : allowed) {
            boolean present = group.data().toCollection().stream()
                    .filter(n -> n instanceof PermissionNode)
                    .map(n -> ((PermissionNode) n).getPermission())
                    .anyMatch(perm::equals);
            if (!present) {
                group.data().add(PermissionNode.builder(perm).build());
            }
        }
        List<String> deny = ROLE_DENY.getOrDefault(role, List.of());
        for (String perm : deny) {
            for (Node n : group.data().toCollection()) {
                if (n instanceof PermissionNode pn && pn.getPermission().equals(perm) && pn.getValue()) {
                    group.data().remove(n);
                }
            }
            group.data().add(PermissionNode.builder(perm).value(false).build());
        }
        luckPerms.getGroupManager().saveGroup(group);
    }

    private Node groupNode(String role) {
        return InheritanceNode.builder(role).build();
    }

    private void ensureElevOnJoin(UUID uuid) {
        luckPerms.getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            Set<String> groups = user.data().toCollection().stream()
                    .map(Node::getKey)
                    .filter(k -> k.startsWith("group."))
                    .map(k -> k.substring("group.".length()))
                    .collect(Collectors.toSet());
            boolean hasElev = groups.contains("elev");
            boolean hasOtherRole = groups.stream()
                    .anyMatch(name -> name.equals("owner") || name.equals("laerer") || name.equals("mod"));
            boolean primaryIsOurs = Role.byGroup(user.getPrimaryGroup()) != null;

            boolean changed = false;
            if (!hasElev && !hasOtherRole) {
                user.data().add(groupNode("elev"));
                changed = true;
            }
            if (!primaryIsOurs && !hasOtherRole) {
                user.setPrimaryGroup("elev");
                changed = true;
            }
            if (changed) {
                luckPerms.getUserManager().saveUser(user);
            }
        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(this, runnable));
    }

    private Role roleOf(UUID uuid) {
        User u = luckPerms.getUserManager().getUser(uuid);
        if (u == null) {
            return null;
        }
        Role fromPrimary = Role.byGroup(u.getPrimaryGroup());
        if (fromPrimary != null) {
            return fromPrimary;
        }
        Role found = null;
        int best = Integer.MIN_VALUE;
        for (Role candidate : Role.values()) {
            boolean held = u.data().toCollection().stream()
                    .map(Node::getKey)
                    .anyMatch(("group." + candidate.group)::equals);
            if (held && candidate.weight > best) {
                best = candidate.weight;
                found = candidate;
            }
        }
        return found;
    }

    private String roleDisplay(Player p) {
        Role r = roleOf(p.getUniqueId());
        if (r != null) {
            return r.display;
        }
        User u = luckPerms.getUserManager().getUser(p.getUniqueId());
        String pg = u != null ? u.getPrimaryGroup() : "elev";
        return "\u00A77" + pg;
    }

    private String roleDisplayOf(UUID uuid) {
        Role r = roleOf(uuid);
        return r != null ? r.display : null;
    }

    // ------------------------------------------------------------------
    // Stats (drap / dødsfall)
    // ------------------------------------------------------------------

    private int getKills(UUID u) {
        return stats.getInt("players." + u + ".kills");
    }

    private int getDeaths(UUID u) {
        return stats.getInt("players." + u + ".deaths");
    }

    private void addKill(UUID killer, UUID victim) {
        String k = "players." + killer + ".kills";
        stats.set(k, stats.getInt(k) + 1);
        addDeath(victim);
    }

    private void addDeath(UUID dead) {
        String d = "players." + dead + ".deaths";
        stats.set(d, stats.getInt(d) + 1);
    }

    private void rememberName(Player p) {
        stats.set("players." + p.getUniqueId() + ".name", p.getName());
    }

    private void saveStatsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, this::saveStatsSync);
    }

    private void saveStatsSync() {
        try {
            stats.save(statsFile);
        } catch (IOException e) {
            getLogger().severe("Kunne ikke lagre stats.yml: " + e.getMessage());
        }
    }

    private List<ConfigEntry> topList(int n) {
        ConfigurationSection sec = stats.getConfigurationSection("players");
        List<ConfigEntry> list = new ArrayList<>();
        if (sec == null) {
            return list;
        }
        for (String key : sec.getKeys(false)) {
            UUID uuid = parseUuid(key);
            if (uuid == null) {
                continue;
            }
            int kills = sec.getInt(key + ".kills");
            int deaths = sec.getInt(key + ".deaths");
            String name = sec.getString(key + ".name");
            list.add(new ConfigEntry(uuid, name, kills, deaths));
        }
        list.sort(Comparator.comparingInt(ConfigEntry::kills).reversed().thenComparingInt(ConfigEntry::deaths));
        return list.stream().limit(n).collect(Collectors.toList());
    }

    private UUID parseUuid(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    record ConfigEntry(UUID uuid, String name, int kills, int deaths) {}

    // ------------------------------------------------------------------
    // Sidepanel
    // ------------------------------------------------------------------

    private String invisible(int i) {
        return "\u00A7" + COLORS.charAt(i % 16) + "\u00A7r";
    }

    private void updateSidebar(Player p) {
        UUID uid = p.getUniqueId();
        if (!p.isOnline()) {
            return;
        }
        int kills = getKills(uid);
        int deaths = getDeaths(uid);
        double kd = deaths == 0 ? kills : (double) kills / deaths;

        List<String> lines = new ArrayList<>();
        lines.add("&8\u00A7m" + " \u2500".repeat(9));
        lines.add("&6\u25B8 &7Drap: &a" + kills);
        lines.add("&6\u25B8 &7D\u00F8dsfall: &c" + deaths);
        lines.add("&6\u25B8 &7D\u00F8d/drap: &6" + String.format("%.1f", kd));
        lines.add("&6\u25B8 &7Rolle: &r" + roleDisplay(p));
        lines.add("&8\u00A7m" + " \u2500".repeat(9));
        lines.add("&7&o/topp &8\u00B7 &7&o/stats");

        String sig = lines.stream().map(this::color).collect(Collectors.joining("\u0000"));

        Scoreboard sb = p.getScoreboard();
        boolean ourSidebar = sb != null
                && !sb.equals(Bukkit.getScoreboardManager().getMainScoreboard())
                && sb.getObjective("tstats") != null
                && sb.getObjective(DisplaySlot.SIDEBAR) != null
                && sb.getObjective(DisplaySlot.SIDEBAR).getName().equals("tstats");
        if (ourSidebar && sig.equals(sidebarSig.get(uid))) {
            return;
        }
        if (sb == null || sb.equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
        }
        try {
            for (int i = 0; i < TEAM_COUNT; i++) {
                sb.resetScores(invisible(i));
                Team team = sb.getTeam(TEAM_PREFIX + i);
                if (team != null) {
                    team.unregister();
                }
            }
            Objective obj = sb.getObjective("tstats");
            if (obj == null) {
                obj = sb.registerNewObjective("tstats", "dummy", TITLE);
            }
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            int score = lines.size() - 1;
            for (int i = 0; i < lines.size(); i++) {
                String entry = invisible(i);
                Team team = sb.registerNewTeam(TEAM_PREFIX + i);
                team.addEntry(entry);
                team.setPrefix(color(lines.get(i)));
                obj.getScore(entry).setScore(score - i);
            }
            if (!p.getScoreboard().equals(sb)) {
                p.setScoreboard(sb);
            }
            sidebarSig.put(uid, sig);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Sidepanel-feil for " + p.getName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Tab-liste med lederboard
    // ------------------------------------------------------------------

    private Component toComponent(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private void updateTab(Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        String header = "\u00A7b\u00A7l\u2500\u2500\u2500 Tangen VGS \u2500\u2500\u2500\n"
                + "\u00A77\u00A7oTangen videreg\u00E5ende skole";

        StringBuilder footer = new StringBuilder();
        footer.append("\u00A78\u00A7m").append(" \u2500".repeat(11)).append("\n");
        footer.append("\u00A7b\u00A7l\u2605 Topp 3 - flest drap \u2605\n");
        List<ConfigEntry> top = topList(3);
        if (top.isEmpty()) {
            footer.append("\u00A77Ingen stats enn\u00E5!\n");
        }
        int rank = 1;
        for (ConfigEntry e : top) {
            String name = e.name() != null ? e.name() : nameOf(e.uuid());
            String role = roleDisplayOf(e.uuid());
            footer.append("\u00A77#").append(rank)
                    .append(" \u00A7f").append(name)
                    .append(" \u00A78[").append(role != null ? role : "\u00A77?")
                    .append("\u00A78] \u00A7a").append(e.kills())
                    .append("\u00A77 drap\n");
            rank++;
        }
        footer.append("\u00A78\u00A7m").append(" \u2500".repeat(11));

        viewer.sendPlayerListHeaderAndFooter(toComponent(header), toComponent(footer.toString()));
    }

    private void updateListName(Player p) {
        if (!p.isOnline()) {
            return;
        }
        int kills = getKills(p.getUniqueId());
        Role r = roleOf(p.getUniqueId());
        if (r == null) {
            r = Role.ELEV;
        }
        String tag = "\u00A77[" + r.display + "\u00A77] \u00A7f" + p.getName();
        p.playerListName(toComponent(tag + " \u00A78\u00B7 \u00A7a" + kills));
    }

    private void refreshTabForAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTab(p);
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // ------------------------------------------------------------------
    // Hendelser
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        rememberName(p);
        ensureElevOnJoin(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
                if (p.isOnline()) {
                    updateSidebar(p);
                    updateTab(p);
                    updateListName(p);
                    refreshTabForAll();
                    updateVisibility();
                    if (nightVision.contains(p.getUniqueId())) {
                        applyNightVision(p);
                    }
                    giveStarterKit(p);
                }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        hiddenFrom.remove(uid);
        for (Set<UUID> s : hiddenFrom.values()) {
            s.remove(uid);
        }
        teamChatMode.remove(uid);
    }

    @EventHandler
    public void onChat(PlayerChatEvent event) {
        Player p = event.getPlayer();
        String msg = event.getMessage();
        if (msg.startsWith("!")) {
            event.setCancelled(true);
            broadcastTeam(p, msg.substring(1));
            return;
        }
        if (Boolean.TRUE.equals(teamChatMode.get(p.getUniqueId()))) {
            event.setCancelled(true);
            broadcastTeam(p, msg);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        addDeath(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null) {
            addKill(killer.getUniqueId(), victim.getUniqueId());
        }
        saveStatsAsync();
        updateSidebar(victim);
        updateListName(victim);
        if (killer != null && killer.getUniqueId() != victim.getUniqueId()) {
            updateSidebar(killer);
            updateListName(killer);
        }
        refreshTabForAll();
    }

    // ------------------------------------------------------------------
    // Netherite-øks: fell hele treet på én blokk
    // ------------------------------------------------------------------

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled() || !treeFallEnabled) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return;
        }
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool.getType() != Material.NETHERITE_AXE) {
                return;
            }
        Block block = event.getBlock();
        if (!isLog(block.getType())) {
            return;
        }
        event.setCancelled(true);

        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> logs = new LinkedHashSet<>();
        logs.add(block);
        queue.add(block);

        int broke = 0;
        while (!queue.isEmpty()) {
            Block b = queue.poll();
            b.breakNaturally(tool);
            broke++;
            if (broke >= 100) {
                break;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block n = b.getRelative(dx, dy, dz);
                        if (isLog(n.getType()) && logs.add(n)) {
                            queue.add(n);
                        }
                    }
                }
            }
        }

        tool.damage(1, player);
        if (broke > 1) {
            player.giveExp(broke);
                player.sendActionBar(toComponent(color("&7Øksa felte &a" + broke + "&7 tømmerblokker.")));
        }
    }

    private boolean isLog(Material type) {
        String n = type.name();
        return n.endsWith("_LOG") || n.endsWith("_STEM");
    }

    // ------------------------------------------------------------------
    // Spawn-sone: 4 chunks trygg area
    // ------------------------------------------------------------------

    private void loadSafeZone() {
        safeZoneEnabled = getConfig().getBoolean("safe-zone.enabled", true);
        safeChunkWidth = Math.max(1, getConfig().getInt("safe-zone.chunk-width", 2));
        if (!safeZoneEnabled || Bukkit.getWorlds().isEmpty()) {
            safeWorld = null;
            getLogger().info("Spawn-sone er av.");
            return;
        }
        safeWorld = Bukkit.getWorlds().get(0);
        org.bukkit.Chunk c = safeWorld.getSpawnLocation().getChunk();
        int cx = c.getX();
        int cz = c.getZ();
        safeMinX = cx * 16;
        safeMinZ = cz * 16;
        safeMaxX = safeMinX + safeChunkWidth * 16;
        safeMaxZ = safeMinZ + safeChunkWidth * 16;
        getLogger().info("Spawn-sone: " + safeWorld.getName()
                + " X " + safeMinX + ".." + safeMaxX
                + " Z " + safeMinZ + ".." + safeMaxZ
                + " (" + safeChunkWidth + "x" + safeChunkWidth + " chunks)");
    }

    private boolean inSafeZone(Location loc) {
        return safeWorld != null && loc != null && loc.getWorld() != null
                && loc.getWorld().equals(safeWorld)
                && loc.getX() >= safeMinX && loc.getX() < safeMaxX
                && loc.getZ() >= safeMinZ && loc.getZ() < safeMaxZ;
    }

    private void zoneWarn(Player p) {
        p.sendActionBar(toComponent(color("&c&lTrygg-sone: &7Ikke lov å endre eller ødelegge her.")));
    }

    @EventHandler
    public void onBreakSafe(BlockBreakEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            zoneWarn(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlaceSafe(BlockPlaceEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            zoneWarn(event.getPlayer());
        }
    }

    @EventHandler
    public void onInteractSafe(PlayerInteractEvent event) {
        if (!safeZoneEnabled || event.isCancelled()) {
            return;
        }
        Block b = event.getClickedBlock();
        if (b == null || !inSafeZone(b.getLocation())) {
            return;
        }
        Action a = event.getAction();
        if ((a == Action.LEFT_CLICK_BLOCK || a == Action.RIGHT_CLICK_BLOCK) && b.getType().isInteractable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntitySafe(EntityDamageByEntityEvent event) {
        if (!safeZoneEnabled) {
            return;
        }
        boolean victimInZone = inSafeZone(event.getEntity().getLocation());
        if (!victimInZone) {
            return;
        }
        if (event.getDamager() instanceof Player) {
            event.setCancelled(true);
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player) {
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageSafe(EntityDamageEvent event) {
        if (safeZoneEnabled && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && inSafeZone(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHungerSafe(FoodLevelChangeEvent event) {
        if (!safeZoneEnabled || !(event.getEntity() instanceof Player p)
                || !inSafeZone(p.getLocation())) {
            return;
        }
        if (event.getFoodLevel() < p.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpawnSafe(CreatureSpawnEvent event) {
        if (!safeZoneEnabled || !inSafeZone(event.getLocation())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.EGG) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onExplodeSafe(EntityExplodeEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplodeSafe(BlockExplodeEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBurnSafe(BlockBurnEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpreadSafe(BlockSpreadEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArmorManipulateSafe(PlayerArmorStandManipulateEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getRightClicked().getLocation())) {
            event.setCancelled(true);
            zoneWarn(event.getPlayer());
        }
    }

    @EventHandler
    public void onPickupSafe(PlayerAttemptPickupItemEvent event) {
        if (safeZoneEnabled && inSafeZone(event.getItem().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMoveSafe(PlayerMoveEvent event) {
        if (!safeZoneEnabled || safeWorld == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !to.getWorld().equals(safeWorld)) {
            return;
        }
        if (from.distanceSquared(to) > 64) {
            return;
        }
        if (inSafeZone(from) && !inSafeZone(to)) {
            org.bukkit.entity.Player p = event.getPlayer();
            p.sendActionBar(toComponent(color("&7&lDu forlot trygg-sonen – RTP!")));
            randomTeleport(p);
        }
    }

    private void randomTeleport(Player p) {
        if (p == null || !p.isOnline()) {
            return;
        }
        World w = safeWorld != null ? safeWorld : p.getWorld();
        double half = w.getWorldBorder().getSize() / 2.0;
        double maxDist = Math.min(3000.0, half - 100.0);
        if (maxDist < 200.0) {
            maxDist = 200.0;
        }
        double sx = w.getSpawnLocation().getX();
        double sz = w.getSpawnLocation().getZ();
        Location target = null;
        for (int i = 0; i < 15; i++) {
            double ang = Math.random() * Math.PI * 2.0;
            double dist = 200.0 + Math.random() * (maxDist - 200.0);
            int tx = (int) Math.round(sx + Math.cos(ang) * dist);
            int tz = (int) Math.round(sz + Math.sin(ang) * dist);
            int ty = w.getHighestBlockYAt(tx, tz);
            if (ty <= 3) {
                continue;
            }
            org.bukkit.block.Block below = w.getBlockAt(tx, ty - 1, tz);
            if (below.isLiquid()) {
                continue;
            }
            String biome = w.getBiome(tx, tz).name();
            if (biome.contains("OCEAN") || biome.contains("RIVER")) {
                continue;
            }
            target = new Location(w, tx + 0.5, ty + 1.0, tz + 0.5);
            break;
        }
        if (target != null) {
            p.teleport(target);
            p.sendActionBar(toComponent(color("&aRTP: &7Du ble teleportet til &f"
                    + target.getBlockX() + ", " + target.getBlockY() + ", " + target.getBlockZ())));
        } else {
            p.sendActionBar(toComponent(color("&cFant ingen gyldig plass – prøv igjen.")));
        }
    }

    private void evictMobsFromZone() {
        if (!safeZoneEnabled || safeWorld == null) {
            return;
        }
        for (Entity e : safeWorld.getEntities()) {
            if (!(e instanceof LivingEntity) || e instanceof Player || e instanceof ArmorStand) {
                continue;
            }
            if (!inSafeZone(e.getLocation())) {
                continue;
            }
            double x = e.getLocation().getX();
            double z = e.getLocation().getZ();
            double dL = x - safeMinX;
            double dR = safeMaxX - x;
            double dF = z - safeMinZ;
            double dB = safeMaxZ - z;
            double min = Math.min(Math.min(dL, dR), Math.min(dF, dB));
            double nx = x;
            double nz = z;
            if (min == dL) {
                nx = safeMinX - 2;
            } else if (min == dR) {
                nx = safeMaxX + 2;
            } else if (min == dF) {
                nz = safeMinZ - 2;
            } else {
                nz = safeMaxZ + 2;
            }
            e.teleport(new Location(safeWorld, nx, e.getLocation().getY(), nz,
                    e.getLocation().getYaw(), e.getLocation().getPitch()));
        }
    }

    private void toggleSafeBorder(Player p) {
        Integer old = borderTasks.remove(p.getUniqueId());
        if (old != null) {
            Bukkit.getScheduler().cancelTask(old);
            p.sendMessage(color("&7Grensa er ikke lenger markert."));
            return;
        }
        double y = p.getEyeLocation().getY();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(115, 255, 0), 1.2f);
        int taskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (safeWorld == null) {
                Integer id = borderTasks.remove(p.getUniqueId());
                if (id != null) {
                    Bukkit.getScheduler().cancelTask(id);
                }
                return;
            }
            drawSafeBorder(p, y, dust);
        }, 0L, 2L).getTaskId();
        borderTasks.put(p.getUniqueId(), taskId);
        p.sendMessage(color("&aGrensa er markert med gr\u00F8nne partikler \u2013 skriv &f/sone&a igjen for \u00E5 sl\u00E5 den av."));
    }

    private void cancelAllBorders() {
        for (Integer id : borderTasks.values()) {
            Bukkit.getScheduler().cancelTask(id);
        }
        borderTasks.clear();
    }

    private void updateZoneConfig(Consumer<YamlConfiguration> change) {
        File cfgFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
        change.accept(cfg);
        try {
            cfg.save(cfgFile);
        } catch (IOException e) {
            getLogger().severe("Kunne ikke lagre config.yml: " + e.getMessage());
        }
        reloadConfig();
        loadSafeZone();
    }

    // ------------------------------------------------------------------
    // Hologrammer (TextDisplay)
    // ------------------------------------------------------------------

    private void loadHolograms() {
        for (Map<?, ?> m : getConfig().getMapList("holograms")) {
            try {
                String id = String.valueOf(m.get("id"));
                String wn = String.valueOf(m.get("world"));
                double x = ((Number) m.get("x")).doubleValue();
                double y = ((Number) m.get("y")).doubleValue();
                double z = ((Number) m.get("z")).doubleValue();
                List<String> lines = ((List<?>) m.get("lines")).stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
                holoData.put(id, new HologramData(wn, x, y, z, lines));
                spawnHologram(id, wn, x, y, z, lines);
            } catch (Exception e) {
                getLogger().warning("Kunne ikke laste hologram: " + e.getMessage());
            }
        }
        if (!holoData.isEmpty()) {
            getLogger().info("Lastet " + holoData.size() + " hologrammer.");
        }
    }

    private void saveHolograms() {
        File cfgFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, HologramData> e : holoData.entrySet()) {
            HologramData d = e.getValue();
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getKey());
            m.put("world", d.world);
            m.put("x", d.x);
            m.put("y", d.y);
            m.put("z", d.z);
            m.put("lines", new ArrayList<>(d.lines));
            list.add(m);
        }
        cfg.set("holograms", list);
        try {
            cfg.save(cfgFile);
        } catch (IOException ex) {
            getLogger().severe("Kunne ikke lagre hologrammer: " + ex.getMessage());
        }
    }

    private void spawnHologram(String id, String worldName, double x, double y, double z, List<String> lines) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            getLogger().warning("Verden '" + worldName + "' ikke funnet for hologram '" + id + "'.");
            return;
        }
        despawnHologram(id);
        Component text = renderHoloLines(lines);
        TextDisplay td = w.spawn(new Location(w, x, y, z), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setTextOpacity((byte) 220);
            d.setViewRange(64f);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.text(text);
        });
        holoDisplays.put(id, td);
    }

    private void despawnHologram(String id) {
        TextDisplay old = holoDisplays.remove(id);
        if (old != null) {
            old.remove();
        }
    }

    private Component renderHoloLines(List<String> lines) {
        String legacy = lines.stream()
                .map(l -> color(l))
                .collect(Collectors.joining("\n"));
        return toComponent(legacy);
    }

    private boolean manageHologramsAllowed(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return true;
        }
        return p.isOp() || p.hasPermission("tangen.stats.hologram")
                || Role.OWNER == roleOf(p.getUniqueId())
                || Role.LAERER == roleOf(p.getUniqueId())
                || Role.MOD == roleOf(p.getUniqueId());
    }

    private void handleHologram(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (holoData.isEmpty()) {
                sender.sendMessage(color("&7Ingen hologrammer. Lag et med /holo add <tekst>."));
            } else {
                for (Map.Entry<String, HologramData> e : holoData.entrySet()) {
                    String first = e.getValue().lines.isEmpty() ? "" : e.getValue().lines.get(0);
                    sender.sendMessage(color("&7" + e.getKey() + " &8- &f" + first
                            + " &8(" + e.getValue().world + " " + (int) e.getValue().x
                            + ", " + (int) e.getValue().y + ", " + (int) e.getValue().z + ")"));
                }
            }
            return;
        }
        if (!manageHologramsAllowed(sender)) {
            sender.sendMessage(color("&cDu har ikke lov til \u00E5 endre hologrammer."));
            return;
        }
        String sub = args[0].toLowerCase();
        if (!(sender instanceof Player) && !sub.equals("list") && !sub.equals("clear")) {
            sender.sendMessage("Kun spillere kan opprette/flytte hologrammer.");
            return;
        }
        switch (sub) {
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cBruk: /holo add <tekst>"));
                    return;
                }
                Player p = (Player) sender;
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                String id = uniqueHoloId();
                Location loc = p.getLocation();
                HologramData d = new HologramData(loc.getWorld().getName(),
                        loc.getX(), loc.getY() + 0.5, loc.getZ(), new ArrayList<>(List.of("&f" + text)));
                holoData.put(id, d);
                spawnHologram(id, d.world, d.x, d.y, d.z, d.lines);
                saveHolograms();
                sender.sendMessage(color("&aHologram &f" + id + "&a opprettet. &7(/holo list)"));
            }
            case "move" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cBruk: /holo move <id>"));
                    return;
                }
                HologramData d = holoData.get(args[1]);
                if (d == null) {
                    sender.sendMessage(color("&cFant ikke hologram '" + args[1] + "'."));
                    return;
                }
                Player p = (Player) sender;
                Location loc = p.getLocation();
                d.x = loc.getX();
                d.y = loc.getY() + 0.5;
                d.z = loc.getZ();
                d.world = loc.getWorld().getName();
                spawnHologram(args[1], d.world, d.x, d.y, d.z, d.lines);
                saveHolograms();
                sender.sendMessage(color("&aHologram &f" + args[1] + "&a flyttet."));
            }
            case "addline" -> {
                if (args.length < 3) {
                    sender.sendMessage(color("&cBruk: /holo addline <id> <tekst>"));
                    return;
                }
                HologramData d = holoData.get(args[1]);
                if (d == null) {
                    sender.sendMessage(color("&cFant ikke hologram '" + args[1] + "'."));
                    return;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                d.lines.add("&f" + text);
                spawnHologram(args[1], d.world, d.x, d.y, d.z, d.lines);
                saveHolograms();
                sender.sendMessage(color("&aLinje lagt til i &f" + args[1] + "&a."));
            }
            case "setline" -> {
                if (args.length < 4) {
                    sender.sendMessage(color("&cBruk: /holo setline <id> <linjenr> <tekst>"));
                    return;
                }
                HologramData d = holoData.get(args[1]);
                int idx;
                try {
                    idx = Integer.parseInt(args[2]) - 1;
                } catch (NumberFormatException ex) {
                    sender.sendMessage(color("&cLinjenr må være et tall."));
                    return;
                }
                if (d == null) {
                    sender.sendMessage(color("&cFant ikke hologram '" + args[1] + "'."));
                    return;
                }
                if (idx < 0 || idx >= d.lines.size()) {
                    sender.sendMessage(color("&cLinjenr finnes ikke. Antall linjer: " + d.lines.size()));
                    return;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                d.lines.set(idx, "&f" + text);
                spawnHologram(args[1], d.world, d.x, d.y, d.z, d.lines);
                saveHolograms();
                sender.sendMessage(color("&aLinje &f" + (idx + 1) + "&a endret i &f" + args[1] + "&a."));
            }
            case "removeline" -> {
                if (args.length < 3) {
                    sender.sendMessage(color("&cBruk: /holo removeline <id> <linjenr>"));
                    return;
                }
                HologramData d = holoData.get(args[1]);
                int idx;
                try {
                    idx = Integer.parseInt(args[2]) - 1;
                } catch (NumberFormatException ex) {
                    sender.sendMessage(color("&cLinjenr må være et tall."));
                    return;
                }
                if (d == null) {
                    sender.sendMessage(color("&cFant ikke hologram '" + args[1] + "'."));
                    return;
                }
                if (idx < 0 || idx >= d.lines.size()) {
                    sender.sendMessage(color("&cLinjenr finnes ikke. Antall linjer: " + d.lines.size()));
                    return;
                }
                d.lines.remove(idx);
                spawnHologram(args[1], d.world, d.x, d.y, d.z, d.lines);
                saveHolograms();
                sender.sendMessage(color("&aLinje fjernet fra &f" + args[1] + "&a."));
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cBruk: /holo remove <id>"));
                    return;
                }
                if (holoData.remove(args[1]) == null) {
                    sender.sendMessage(color("&cFant ikke hologram '" + args[1] + "'."));
                    return;
                }
                despawnHologram(args[1]);
                saveHolograms();
                sender.sendMessage(color("&aHologram &f" + args[1] + "&a fjernet."));
            }
            case "clear" -> {
                for (String id : new ArrayList<>(holoData.keySet())) {
                    holoData.remove(id);
                    despawnHologram(id);
                }
                saveHolograms();
                sender.sendMessage(color("&aAlle hologrammer fjernet."));
            }
            default -> sender.sendMessage(color("&cBruk: /holo add <tekst> | addline <id> <tekst> | setline <id> <nr> <tekst> | removeline <id> <nr> | move <id> | remove <id> | list | clear"));
        }
    }

    private String uniqueHoloId() {
        String base = "holo1";
        int n = 1;
        while (holoData.containsKey(base)) {
            n++;
            base = "holo" + n;
        }
        return base;
    }

    private boolean handleZoneChange(CommandSender sender, String[] args) {
        Player p = sender instanceof Player ? (Player) sender : null;
        boolean allowed = p == null || p.isOp()
                || p.hasPermission("tangen.stats.sone")
                || Role.OWNER == roleOf(p.getUniqueId());
        if (!allowed) {
            sender.sendMessage(color("&cDu har ikke lov til \u00E5 endre sonen."));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "sett" -> {
                if (args.length < 2) {
                    sender.sendMessage(color("&cBruk: /sone sett <antall chunks per side>"));
                    return true;
                }
                int n;
                try {
                    n = Math.max(1, Math.min(16, Integer.parseInt(args[1])));
                } catch (NumberFormatException e) {
                    sender.sendMessage(color("&cUgyldig tall: " + args[1]));
                    return true;
                }
                updateZoneConfig(cfg -> {
                    cfg.set("safe-zone.enabled", true);
                    cfg.set("safe-zone.chunk-width", n);
                });
                cancelAllBorders();
                sender.sendMessage(color("&aSone satt til &f" + n + "x" + n + "&a chunks rundt spawn."));
                sender.sendMessage(color("&7Skriv &f/sone&7 for \u00E5 markere grensa."));
                return true;
            }
            case "fjerne" -> {
                updateZoneConfig(cfg -> cfg.set("safe-zone.enabled", false));
                cancelAllBorders();
                sender.sendMessage(color("&cSonen er fjernet \u2013 beskyttelsen er av."));
                return true;
            }
            case "aktivere" -> {
                int cur = getConfig().getInt("safe-zone.chunk-width", 2);
                updateZoneConfig(cfg -> {
                    cfg.set("safe-zone.enabled", true);
                    cfg.set("safe-zone.chunk-width", cur);
                });
                sender.sendMessage(color("&aSonen er aktiv igjen (" + cur + "x" + cur + " chunks)."));
                return true;
            }
            default -> {
                sender.sendMessage(color("&cBruk: /sone | /sone sett <antall> | /sone fjerne | /sone aktivere"));
                return true;
            }
        }
    }

    private void drawSafeBorder(Player p, double y, Particle.DustOptions dust) {
        double yawY = y;
        for (int x = safeMinX; x < safeMaxX; x++) {
            p.spawnParticle(Particle.DUST, x + 0.5, yawY, safeMinZ + 0.5, 1, 0, 0, 0, 0, dust);
            p.spawnParticle(Particle.DUST, x + 0.5, yawY, safeMaxZ - 0.5, 1, 0, 0, 0, 0, dust);
        }
        for (int z = safeMinZ; z < safeMaxZ; z++) {
            p.spawnParticle(Particle.DUST, safeMinX + 0.5, yawY, z + 0.5, 1, 0, 0, 0, 0, dust);
            p.spawnParticle(Particle.DUST, safeMaxX - 0.5, yawY, z + 0.5, 1, 0, 0, 0, 0, dust);
        }
    }

    // ------------------------------------------------------------------
    // Team-data og hjelpemetoder
    // ------------------------------------------------------------------

    private static class TeamData {
        final String name;
        UUID leader;
        final List<UUID> members = new ArrayList<>();
        final String color;

        TeamData(String name, UUID leader, String color) {
            this.name = name;
            this.leader = leader;
            this.color = color;
            members.add(leader);
        }
    }

    private TeamData getTeamOf(UUID uuid) {
        for (TeamData td : teams.values()) {
            if (td.members.contains(uuid)) {
                return td;
            }
        }
        return null;
    }

    private void loadTeams() {
        if (teamsFile == null || !teamsFile.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(teamsFile);
        ConfigurationSection sec = cfg.getConfigurationSection("teams");
        if (sec == null) {
            return;
        }
        for (String name : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            String leaderStr = s.getString("leader");
            String color = s.getString("color", "&7");
            UUID leader = leaderStr != null ? parseUuid(leaderStr) : null;
            if (leader == null) {
                continue;
            }
            TeamData td = new TeamData(name, leader, color);
            td.members.clear();
            for (String m : s.getStringList("members")) {
                UUID uid = parseUuid(m);
                if (uid != null) {
                    td.members.add(uid);
                }
            }
            teams.put(name.toLowerCase(), td);
        }
    }

    private void saveTeams() {
        if (teamsFile == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (TeamData td : teams.values()) {
            ConfigurationSection s = cfg.createSection("teams." + td.name);
            s.set("leader", td.leader.toString());
            s.set("members", td.members.stream().map(UUID::toString).collect(Collectors.toList()));
            s.set("color", td.color);
        }
        try {
            cfg.save(teamsFile);
        } catch (IOException e) {
            getLogger().warning("Kunne ikke lagre teams.yml: " + e.getMessage());
        }
    }

    private void loadNightVision() {
        if (nightVisionFile == null || !nightVisionFile.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(nightVisionFile);
        for (String value : cfg.getStringList("uuids")) {
            try {
                nightVision.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveNightVision() {
        if (nightVisionFile == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("uuids", nightVision.stream().map(UUID::toString).toList());
        try {
            cfg.save(nightVisionFile);
        } catch (IOException e) {
            getLogger().warning("Kunne ikke lagre nightvision.yml: " + e.getMessage());
        }
    }

    private void toggleNightVision(Player p) {
        UUID uid = p.getUniqueId();
        if (nightVision.contains(uid)) {
            nightVision.remove(uid);
            saveNightVision();
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.sendMessage(color("&cNattesyn er av. Skriv /nightvision igjen for å slå på."));
        } else {
            nightVision.add(uid);
            saveNightVision();
            applyNightVision(p);
            p.sendMessage(color("&aNattesyn er på. Skriv /nightvision igjen for å slå av."));
        }
    }

    private void applyNightVision(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE, 1, false, false, true));
    }

    private void loadStarter() {
        if (starterFile == null || !starterFile.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(starterFile);
        for (String value : cfg.getStringList("uuids")) {
            try {
                starterGiven.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveStarter() {
        if (starterFile == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("uuids", starterGiven.stream().map(UUID::toString).toList());
        try {
            cfg.save(starterFile);
        } catch (IOException e) {
            getLogger().warning("Kunne ikke lagre starter.yml: " + e.getMessage());
        }
    }

    private void giveStarterKit(Player p) {
        UUID uid = p.getUniqueId();
        if (starterGiven.contains(uid)) {
            return;
        }
        ItemStack meat = new ItemStack(Material.COOKED_BEEF, 32);
        if (p.getInventory().firstEmpty() != -1) {
            p.getInventory().addItem(meat);
        } else {
            p.getWorld().dropItem(p.getLocation(), meat);
        }
        starterGiven.add(uid);
        saveStarter();
        p.sendMessage(color("&aDu fikk 32 kokt biff som startmat!"));
    }

    private void updateVisibility() {
        if (!antiRadarEnabled) {
            return;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player viewer : online) {
            Set<UUID> shouldHide = new HashSet<>();
            for (Player target : online) {
                if (viewer.equals(target)) {
                    continue;
                }
                if (!viewer.getWorld().equals(target.getWorld())
                        || viewer.getLocation().distanceSquared(target.getLocation()) > (double) antiRadarDistance * antiRadarDistance) {
                    shouldHide.add(target.getUniqueId());
                }
            }
            Set<UUID> currently = hiddenFrom.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());
            for (UUID uid : shouldHide) {
                if (!currently.contains(uid)) {
                    Player target = Bukkit.getPlayer(uid);
                    if (target != null) {
                        viewer.hideEntity(this, target);
                        currently.add(uid);
                    }
                }
            }
            var iter = currently.iterator();
            while (iter.hasNext()) {
                UUID uid = iter.next();
                if (!shouldHide.contains(uid)) {
                    Player target = Bukkit.getPlayer(uid);
                    if (target != null) {
                        viewer.showEntity(this, target);
                    }
                    iter.remove();
                }
            }
        }
    }

    private void broadcastTeam(Player sender, String msg) {
        TeamData td = getTeamOf(sender.getUniqueId());
        if (td == null) {
            sender.sendMessage(color("&cDu er ikke i noe team."));
            return;
        }
        String formatted = color(td.color + "&l[Team] &r" + roleDisplay(sender) + " " + sender.getName() + ": &r" + msg);
        Component comp = toComponent(formatted);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (td.members.contains(p.getUniqueId())) {
                p.sendMessage(comp);
            }
        }
    }

    // ------------------------------------------------------------------
    // Team-kommandoer
    // ------------------------------------------------------------------

    private void handleCreateTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(color("&cKun spillere kan opprette team."));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(color("&cBruk: /createteam <teamnavn>"));
            return;
        }
        String name = args[0];
        if (name.length() > 24) {
            sender.sendMessage(color("&cTeamnavnet er for langt (maks 24 tegn)."));
            return;
        }
        if (getTeamOf(p.getUniqueId()) != null) {
            sender.sendMessage(color("&cDu er allerede i et team. Forlat det først med /team leave."));
            return;
        }
        if (teams.containsKey(name.toLowerCase())) {
            sender.sendMessage(color("&cDet finnes allerede et team med det navnet."));
            return;
        }
        String[] palette = {"&a", "&b", "&c", "&d", "&e", "&3", "&9", "&5"};
        String colorCode = palette[teams.size() % palette.length];
        TeamData td = new TeamData(name, p.getUniqueId(), colorCode);
        teams.put(name.toLowerCase(), td);
        saveTeams();
        sender.sendMessage(color("&aTeamet &f" + name + "&a er opprettet!"));
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(color("&cKun spillere kan bruke /team."));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(color("&7Bruk: /team <add|remove|leave|disband|list|info|chat>"));
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                if (teams.isEmpty()) {
                    sender.sendMessage(color("&7Ingen team."));
                    return;
                }
                List<String> msg = new ArrayList<>();
                msg.add(color("&b&lTeamliste:"));
                for (TeamData td : teams.values()) {
                    msg.add(color(td.color + td.name + " &7- &f" + td.members.size() + " medlemmer"));
                }
                sender.sendMessage(String.join("\n", msg));
                return;
            }
            case "info" -> {
                TeamData td = null;
                if (args.length >= 2) {
                    td = teams.get(args[1].toLowerCase());
                } else {
                    td = getTeamOf(p.getUniqueId());
                }
                if (td == null) {
                    sender.sendMessage(color("&cTeamet ble ikke funnet."));
                    return;
                }
                List<String> lines = new ArrayList<>();
                lines.add(color(td.color + "&l" + td.name));
                for (UUID uid : td.members) {
                    Role r = roleOf(uid);
                    String role = r != null ? r.display : "elev";
                    lines.add(color("&7- " + role + " &f" + nameOf(uid) + (uid.equals(td.leader) ? " &e(Leder)" : "")));
                }
                sender.sendMessage(String.join("\n", lines));
                return;
            }
            case "chat" -> {
                if (args.length >= 2 && "on".equalsIgnoreCase(args[1])) {
                    teamChatMode.put(p.getUniqueId(), true);
                    sender.sendMessage(color("&aTeamchat slått på."));
                    return;
                }
                if (args.length >= 2 && "off".equalsIgnoreCase(args[1])) {
                    teamChatMode.put(p.getUniqueId(), false);
                    sender.sendMessage(color("&7Teamchat slått av."));
                    return;
                }
                boolean current = Boolean.TRUE.equals(teamChatMode.get(p.getUniqueId()));
                teamChatMode.put(p.getUniqueId(), !current);
                sender.sendMessage(color(current ? "&7Teamchat slått av." : "&aTeamchat slått på."));
                return;
            }
            case "add" -> {
                TeamData td = getTeamOf(p.getUniqueId());
                if (td == null) {
                    sender.sendMessage(color("&cDu er ikke i noe team."));
                    return;
                }
                if (!td.leader.equals(p.getUniqueId())) {
                    sender.sendMessage(color("&cBare ledere kan legge til medlemmer."));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(color("&cBruk: /team add <spiller>"));
                    return;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(color("&cSpilleren er ikke online."));
                    return;
                }
                if (getTeamOf(target.getUniqueId()) != null) {
                    sender.sendMessage(color("&cDen spilleren er allerede i et team."));
                    return;
                }
                td.members.add(target.getUniqueId());
                saveTeams();
                sender.sendMessage(color("&a" + target.getName() + " ble lagt til i teamet."));
                target.sendMessage(color(td.color + "&l[Team] &rDu ble lagt til i teamet &f" + td.name + "&r."));
                return;
            }
            case "remove" -> {
                TeamData td = getTeamOf(p.getUniqueId());
                if (td == null) {
                    sender.sendMessage(color("&cDu er ikke i noe team."));
                    return;
                }
                if (!td.leader.equals(p.getUniqueId())) {
                    sender.sendMessage(color("&cBare ledere kan fjerne medlemmer."));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(color("&cBruk: /team remove <spiller>"));
                    return;
                }
                UUID uid = findUuid(args[1]);
                if (uid == null || !td.members.contains(uid)) {
                    sender.sendMessage(color("&cFant ikke det medlemmet."));
                    return;
                }
                if (uid.equals(td.leader)) {
                    sender.sendMessage(color("&cDu kan ikke fjerne lederen. Bruk /team disband."));
                    return;
                }
                td.members.remove(uid);
                saveTeams();
                Player target = Bukkit.getPlayer(uid);
                String name = target != null ? target.getName() : args[1];
                sender.sendMessage(color("&a" + name + " ble fjernet fra teamet."));
                if (target != null) {
                    target.sendMessage(color(td.color + "&l[Team] &rDu ble fjernet fra teamet &f" + td.name + "&r."));
                }
                return;
            }
            case "leave" -> {
                TeamData td = getTeamOf(p.getUniqueId());
                if (td == null) {
                    sender.sendMessage(color("&cDu er ikke i noe team."));
                    return;
                }
                if (td.leader.equals(p.getUniqueId())) {
                    sender.sendMessage(color("&cLedere kan ikke forlate teamet. Bruk /team disband."));
                    return;
                }
                td.members.remove(p.getUniqueId());
                saveTeams();
                sender.sendMessage(color("&aDu forlot teamet &f" + td.name + "&a."));
                return;
            }
            case "disband" -> {
                TeamData td = getTeamOf(p.getUniqueId());
                if (td == null) {
                    sender.sendMessage(color("&cDu er ikke i noe team."));
                    return;
                }
                if (!td.leader.equals(p.getUniqueId())) {
                    sender.sendMessage(color("&cBare ledere kan oppløse teamet."));
                    return;
                }
                String teamName = td.name;
                String teamColor = td.color;
                List<UUID> membersCopy = new ArrayList<>(td.members);
                teams.remove(td.name.toLowerCase());
                saveTeams();
                for (UUID uid : membersCopy) {
                    Player member = Bukkit.getPlayer(uid);
                    if (member != null) {
                        member.sendMessage(color(teamColor + "&l[Team] &f" + teamName + " &rble oppløst."));
                    }
                }
                sender.sendMessage(color("&aTeamet &f" + teamName + "&a ble oppløst."));
                return;
            }
            default -> {
                sender.sendMessage(color("&cUkjent teamkommando."));
            }
        }
    }

    // ------------------------------------------------------------------
    // Kommandoer
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "hologram" -> {
                handleHologram(sender, args);
                return true;
            }
            case "spawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Kun spillere kan bruke /spawn.");
                    return true;
                }
                World w = safeWorld != null ? safeWorld : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) {
                    sender.sendMessage(color("&cFant ingen verden."));
                    return true;
                }
                p.teleport(w.getSpawnLocation());
                p.sendActionBar(toComponent(color("&aTeleportert til spawn.")));
                return true;
            }
            case "rtp" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Kun spillere kan bruke /rtp.");
                    return true;
                }
                randomTeleport(p);
                return true;
            }
            case "stats" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Kun spillere kan bruke /stats.");
                    return true;
                }
                int k = getKills(p.getUniqueId());
                int d = getDeaths(p.getUniqueId());
                double kd = d == 0 ? k : (double) k / d;
                String role = roleDisplay(p);
                sender.sendMessage(String.join("\n",
                        color("&b\u00A7l-- Dine stats --"),
                        color("&aDrap: &f" + k + "   &cD\u00F8dsfall: &f" + d
                                + "   &6D\u00F8d/drap: &f" + String.format("%.1f", kd)),
                        color("&7Rolle: ") + role));
                return true;
            }
            case "nightvision" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Kun spillere kan bruke /nightvision.");
                    return true;
                }
                toggleNightVision(p);
                return true;
            }
            case "topp" -> {
                int n = 10;
                if (args.length > 0) {
                    try {
                        n = Math.max(1, Math.min(20, Integer.parseInt(args[0])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                List<ConfigEntry> top = topList(n);
                List<String> msg = new ArrayList<>();
                msg.add(color("&b\u00A7l\u00A7nTangen Toppliste (drap)"));
                if (top.isEmpty()) {
                    msg.add(color("&7Ingen stats enn\u00E5."));
                }
                int rank = 1;
                for (ConfigEntry e : top) {
                    String name = e.name() != null ? e.name() : nameOf(e.uuid());
                    String role = roleDisplayOf(e.uuid());
                    msg.add(color("&7#" + rank + " &f" + name + " &8["
                            + (role != null ? role : "\u00A77?") + "&8] &7- &a"
                            + e.kills() + "&7 drap &c" + e.deaths() + "&7 d\u00F8d"));
                    rank++;
                }
                msg.add(color("&7&oBruker /topp <antall> for flere."));
                sender.sendMessage(String.join("\n", msg));
                return true;
            }
            case "role" -> {
                if (args.length == 0) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("Bruk: /role give <spiller> <rolle>");
                        return true;
                    }
                    sender.sendMessage("&7Din rolle: &r".replace("&", "\u00A7") + roleDisplay(p));
                    return true;
                }
                if (args.length >= 1 && "give".equalsIgnoreCase(args[0])) {
                    if (args.length < 3) {
                        sender.sendMessage(color("&cBruk: /role give <spiller> <owner|laerer|mod|elev>"));
                        return true;
                    }
                    boolean allowed = !(sender instanceof Player) || sender.hasPermission(PERM_ROLE) || sender.isOp();
                    if (!allowed) {
                        sender.sendMessage(color("&cDu har ikke lov til \u00E5 endre roller."));
                        return true;
                    }
                    Role target = Role.byGroup(args[2]);
                    if (target == null) {
                        sender.sendMessage(color("&cUkjent rolle. Velg: " + String.join(", ", Role.names())));
                        return true;
                    }
                    UUID tid = findUuid(args[1]);
                    if (tid == null) {
                        sender.sendMessage(color("&cFant ikke spilleren '" + args[1] + "'."));
                        return true;
                    }
                    Player onlineTarget = null;
                    for (Player po : Bukkit.getOnlinePlayers()) {
                        if (po.getUniqueId().equals(tid)) {
                            onlineTarget = po;
                            break;
                        }
                    }
                    final Player targetOnline = onlineTarget;
                    final String cmdTarget = args[1];
                    getLogger().info("Rollekommando: " + sender.getName() + " setter " + cmdTarget + " (" + tid + ") -> " + target.group);
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        try {
                            User user = luckPerms.getUserManager().loadUser(tid).get(10, TimeUnit.SECONDS);
                            for (Role r : Role.values()) {
                                user.data().remove(groupNode(r.group));
                            }
                            user.data().add(groupNode(target.group));
                            user.setPrimaryGroup(target.group);
                            luckPerms.getUserManager().saveUser(user).get(10, TimeUnit.SECONDS);
                            getLogger().info("Rolle satt: " + cmdTarget + " -> " + target.group);
                            Player online = targetOnline;
                            if (online == null) {
                                online = Bukkit.getPlayer(tid);
                            }
                            final Player targetP = online;
                            Bukkit.getScheduler().runTask(this, () -> {
                                if (targetP != null) {
                                    updateListName(targetP);
                                    updateSidebar(targetP);
                                    refreshTabForAll();
                                }
                            });
                            sender.sendMessage(color("&a" + cmdTarget + " har n\u00E5 rollen &r")
                                    + target.display + color("&a."));
                        } catch (Exception e) {
                            getLogger().severe("Feil ved rolleendring for " + cmdTarget + ": " + e.getMessage());
                            e.printStackTrace();
                            sender.sendMessage(color("&cFeil ved rolleendring: " + e.getMessage()));
                        }
                    });
                    return true;
                }
                sender.sendMessage(color("&cBruk: /role give <spiller> <rolle>"));
                return true;
            }
            case "sone" -> {
                if (!(sender instanceof Player p)) {
                    if (args.length >= 1) {
                        return handleZoneChange(sender, args);
                    }
                    sender.sendMessage("Bruk: /sone sett <antal> | /sone fjerne | /sone aktivere");
                    return true;
                }
                if (args.length == 0) {
                    if (safeWorld == null) {
                        p.sendMessage(color("&cSonen er ikke satt opp."));
                        return true;
                    }
                    toggleSafeBorder(p);
                    return true;
                }
                return handleZoneChange(p, args);
            }
            case "team" -> {
                handleTeam(sender, args);
                return true;
            }
            case "createteam" -> {
                handleCreateTeam(sender, args);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private UUID findUuid(String name) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p.getUniqueId();
            }
        }
        ConfigurationSection sec = stats.getConfigurationSection("players");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID u = parseUuid(key);
                if (u == null) {
                    continue;
                }
                String n = sec.getString(key + ".name");
                if (n != null && n.equalsIgnoreCase(name)) {
                    return u;
                }
            }
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off != null) {
            UUID u = off.getUniqueId();
            if (u != null) {
                return u;
            }
        }
        return null;
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String n = op.getName();
        if (n == null && op.isOnline() && op instanceof Player p) {
            n = p.getName();
        }
        return n != null ? n : uuid.toString().substring(0, 8);
    }
}