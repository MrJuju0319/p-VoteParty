package fr.mrjuju0319.pvoteparty.vote;

import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record VoteConfig(
        String serverName,
        int votePartyGoal,
        List<String> voteRewards,
        List<String> partyRewards,
        String noOnlinePlayersMessage,
        String storageType,
        MysqlSettings mysql,
        boolean master,
        int syncIntervalSeconds
) {

    public static VoteConfig fromConfig(FileConfiguration config) {
        ConfigurationSection mysqlSection = config.getConfigurationSection("storage.mysql");
        MysqlSettings mysqlSettings = new MysqlSettings(
                mysqlSection != null ? mysqlSection.getString("host", "127.0.0.1") : "127.0.0.1",
                mysqlSection != null ? mysqlSection.getInt("port", 3306) : 3306,
                mysqlSection != null ? mysqlSection.getString("database", "pvoteparty") : "pvoteparty",
                mysqlSection != null ? mysqlSection.getString("username", "root") : "root",
                mysqlSection != null ? mysqlSection.getString("password", "") : "",
                mysqlSection != null ? mysqlSection.getBoolean("ssl", false) : false
        );

        return new VoteConfig(
                config.getString("server-name", "default"),
                Math.max(1, config.getInt("vote.goal", 100)),
                Collections.unmodifiableList(config.getStringList("vote.rewards")),
                Collections.unmodifiableList(config.getStringList("vote.party-rewards")),
                config.getString("messages.no-online-players", "&cAucun joueur en ligne."),
                config.getString("storage.type", "yml").toLowerCase(),
                mysqlSettings,
                config.getBoolean("master", false),
                Math.max(1, config.getInt("sync.interval-seconds", 5))
        );
    }

    public record MysqlSettings(String host, int port, String database, String username, String password, boolean ssl) {
        public String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
    }
}
