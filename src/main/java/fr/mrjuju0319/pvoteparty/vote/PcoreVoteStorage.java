package fr.mrjuju0319.pvoteparty.vote;

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
 * This implementation uses reflection to avoid a hard compile dependency on p-core API package names.
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
    private List<Map<String, Object>> query(String sql, List<Object> params) {
        try {
            Object dbService = pcoreApi.getClass().getMethod("db").invoke(pcoreApi);
            Object cf = dbService.getClass().getMethod("query", String.class, String.class, List.class)
                    .invoke(dbService, pluginId, sql, params);
            return ((CompletableFuture<List<Map<String, Object>>>) cf).join();
        } catch (Exception e) {
            throw new IllegalStateException("p-core DbService query failed", e);
        }
    }

    private void bootstrap() {
        exec("CREATE TABLE IF NOT EXISTS vp_profiles (player_name VARCHAR(32) PRIMARY KEY, votes INT NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_state (k VARCHAR(64) PRIMARY KEY, v TEXT NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_pending (id BIGINT AUTO_INCREMENT PRIMARY KEY, player_name VARCHAR(32) NOT NULL, command TEXT NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_online (player_name VARCHAR(32) PRIMARY KEY, server_name VARCHAR(64) NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_palliers (name VARCHAR(64) PRIMARY KEY, enabled BOOLEAN NOT NULL)", List.of());
        exec("CREATE TABLE IF NOT EXISTS vp_stats (player_name VARCHAR(32) PRIMARY KEY, day_count INT NOT NULL, week_count INT NOT NULL, month_count INT NOT NULL, year_count INT NOT NULL, total_count INT NOT NULL, day_key VARCHAR(16) NOT NULL, week_key VARCHAR(16) NOT NULL, month_key VARCHAR(16) NOT NULL, year_key VARCHAR(8) NOT NULL)", List.of());
    }

    private String key(String n) {
        return n.toLowerCase(Locale.ROOT);
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
        String weekKey = now.getYear() + "-W" + now.get(WeekFields.ISO.weekOfWeekBasedYear());
        String monthKey = now.getYear() + "-" + now.getMonthValue();
        String yearKey = String.valueOf(now.getYear());

        List<Map<String, Object>> rows = query("SELECT day_count,week_count,month_count,year_count,total_count,day_key,week_key,month_key,year_key FROM vp_stats WHERE player_name=?", List.of(player));

        int oldDay = 0, oldWeek = 0, oldMonth = 0, oldYear = 0, oldTotal = 0;
        String oldDayKey = dayKey, oldWeekKey = weekKey, oldMonthKey = monthKey, oldYearKey = yearKey;
        if (!rows.isEmpty()) {
            Map<String, Object> r = rows.get(0);
            oldDay = toInt(r.get("day_count"));
            oldWeek = toInt(r.get("week_count"));
            oldMonth = toInt(r.get("month_count"));
            oldYear = toInt(r.get("year_count"));
            oldTotal = toInt(r.get("total_count"));
            oldDayKey = String.valueOf(r.get("day_key"));
            oldWeekKey = String.valueOf(r.get("week_key"));
            oldMonthKey = String.valueOf(r.get("month_key"));
            oldYearKey = String.valueOf(r.get("year_key"));
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
        List<Map<String, Object>> rows = query("SELECT v FROM vp_state WHERE k='party-progress'", List.of());
        return rows.isEmpty() ? 0 : Integer.parseInt(String.valueOf(rows.get(0).get("v")));
    }

    @Override
    public synchronized void setPartyProgress(int value) {
        upsertState("party-progress", String.valueOf(Math.max(0, value)));
    }

    @Override
    public synchronized int getVotes(String playerName) {
        List<Map<String, Object>> rows = query("SELECT votes FROM vp_profiles WHERE player_name=?", List.of(key(playerName)));
        return rows.isEmpty() ? 0 : toInt(rows.get(0).get("votes"));
    }

    @Override
    public synchronized VotePlayerStats getStats(String playerName) {
        List<Map<String, Object>> rows = query("SELECT day_count,week_count,month_count,year_count,total_count FROM vp_stats WHERE player_name=?", List.of(key(playerName)));
        if (rows.isEmpty()) return VotePlayerStats.empty();
        Map<String, Object> r = rows.get(0);
        return new VotePlayerStats(toInt(r.get("day_count")), toInt(r.get("week_count")), toInt(r.get("month_count")), toInt(r.get("year_count")), toInt(r.get("total_count")));
    }

    @Override
    public synchronized void enqueuePendingReward(String playerName, String command) {
        exec("INSERT INTO vp_pending(player_name, command) VALUES (?,?)", List.of(key(playerName), command));
    }

    @Override
    public synchronized List<String> popPendingRewards(String playerName) {
        String p = key(playerName);
        List<Map<String, Object>> rows = query("SELECT id, command FROM vp_pending WHERE player_name=? ORDER BY id ASC", List.of(p));
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long id = Long.parseLong(String.valueOf(row.get("id")));
            out.add(String.valueOf(row.get("command")));
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
        List<Map<String, Object>> rows = query("SELECT server_name FROM vp_online WHERE player_name=?", List.of(key(playerName)));
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("server_name"));
    }

    @Override
    public synchronized Map<String, Integer> topVotes(int limit) {
        List<Map<String, Object>> rows = query("SELECT player_name, votes FROM vp_profiles ORDER BY votes DESC LIMIT ?", List.of(limit));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            out.put(String.valueOf(row.get("player_name")), toInt(row.get("votes")));
        }
        return out;
    }

    @Override
    public synchronized void setPallier(String pallier, boolean value) {
        exec("INSERT INTO vp_palliers(name, enabled) VALUES (?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)", List.of(key(pallier), value));
    }

    @Override
    public synchronized boolean getPallier(String pallier) {
        List<Map<String, Object>> rows = query("SELECT enabled FROM vp_palliers WHERE name=?", List.of(key(pallier)));
        return !rows.isEmpty() && Boolean.parseBoolean(String.valueOf(rows.get(0).get("enabled")));
    }

    @Override
    public synchronized void resetPallier(String pallier) {
        exec("DELETE FROM vp_palliers WHERE name=?", List.of(key(pallier)));
    }

    @Override
    public synchronized void resetAllPalliers() {
        exec("TRUNCATE TABLE vp_palliers", List.of());
    }

    @Override
    public synchronized void upsertSharedConfig(VoteConfig config) {
        upsertState("cfg.goal", String.valueOf(config.votePartyGoal()));
        upsertState("cfg.voteRewards", String.join("\n", config.voteRewards()));
        upsertState("cfg.partyRewards", String.join("\n", config.partyRewards()));
    }

    @Override
    public synchronized SharedConfig loadSharedConfig() {
        Integer goal = readInt("cfg.goal");
        List<String> voteRewards = splitLines(readState("cfg.voteRewards"));
        List<String> partyRewards = splitLines(readState("cfg.partyRewards"));
        return new SharedConfig(goal, voteRewards, partyRewards);
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
        List<Map<String, Object>> rows = query("SELECT v FROM vp_state WHERE k=?", List.of(key));
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("v"));
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
