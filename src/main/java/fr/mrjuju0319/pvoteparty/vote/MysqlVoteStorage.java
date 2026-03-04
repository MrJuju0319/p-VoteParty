package fr.mrjuju0319.pvoteparty.vote;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MysqlVoteStorage implements VoteStorage {

    private final Connection connection;

    public MysqlVoteStorage(VoteConfig.MysqlSettings settings) {
        try {
            this.connection = DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
            bootstrap();
        } catch (SQLException e) {
            throw new IllegalStateException("Impossible de se connecter a MySQL", e);
        }
    }

    private void bootstrap() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_profiles (player_name VARCHAR(32) PRIMARY KEY, votes INT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_state (k VARCHAR(64) PRIMARY KEY, v TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_pending (id BIGINT AUTO_INCREMENT PRIMARY KEY, player_name VARCHAR(32) NOT NULL, command TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_online (player_name VARCHAR(32) PRIMARY KEY, server_name VARCHAR(64) NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_player_palliers (player_name VARCHAR(32) NOT NULL, name VARCHAR(64) NOT NULL, enabled BOOLEAN NOT NULL, PRIMARY KEY(player_name, name))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS vp_stats (" +
                    "player_name VARCHAR(32) PRIMARY KEY," +
                    "day_count INT NOT NULL, week_count INT NOT NULL, month_count INT NOT NULL, year_count INT NOT NULL, total_count INT NOT NULL," +
                    "day_key VARCHAR(16) NOT NULL, week_key VARCHAR(16) NOT NULL, month_key VARCHAR(16) NOT NULL, year_key VARCHAR(8) NOT NULL)");
        }
    }

    private String key(String n) {
        return n.toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized int incrementVotes(String playerName, int amount) {
        String player = key(playerName);
        try {
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO vp_profiles(player_name, votes) VALUES (?, ?) ON DUPLICATE KEY UPDATE votes = votes + VALUES(votes)")) {
                upsert.setString(1, player);
                upsert.setInt(2, amount);
                upsert.executeUpdate();
            }
            incrementStats(player, amount);
            return getVotes(player);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void incrementStats(String player, int amount) throws SQLException {
        LocalDate now = LocalDate.now();
        String dayKey = now.toString();
        String weekKey = now.getYear() + "-W" + now.get(WeekFields.ISO.weekOfWeekBasedYear());
        String monthKey = now.getYear() + "-" + now.getMonthValue();
        String yearKey = String.valueOf(now.getYear());

        int oldDay = 0;
        int oldWeek = 0;
        int oldMonth = 0;
        int oldYear = 0;
        int oldTotal = 0;
        String oldDayKey = dayKey;
        String oldWeekKey = weekKey;
        String oldMonthKey = monthKey;
        String oldYearKey = yearKey;

        try (PreparedStatement read = connection.prepareStatement(
                "SELECT day_count,week_count,month_count,year_count,total_count,day_key,week_key,month_key,year_key FROM vp_stats WHERE player_name=?")) {
            read.setString(1, player);
            ResultSet rs = read.executeQuery();
            if (rs.next()) {
                oldDay = rs.getInt(1);
                oldWeek = rs.getInt(2);
                oldMonth = rs.getInt(3);
                oldYear = rs.getInt(4);
                oldTotal = rs.getInt(5);
                oldDayKey = rs.getString(6);
                oldWeekKey = rs.getString(7);
                oldMonthKey = rs.getString(8);
                oldYearKey = rs.getString(9);
            }
        }

        int day = dayKey.equals(oldDayKey) ? oldDay : 0;
        int week = weekKey.equals(oldWeekKey) ? oldWeek : 0;
        int month = monthKey.equals(oldMonthKey) ? oldMonth : 0;
        int year = yearKey.equals(oldYearKey) ? oldYear : 0;

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO vp_stats(player_name,day_count,week_count,month_count,year_count,total_count,day_key,week_key,month_key,year_key) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE day_count=?,week_count=?,month_count=?,year_count=?,total_count=?,day_key=?,week_key=?,month_key=?,year_key=?")) {
            int total = oldTotal + amount;
            ps.setString(1, player);
            ps.setInt(2, day + amount);
            ps.setInt(3, week + amount);
            ps.setInt(4, month + amount);
            ps.setInt(5, year + amount);
            ps.setInt(6, total);
            ps.setString(7, dayKey);
            ps.setString(8, weekKey);
            ps.setString(9, monthKey);
            ps.setString(10, yearKey);

            ps.setInt(11, day + amount);
            ps.setInt(12, week + amount);
            ps.setInt(13, month + amount);
            ps.setInt(14, year + amount);
            ps.setInt(15, total);
            ps.setString(16, dayKey);
            ps.setString(17, weekKey);
            ps.setString(18, monthKey);
            ps.setString(19, yearKey);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized int incrementPartyProgress(int amount) {
        int current = getPartyProgress() + amount;
        setPartyProgress(current);
        return current;
    }

    @Override
    public synchronized int getPartyProgress() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM vp_state WHERE k='party-progress'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Integer.parseInt(rs.getString(1));
            }
            return 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void setPartyProgress(int value) {
        upsertState("party-progress", String.valueOf(Math.max(0, value)));
    }

    @Override
    public synchronized int getVotes(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT votes FROM vp_profiles WHERE player_name=?")) {
            ps.setString(1, key(playerName));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public synchronized VotePlayerStats getStats(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT day_count,week_count,month_count,year_count,total_count FROM vp_stats WHERE player_name=?")) {
            ps.setString(1, key(playerName));
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return VotePlayerStats.empty();
            }
            return new VotePlayerStats(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void enqueuePendingReward(String playerName, String command) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO vp_pending(player_name, command) VALUES (?,?)")) {
            ps.setString(1, key(playerName));
            ps.setString(2, command);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<String> popPendingRewards(String playerName) {
        String p = key(playerName);
        List<String> out = new ArrayList<>();
        try (PreparedStatement sel = connection.prepareStatement("SELECT id, command FROM vp_pending WHERE player_name=? ORDER BY id ASC")) {
            sel.setString(1, p);
            ResultSet rs = sel.executeQuery();
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
                out.add(rs.getString("command"));
            }
            try (PreparedStatement del = connection.prepareStatement("DELETE FROM vp_pending WHERE id=?")) {
                for (Long id : ids) {
                    del.setLong(1, id);
                    del.executeUpdate();
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void setOnlineServer(String playerName, String serverName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO vp_online(player_name, server_name) VALUES (?, ?) ON DUPLICATE KEY UPDATE server_name=VALUES(server_name)")) {
            ps.setString(1, key(playerName));
            ps.setString(2, serverName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void clearOnlineServer(String playerName, String serverName) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM vp_online WHERE player_name=? AND server_name=?")) {
            ps.setString(1, key(playerName));
            ps.setString(2, serverName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized String getOnlineServer(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT server_name FROM vp_online WHERE player_name=?")) {
            ps.setString(1, key(playerName));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized Map<String, Integer> topVotes(int limit) {
        Map<String, Integer> map = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT player_name, votes FROM vp_profiles ORDER BY votes DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
            return map;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void setPallier(String playerName, String pallier, boolean value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO vp_player_palliers(player_name, name, enabled) VALUES (?,?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)")) {
            ps.setString(1, key(playerName));
            ps.setString(2, key(pallier));
            ps.setBoolean(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized boolean getPallier(String playerName, String pallier) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT enabled FROM vp_player_palliers WHERE player_name=? AND name=?")) {
            ps.setString(1, key(playerName));
            ps.setString(2, key(pallier));
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getBoolean(1);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void resetPallier(String playerName, String pallier) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM vp_player_palliers WHERE player_name=? AND name=?")) {
            ps.setString(1, key(playerName));
            ps.setString(2, key(pallier));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void resetAllPalliers(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM vp_player_palliers WHERE player_name=?")) {
            ps.setString(1, key(playerName));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public synchronized void resetPalliersForAllPlayers(String pallierOrAll) {
        try {
            if ("all".equalsIgnoreCase(pallierOrAll)) {
                try (PreparedStatement ps = connection.prepareStatement("TRUNCATE TABLE vp_player_palliers")) {
                    ps.executeUpdate();
                }
                return;
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM vp_player_palliers WHERE name=?")) {
                ps.setString(1, key(pallierOrAll));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
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
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readState(String key) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT v FROM vp_state WHERE k=?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void upsertState(String key, String value) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO vp_state(k,v) VALUES (?,?) ON DUPLICATE KEY UPDATE v=VALUES(v)")) {
            ps.setString(1, key);
            ps.setString(2, value == null ? "" : value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void flush() {
        // no-op for SQL backend
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

}
