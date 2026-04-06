package fr.mrjuju0319.pvoteparty.vote;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class VoteService {

    private final JavaPlugin plugin;
    private final VoteStorage storage;
    private String serverName;
    private String noOnlinePlayersMessage;
    private boolean master;

    private int votePartyGoal;
    private List<String> voteRewards;
    private List<String> partyRewards;
    private List<String> partyGlobalRewards;
    private List<String> partyPlayerRewards;

    public VoteService(JavaPlugin plugin, VoteConfig config, VoteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.serverName = config.serverName();
        this.noOnlinePlayersMessage = config.noOnlinePlayersMessage();
        this.master = config.master();
        this.votePartyGoal = config.votePartyGoal();
        this.voteRewards = config.voteRewards();
        this.partyRewards = config.partyRewards();
        this.partyGlobalRewards = config.partyGlobalRewards();
        this.partyPlayerRewards = config.partyPlayerRewards();
    }

    public void initializeRuntimeConfig() {
        if (master) {
            storage.upsertSharedConfig(new VoteConfig(
                    serverName,
                    votePartyGoal,
                    voteRewards,
                    partyRewards,
                    partyGlobalRewards,
                    partyPlayerRewards,
                    noOnlinePlayersMessage,
                    "",
                    null,
                    true,
                    5
            ));
        } else {
            applySharedConfig();
        }
    }

    public void applySharedConfig() {
        VoteStorage.SharedConfig shared = storage.loadSharedConfig();
        if (shared.goal() != null && shared.goal() > 0) {
            votePartyGoal = shared.goal();
        }
        if (!shared.voteRewards().isEmpty()) {
            voteRewards = shared.voteRewards();
        }
        if (!shared.partyRewards().isEmpty()) {
            partyRewards = shared.partyRewards();
        }
        if (!shared.partyGlobalRewards().isEmpty()) {
            partyGlobalRewards = shared.partyGlobalRewards();
        }
        if (!shared.partyPlayerRewards().isEmpty()) {
            partyPlayerRewards = shared.partyPlayerRewards();
        }
    }

    public void reloadFromConfig(VoteConfig config) {
        this.serverName = config.serverName();
        this.noOnlinePlayersMessage = config.noOnlinePlayersMessage();
        this.master = config.master();
        this.votePartyGoal = config.votePartyGoal();
        this.voteRewards = config.voteRewards();
        this.partyRewards = config.partyRewards();
        this.partyGlobalRewards = config.partyGlobalRewards();
        this.partyPlayerRewards = config.partyPlayerRewards();

        if (master) {
            storage.upsertSharedConfig(config);
        } else {
            applySharedConfig();
        }
    }

    public void addVote(String playerName, int amount) {
        storage.incrementVotes(playerName, amount);
        int party = storage.incrementPartyProgress(amount);

        dispatchVoteRewards(playerName, amount);

        if (party >= votePartyGoal) {
            triggerPartyRewards();
            storage.setPartyProgress(0);
        }
    }

    private void dispatchVoteRewards(String playerName, int amount) {
        for (int i = 0; i < amount; i++) {
            for (String command : voteRewards) {
                queueOrDispatch(playerName, command);
            }
        }
    }

    public void triggerPartyRewards() {
        for (String command : partyGlobalRewards) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        for (String command : partyPlayerRewards) {
            dispatchPartyPlayerCommandWithPermission(command);
        }

        for (String command : partyRewards) {
            if (command.contains("{player}")) {
                dispatchPartyPlayerCommand(command);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    public void dispatchPartyPlayerCommandWithPermission(String rawCommand) {
        PermissionCommand permissionCommand = parsePermissionCommand(rawCommand);
        String command = permissionCommand.command();
        if (!command.contains("{player}")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return;
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (permissionCommand.permission() == null || online.hasPermission(permissionCommand.permission())) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", online.getName()));
            }
        }
    }

    public void dispatchPartyPlayerCommand(String command) {
        boolean anyOnline = false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            anyOnline = true;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", online.getName()));
        }
        if (!anyOnline) {
            plugin.getLogger().warning(stripLegacyColors(color(noOnlinePlayersMessage)));
        }
    }

    private void queueOrDispatch(String playerName, String command) {
        String parsed = command.replace("{player}", playerName);
        Player local = Bukkit.getPlayerExact(playerName);
        if (local != null && local.isOnline()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        } else {
            storage.enqueuePendingReward(playerName, parsed);
        }
    }

    public void flushPendingForPlayer(String playerName) {
        List<String> pending = storage.popPendingRewards(playerName);
        for (String command : pending) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    public void heartbeatOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            storage.setOnlineServer(player.getName(), serverName);
        }
    }

    public void setOnlinePlayer(String playerName) {
        storage.setOnlineServer(playerName, serverName);
    }

    public void clearOnlinePlayer(String playerName) {
        storage.clearOnlineServer(playerName, serverName);
    }

    public List<String> getKnownOnlinePlayers() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(storage.getOnlinePlayers());
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return List.copyOf(names);
    }

    public int getVotes(String playerName) {
        return storage.getVotes(playerName);
    }

    public VotePlayerStats getStats(String playerName) {
        return storage.getStats(playerName);
    }

    public int getPartyProgress() {
        return storage.getPartyProgress();
    }

    public int getPartyGoal() {
        return votePartyGoal;
    }

    public void setPallier(String playerName, String pallier, boolean value) {
        storage.setPallier(playerName, pallier, value);
    }

    public boolean getPallier(String playerName, String pallier) {
        return storage.getPallier(playerName, pallier);
    }

    public void resetPallier(String playerOrAll, String pallierOrAll) {
        if ("all".equalsIgnoreCase(playerOrAll)) {
            storage.resetPalliersForAllPlayers(pallierOrAll);
            return;
        }

        if ("all".equalsIgnoreCase(pallierOrAll)) {
            storage.resetAllPalliers(playerOrAll);
        } else {
            storage.resetPallier(playerOrAll, pallierOrAll);
        }
    }

    public boolean resetVotes(String periodInput, String playerOrAll) {
        String period = normalizePeriod(periodInput);
        if (period == null) {
            return false;
        }

        if ("all".equalsIgnoreCase(playerOrAll)) {
            storage.resetVotesForAllPlayers(period);
        } else {
            storage.resetVotesForPlayer(playerOrAll, period);
        }
        return true;
    }

    public String color(String message) {
        return message == null ? "" : message.replace('&', '§');
    }

    private String stripLegacyColors(String input) {
        return input == null ? "" : input.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    private String normalizePeriod(String input) {
        if (input == null) {
            return null;
        }
        String value = input.toLowerCase();
        return switch (value) {
            case "day", "days", "jour" -> "day";
            case "week", "hebdo", "semaine" -> "week";
            case "month", "mois", "mensuel" -> "month";
            case "total", "totals" -> "total";
            default -> null;
        };
    }

    private PermissionCommand parsePermissionCommand(String rawCommand) {
        if (rawCommand == null) {
            return new PermissionCommand(null, "");
        }

        String trimmed = rawCommand.trim();
        if (!trimmed.startsWith("[perm:")) {
            return new PermissionCommand(null, trimmed);
        }

        int end = trimmed.indexOf(']');
        if (end <= 6) {
            return new PermissionCommand(null, trimmed);
        }

        String permission = trimmed.substring(6, end).trim();
        String command = trimmed.substring(end + 1).trim();
        if (permission.isEmpty()) {
            return new PermissionCommand(null, command);
        }
        return new PermissionCommand(permission, command);
    }

    private record PermissionCommand(String permission, String command) {
    }

    public void tickSync(boolean pullSharedConfig) {
        heartbeatOnlinePlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            flushPendingForPlayer(player.getName());
        }
        if (pullSharedConfig) {
            applySharedConfig();
        }
        storage.flush();
    }

    public void shutdown() {
        storage.close();
    }
}
