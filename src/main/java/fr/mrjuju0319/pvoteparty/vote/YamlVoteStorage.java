package fr.mrjuju0319.pvoteparty.vote;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class YamlVoteStorage implements VoteStorage {

    private final File file;
    private final Map<String, Integer> votes = new HashMap<>();
    private final Map<String, List<String>> pending = new HashMap<>();
    private final Map<String, String> online = new HashMap<>();
    private final Map<String, Map<String, Boolean>> palliers = new HashMap<>();
    private final Map<String, StatsState> stats = new HashMap<>();
    private final Map<String, Object> shared = new HashMap<>();
    private int partyProgress;
    private boolean dirty;

    public YamlVoteStorage(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        load();
    }

    private String key(String v) {
        return v.toLowerCase(Locale.ROOT);
    }

    private synchronized void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        partyProgress = yaml.getInt("party-progress", 0);

        ConfigurationSection profiles = yaml.getConfigurationSection("profiles");
        if (profiles != null) {
            for (String name : profiles.getKeys(false)) {
                String base = "profiles." + name;
                votes.put(name, yaml.getInt(base + ".votes", 0));
                stats.put(name, StatsState.fromYaml(yaml, base + ".stats"));
            }
        }

        ConfigurationSection p = yaml.getConfigurationSection("pending");
        if (p != null) {
            for (String name : p.getKeys(false)) {
                pending.put(name, new ArrayList<>(yaml.getStringList("pending." + name)));
            }
        }

        ConfigurationSection on = yaml.getConfigurationSection("online");
        if (on != null) {
            for (String name : on.getKeys(false)) {
                online.put(name, yaml.getString("online." + name, ""));
            }
        }

        ConfigurationSection pa = yaml.getConfigurationSection("palliers");
        if (pa != null) {
            for (String player : pa.getKeys(false)) {
                ConfigurationSection playerSection = pa.getConfigurationSection(player);
                if (playerSection == null) {
                    continue;
                }
                Map<String, Boolean> byPallier = new HashMap<>();
                for (String pallier : playerSection.getKeys(false)) {
                    byPallier.put(pallier, yaml.getBoolean("palliers." + player + "." + pallier, false));
                }
                palliers.put(player, byPallier);
            }
        }

        shared.put("goal", yaml.get("shared-config.goal"));
        shared.put("vote-rewards", yaml.getStringList("shared-config.vote-rewards"));
        shared.put("party-rewards", yaml.getStringList("shared-config.party-rewards"));
    }

    @Override
    public synchronized int incrementVotes(String playerName, int amount) {
        String p = key(playerName);
        int value = votes.getOrDefault(p, 0) + amount;
        votes.put(p, value);

        StatsState state = stats.computeIfAbsent(p, x -> StatsState.empty());
        state.increment(amount);
        dirty = true;
        return value;
    }

    @Override
    public synchronized int incrementPartyProgress(int amount) {
        partyProgress += amount;
        dirty = true;
        return partyProgress;
    }

    @Override
    public synchronized int getPartyProgress() {
        return partyProgress;
    }

    @Override
    public synchronized void setPartyProgress(int value) {
        partyProgress = Math.max(0, value);
        dirty = true;
    }

    @Override
    public synchronized int getVotes(String playerName) {
        return votes.getOrDefault(key(playerName), 0);
    }

    @Override
    public synchronized VotePlayerStats getStats(String playerName) {
        return stats.getOrDefault(key(playerName), StatsState.empty()).toPublic();
    }

    @Override
    public synchronized void enqueuePendingReward(String playerName, String command) {
        pending.computeIfAbsent(key(playerName), x -> new ArrayList<>()).add(command);
        dirty = true;
    }

    @Override
    public synchronized List<String> popPendingRewards(String playerName) {
        List<String> list = pending.remove(key(playerName));
        dirty = true;
        return list == null ? List.of() : list;
    }

    @Override
    public synchronized void setOnlineServer(String playerName, String serverName) {
        online.put(key(playerName), serverName);
        dirty = true;
    }

    @Override
    public synchronized void clearOnlineServer(String playerName, String serverName) {
        String k = key(playerName);
        if (serverName.equalsIgnoreCase(online.getOrDefault(k, ""))) {
            online.remove(k);
            dirty = true;
        }
    }

    @Override
    public synchronized String getOnlineServer(String playerName) {
        return online.get(key(playerName));
    }

    @Override
    public synchronized Map<String, Integer> topVotes(int limit) {
        return votes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }

    @Override
    public synchronized void setPallier(String playerName, String pallier, boolean value) {
        String player = key(playerName);
        String pal = key(pallier);
        palliers.computeIfAbsent(player, k -> new HashMap<>()).put(pal, value);
        dirty = true;
    }

    @Override
    public synchronized boolean getPallier(String playerName, String pallier) {
        String player = key(playerName);
        String pal = key(pallier);
        return palliers.getOrDefault(player, Map.of()).getOrDefault(pal, false);
    }

    @Override
    public synchronized void resetPallier(String playerName, String pallier) {
        String player = key(playerName);
        String pal = key(pallier);
        Map<String, Boolean> byPallier = palliers.get(player);
        if (byPallier != null) {
            byPallier.remove(pal);
            if (byPallier.isEmpty()) {
                palliers.remove(player);
            }
            dirty = true;
        }
    }

    @Override
    public synchronized void resetAllPalliers(String playerName) {
        palliers.remove(key(playerName));
        dirty = true;
    }


    @Override
    public synchronized void resetPalliersForAllPlayers(String pallierOrAll) {
        if ("all".equalsIgnoreCase(pallierOrAll)) {
            palliers.clear();
            dirty = true;
            return;
        }

        String pal = key(pallierOrAll);
        boolean changed = false;
        List<String> emptyPlayers = new ArrayList<>();
        for (Map.Entry<String, Map<String, Boolean>> entry : palliers.entrySet()) {
            Map<String, Boolean> byPallier = entry.getValue();
            if (byPallier.remove(pal) != null) {
                changed = true;
            }
            if (byPallier.isEmpty()) {
                emptyPlayers.add(entry.getKey());
            }
        }
        for (String player : emptyPlayers) {
            palliers.remove(player);
        }
        if (changed || !emptyPlayers.isEmpty()) {
            dirty = true;
        }
    }

    @Override
    public synchronized void upsertSharedConfig(VoteConfig config) {
        shared.put("goal", config.votePartyGoal());
        shared.put("vote-rewards", config.voteRewards());
        shared.put("party-rewards", config.partyRewards());
        dirty = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized SharedConfig loadSharedConfig() {
        Integer goal = shared.get("goal") instanceof Integer g ? g : null;
        List<String> voteRewards = (List<String>) shared.getOrDefault("vote-rewards", List.of());
        List<String> partyRewards = (List<String>) shared.getOrDefault("party-rewards", List.of());
        return new SharedConfig(goal, voteRewards, partyRewards);
    }

    @Override
    public synchronized void flush() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("party-progress", partyProgress);

        votes.forEach((name, count) -> {
            yaml.set("profiles." + name + ".votes", count);
            stats.getOrDefault(name, StatsState.empty()).toYaml(yaml, "profiles." + name + ".stats");
        });

        pending.forEach((name, commands) -> yaml.set("pending." + name, commands));
        online.forEach((name, server) -> yaml.set("online." + name, server));
        palliers.forEach((player, map) -> map.forEach((pallier, value) -> yaml.set("palliers." + player + "." + pallier, value)));

        yaml.set("shared-config.goal", shared.get("goal"));
        yaml.set("shared-config.vote-rewards", shared.getOrDefault("vote-rewards", List.of()));
        yaml.set("shared-config.party-rewards", shared.getOrDefault("party-rewards", List.of()));

        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de sauvegarder votes.yml", e);
        }
    }

    @Override
    public synchronized void close() {
        flush();
    }

    private static final class StatsState {
        private int day;
        private int week;
        private int month;
        private int year;
        private int total;
        private String dayKey;
        private String weekKey;
        private String monthKey;
        private String yearKey;

        static StatsState empty() {
            StatsState s = new StatsState();
            LocalDate now = LocalDate.now();
            s.dayKey = now.toString();
            s.weekKey = weekKey(now);
            s.monthKey = now.getYear() + "-" + now.getMonthValue();
            s.yearKey = String.valueOf(now.getYear());
            return s;
        }

        static StatsState fromYaml(YamlConfiguration yaml, String path) {
            StatsState s = empty();
            s.day = yaml.getInt(path + ".day", 0);
            s.week = yaml.getInt(path + ".week", 0);
            s.month = yaml.getInt(path + ".month", 0);
            s.year = yaml.getInt(path + ".year", 0);
            s.total = yaml.getInt(path + ".total", 0);
            s.dayKey = yaml.getString(path + ".day-key", s.dayKey);
            s.weekKey = yaml.getString(path + ".week-key", s.weekKey);
            s.monthKey = yaml.getString(path + ".month-key", s.monthKey);
            s.yearKey = yaml.getString(path + ".year-key", s.yearKey);
            return s;
        }

        void increment(int value) {
            LocalDate now = LocalDate.now();
            String d = now.toString();
            String w = weekKey(now);
            String m = now.getYear() + "-" + now.getMonthValue();
            String y = String.valueOf(now.getYear());

            if (!d.equals(dayKey)) {
                day = 0;
                dayKey = d;
            }
            if (!w.equals(weekKey)) {
                week = 0;
                weekKey = w;
            }
            if (!m.equals(monthKey)) {
                month = 0;
                monthKey = m;
            }
            if (!y.equals(yearKey)) {
                year = 0;
                yearKey = y;
            }

            day += value;
            week += value;
            month += value;
            year += value;
            total += value;
        }

        VotePlayerStats toPublic() {
            return new VotePlayerStats(day, week, month, year, total);
        }

        void toYaml(YamlConfiguration yaml, String path) {
            yaml.set(path + ".day", day);
            yaml.set(path + ".week", week);
            yaml.set(path + ".month", month);
            yaml.set(path + ".year", year);
            yaml.set(path + ".total", total);
            yaml.set(path + ".day-key", dayKey);
            yaml.set(path + ".week-key", weekKey);
            yaml.set(path + ".month-key", monthKey);
            yaml.set(path + ".year-key", yearKey);
        }

        private static String weekKey(LocalDate now) {
            WeekFields wf = WeekFields.ISO;
            return now.getYear() + "-W" + now.get(wf.weekOfWeekBasedYear());
        }
    }
}
