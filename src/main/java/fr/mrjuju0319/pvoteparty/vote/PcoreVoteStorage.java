package fr.mrjuju0319.pvoteparty.vote;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Managed mode via p-core DbService.
 *
 * Compatible avec result rows sous forme Map OU dev.paracraft.pcore.api.Row.
 */
public class PcoreVoteStorage implements VoteStorage {

    private final Object pcoreApi;
    private final String pluginId;

    public PcoreVoteStorage(Object pcoreApi, String pluginId) {
        this.pcoreApi = Objects.requireNonNull(pcoreApi, "pcoreApi");
        this.pluginId = pluginId;
        bootstrap();
    }

    @SuppressWarnings("unchecked")
    private int exec(String sql, List<Object> params) {
        try {
            Object dbService = pcoreApi.getClass().getMethod("db").invoke(pcoreApi);
            Object cf = dbService.getClass().getMethod("execute", String.class, String.class, List.class)
                    .invoke(dbService, pluginId, sql, params);
            return ((CompletableFuture<Integer>) cf).join();
        } catch (Exception e) {
            throw new IllegalStateException("p-core DbService execute failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> queryRows(String sql, List<Object> params) {
        try {
            Object dbService = pcoreApi.getClass().getMethod("db").invoke(pcoreApi);
            Object cf = dbService.getClass().getMethod("query", String.class, String.class, List.class)
                    .invoke(dbService, pluginId, sql, params);
            Object result = ((CompletableFuture<Object>) cf).join();
            if (result instanceof List<?> list) {
                return (List<Object>) list;
            }
            return List.of();
        } catch (Exception e) {
            throw new IllegalStateException("p-core DbService query failed", e);
        }
    }

    private Object rowGet(Object row, String column) {
        if (row == null) return null;

        if (row instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && column.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                    return e.getValue();
                }
            }
            return null;
        }

        try {
            Method get = row.getClass().getMethod("get", String.class);
            return get.invoke(row, column);
        } catch (Exception ignored) {
        }

        try {
            Method asMap = row.getClass().getMethod("asMap");
            Object raw = asMap.invoke(row);
            if (raw instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null && column.equalsIgnoreCase(String.valueOf(e.getKey()))) {
                        return e.getValue();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void bootstrap() {
        exec("CREATE TABLE IF NOT EXISTS vp_profiles (player_name VARCHAR(32) PRIMARY KEY, votes INT NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_state (k VARCHAR(64) PRIMARY KEY, v TEXT NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_pending (id BIGINT AUTO_INCREMENT PRIMARY KEY, player_name VARCHAR(32) NOT NULL, command TEXT NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_online (player_name VARCHAR(32) PRIMARY KEY, server_name VARCHAR(64) NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_player_palliers (player_name VARCHAR(32) NOT NULL, name VARCHAR(64) NOT NULL, enabled BOOLEAN NOT NULL, PRIMARY KEY(player_name, name))", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_stats (player_name VARCHAR(32) PRIMARY KEY, day_count INT NOT NULL, week_count INT NOT NULL, month_count INT NOT NULL, year_count INT NOT NULL, total_count INT NOT NULL, day_key VARCHAR(16) NOT NULL, week_key VARCHAR(16) NOT NULL, month_key VARCHAR(16) NOT NULL, year_key VARCHAR(8) NOT NULL)", List.of());
    }

    private String key(String n) {
        return n.toLowerCase(Locale.ROOT);
    }

    private String currentWeekKey(LocalDate now) {
        WeekFields wf = WeekFields.ISO;
        return now.get(wf.weekBasedYear()) + "-W" + now.get(wf.weekOfWeekBasedYear());
    }

    @Override
    public synchronized int incrementVotes(String playerName, int amount) {
        String p = key(playerName);
        exec("INSERT INTO vp_profiles(player_name, votes) VALUES (?, ?) ON DUPLICATE KEY UPDATE votes = votes + VALUES(votes)", List.of(p, amount));
        incrementStats(p, amount);
        return getVotes(p);
    }

    private void incrementStats(String player, int amount) {
        LocalDate now = LocalDate.now();
        String dayKey = now.toString();
        String weekKey = currentWeekKey(now);
        String monthKey = now.getYear() + "-" + now.getMonthValue();
        String yearKey = String.valueOf(now.getYear());

        List<Object> rows = queryRows("SELECT day_count,week_count,month_count,year_count,total_count,day_key,week_key,month_key,year_key FROM vp_stats WHERE player_name=?", List.of(player));

        int oldDay = 0, oldWeek = 0, oldMonth = 0, oldYear = 0, oldTotal = 0;
        String oldDayKey = dayKey, oldWeekKey = weekKey, oldMonthKey = monthKey, oldYearKey = yearKey;
        if (!rows.isEmpty()) {
            Object r = rows.get(0);
            oldDay = toInt(rowGet(r, "day_count"));
            oldWeek = toInt(rowGet(r, "week_count"));
            oldMonth = toInt(rowGet(r, "month_count"));
            oldYear = toInt(rowGet(r, "year_count"));
            oldTotal = toInt(rowGet(r, "total_count"));
            oldDayKey = String.valueOf(rowGet(r, "day_key"));
            oldWeekKey = String.valueOf(rowGet(r, "week_key"));
            oldMonthKey = String.valueOf(rowGet(r, "month_key"));
            oldYearKey = String.valueOf(rowGet(r, "year_key"));
        }

        int day = dayKey.equals(oldDayKey) ? oldDay : 0;
        int week = weekKey.equals(oldWeekKey) ? oldWeek : 0;
        int month = monthKey.equals(oldMonthKey) ? oldMonth : 0;
        int year = yearKey.equals(oldYearKey) ? oldYear : 0;
        int total = oldTotal + amount;

        exec("INSERT INTO vp_stats(player_name,day_count,week_count,month_count,year_count,total_count,day_key,week_key,month_key,year_key) VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE day_count=?,week_count=?,month_count=?,year_count=?,total_count=?,day_key=?,week_key=?,month_key=?,year_key=?",
                List.of(player, day + amount, week + amount, month + amount, year + amount, total, dayKey, weekKey, monthKey, yearKey,
                        day + amount, week + amount, month + amount, year + amount, total, dayKey, weekKey, monthKey, yearKey));
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(o));
    }

    @Override
    public synchronized int incrementPartyProgress(int amount) {
        int current = getPartyProgress() + amount;
        setPartyProgress(current);
        return current;
    }

    @Override
    public synchronized int getPartyProgress() {
        List<Object> rows = queryRows("SELECT v FROM vp_state WHERE k='party-progress'", List.of());
        return rows.isEmpty() ? 0 : Integer.parseInt(String.valueOf(rowGet(rows.get(0), "v")));
    }

    @Override
    public synchronized void setPartyProgress(int value) {
        upsertState("party-progress", String.valueOf(Math.max(0, value)));
    }

    @Override
    public synchronized int getVotes(String playerName) {
        List<Object> rows = queryRows("SELECT votes FROM vp_profiles WHERE player_name=?", List.of(key(playerName)));
        return rows.isEmpty() ? 0 : toInt(rowGet(rows.get(0), "votes"));
    }

    @Override
    public synchronized VotePlayerStats getStats(String playerName) {
        String player = key(playerName);
        normalizeStatsForPlayer(player);
        List<Object> rows = queryRows("SELECT day_count,week_count,month_count,year_count,total_count FROM vp_stats WHERE player_name=?", List.of(player));
        if (rows.isEmpty()) return VotePlayerStats.empty();
        Object r = rows.get(0);
        return new VotePlayerStats(toInt(rowGet(r, "day_count")), toInt(rowGet(r, "week_count")), toInt(rowGet(r, "month_count")), toInt(rowGet(r, "year_count")), toInt(rowGet(r, "total_count")));
    }

    private void normalizeStatsForPlayer(String player) {
        LocalDate now = LocalDate.now();
        String dayKey = now.toString();
        String weekKey = currentWeekKey(now);
        String monthKey = now.getYear() + "-" + now.getMonthValue();
        String yearKey = String.valueOf(now.getYear());

        List<Object> rows = queryRows("SELECT day_count,week_count,month_count,year_count,total_count,day_key,week_key,month_key,year_key FROM vp_stats WHERE player_name=?", List.of(player));
        if (rows.isEmpty()) {
            return;
        }

        Object row = rows.get(0);
        int day = dayKey.equals(String.valueOf(rowGet(row, "day_key"))) ? toInt(rowGet(row, "day_count")) : 0;
        int week = weekKey.equals(String.valueOf(rowGet(row, "week_key"))) ? toInt(rowGet(row, "week_count")) : 0;
        int month = monthKey.equals(String.valueOf(rowGet(row, "month_key"))) ? toInt(rowGet(row, "month_count")) : 0;
        int year = yearKey.equals(String.valueOf(rowGet(row, "year_key"))) ? toInt(rowGet(row, "year_count")) : 0;
        int total = toInt(rowGet(row, "total_count"));

        exec("UPDATE vp_stats SET day_count=?,week_count=?,month_count=?,year_count=?,total_count=?,day_key=?,week_key=?,month_key=?,year_key=? WHERE player_name=?",
                List.of(day, week, month, year, total, dayKey, weekKey, monthKey, yearKey, player));
    }

    @Override
    public synchronized void enqueuePendingReward(String playerName, String command) {
        exec("INSERT INTO vp_pending(player_name, command) VALUES (?,?)", List.of(key(playerName), command));
    }

    @Override
    public synchronized List<String> popPendingRewards(String playerName) {
        String p = key(playerName);
        List<Object> rows = queryRows("SELECT id, command FROM vp_pending WHERE player_name=? ORDER BY id ASC", List.of(p));
        List<String> out = new ArrayList<>();
        for (Object row : rows) {
            long id = Long.parseLong(String.valueOf(rowGet(row, "id")));
            out.add(String.valueOf(rowGet(row, "command")));
            exec("DELETE FROM vp_pending WHERE id=?", List.of(id));
        }
        return out;
    }

    @Override
    public synchronized void setOnlineServer(String playerName, String serverName) {
        exec("INSERT INTO vp_online(player_name, server_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE server_name=VALUES(server_name)", List.of(key(playerName), serverName));
    }

    @Override
    public synchronized void clearOnlineServer(String playerName, String serverName) {
        exec("DELETE FROM vp_online WHERE player_name=? AND server_name=?", List.of(key(playerName), serverName));
    }

    @Override
    public synchronized String getOnlineServer(String playerName) {
        List<Object> rows = queryRows("SELECT server_name FROM vp_online WHERE player_name=?", List.of(key(playerName)));
        return rows.isEmpty() ? null : String.valueOf(rowGet(rows.get(0), "server_name"));
    }

    @Override
    public synchronized Map<String, Integer> topVotes(int limit) {
        List<Object> rows = queryRows("SELECT player_name, votes FROM vp_profiles ORDER BY votes DESC LIMIT ?", List.of(limit));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Object row : rows) {
            out.put(String.valueOf(rowGet(row, "player_name")), toInt(rowGet(row, "votes")));
        }
        return out;
    }

    @Override
    public synchronized void setPallier(String playerName, String pallier, boolean value) {
        exec("INSERT INTO vp_player_palliers(player_name, name, enabled) VALUES (?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)", List.of(key(playerName), key(pallier), value));
    }

    @Override
    public synchronized boolean getPallier(String playerName, String pallier) {
        List<Object> rows = queryRows("SELECT enabled FROM vp_player_palliers WHERE player_name=? AND name=?", List.of(key(playerName), key(pallier)));
        return !rows.isEmpty() && Boolean.parseBoolean(String.valueOf(rowGet(rows.get(0), "enabled")));
    }

    @Override
    public synchronized void resetVotesForPlayer(String playerName, String period) {
        String player = key(playerName);
        LocalDate now = LocalDate.now();
        switch (period) {
            case "day" -> exec("UPDATE vp_stats SET day_count=0, day_key=? WHERE player_name=?", List.of(now.toString(), player));
            case "week" -> exec("UPDATE vp_stats SET week_count=0, week_key=? WHERE player_name=?", List.of(currentWeekKey(now), player));
            case "month" -> exec("UPDATE vp_stats SET month_count=0, month_key=? WHERE player_name=?", List.of(now.getYear() + "-" + now.getMonthValue(), player));
            case "total" -> {
                exec("UPDATE vp_profiles SET votes=0 WHERE player_name=?", List.of(player));
                exec("UPDATE vp_stats SET total_count=0 WHERE player_name=?", List.of(player));
            }
            default -> {
            }
        }
    }

    @Override
    public synchronized void resetVotesForAllPlayers(String period) {
        LocalDate now = LocalDate.now();
        switch (period) {
            case "day" -> exec("UPDATE vp_stats SET day_count=0, day_key=?", List.of(now.toString()));
            case "week" -> exec("UPDATE vp_stats SET week_count=0, week_key=?", List.of(currentWeekKey(now)));
            case "month" -> exec("UPDATE vp_stats SET month_count=0, month_key=?", List.of(now.getYear() + "-" + now.getMonthValue()));
            case "total" -> {
                exec("UPDATE vp_profiles SET votes=0", List.of());
                exec("UPDATE vp_stats SET total_count=0", List.of());
            }
            default -> {
            }
        }
    }

    @Override
    public synchronized void resetPallier(String playerName, String pallier) {
        exec("DELETE FROM vp_player_palliers WHERE player_name=? AND name=?", List.of(key(playerName), key(pallier)));
    }

    @Override
    public synchronized void resetAllPalliers(String playerName) {
        exec("DELETE FROM vp_player_palliers WHERE player_name=?", List.of(key(playerName)));
    }


    @Override
    public synchronized void resetPalliersForAllPlayers(String pallierOrAll) {
        if ("all".equalsIgnoreCase(pallierOrAll)) {
            exec("TRUNCATE TABLE vp_player_palliers", List.of());
            return;
        }
        exec("DELETE FROM vp_player_palliers WHERE name=?", List.of(key(pallierOrAll)));
    }

    @Override
    public synchronized void upsertSharedConfig(VoteConfig config) {
        upsertState("cfg.goal", String.valueOf(config.votePartyGoal()));
        upsertState("cfg.voteRewards", String.join("\n", config.voteRewards()));
        upsertState("cfg.partyRewards", String.join("\n", config.partyRewards()));
        upsertState("cfg.partyGlobalRewards", String.join("\n", config.partyGlobalRewards()));
        upsertState("cfg.partyPlayerRewards", String.join("\n", config.partyPlayerRewards()));
    }

    @Override
    public synchronized SharedConfig loadSharedConfig() {
        Integer goal = readInt("cfg.goal");
        List<String> voteRewards = splitLines(readState("cfg.voteRewards"));
        List<String> partyRewards = splitLines(readState("cfg.partyRewards"));
        List<String> partyGlobalRewards = splitLines(readState("cfg.partyGlobalRewards"));
        List<String> partyPlayerRewards = splitLines(readState("cfg.partyPlayerRewards"));
        return new SharedConfig(goal, voteRewards, partyRewards, partyGlobalRewards, partyPlayerRewards);
    }

    private List<String> splitLines(String v) {
        if (v == null || v.isEmpty()) return List.of();
        return List.of(v.split("\\n"));
    }

    private Integer readInt(String key) {
        String value = readState(key);
        if (value == null) return null;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return null; }
    }

    private String readState(String key) {
        List<Object> rows = queryRows("SELECT v FROM vp_state WHERE k=?", List.of(key));
        return rows.isEmpty() ? null : String.valueOf(rowGet(rows.get(0), "v"));
    }

    private void upsertState(String key, String value) {
        exec("INSERT INTO vp_state(k,v) VALUES (?,?) ON DUPLICATE KEY UPDATE v=VALUES(v)", List.of(key, value == null ? "" : value));
    }

    @Override
    public void flush() {
        // managed by p-core
    }

    @Override
    public void close() {
        // managed by p-core
    }
}
