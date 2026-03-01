package fr.mrjuju0319.pvoteparty;

import fr.mrjuju0319.pvoteparty.command.VoteCommand;
import fr.mrjuju0319.pvoteparty.placeholder.UtilitiesPlaceholderExpansion;
import fr.mrjuju0319.pvoteparty.scheduler.SchedulerAdapter;
import fr.mrjuju0319.pvoteparty.vote.MysqlVoteStorage;
import fr.mrjuju0319.pvoteparty.vote.VoteConfig;
import fr.mrjuju0319.pvoteparty.vote.VoteService;
import fr.mrjuju0319.pvoteparty.vote.VoteStorage;
import fr.mrjuju0319.pvoteparty.vote.YamlVoteStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PVotePartyPlugin extends JavaPlugin implements Listener {

    private VoteService voteService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("language.yml", false);

        VoteConfig voteConfig = VoteConfig.fromConfig(getConfig());
        VoteStorage storage = createStorage(voteConfig);
        this.voteService = new VoteService(this, voteConfig, storage);
        voteService.initializeRuntimeConfig();

        PluginCommand vpCommand = getCommand("vp");
        if (vpCommand != null) {
            VoteCommand executor = new VoteCommand(voteService);
            vpCommand.setExecutor(executor);
            vpCommand.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(this, this);

        long period = Math.max(20L, voteConfig.syncIntervalSeconds() * 20L);
        SchedulerAdapter.runRepeating(this, () -> voteService.tickSync(!voteConfig.master()), 20L, period);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new UtilitiesPlaceholderExpansion(this, voteService).register();
        }
    }

    private VoteStorage createStorage(VoteConfig config) {
        if ("mysql".equalsIgnoreCase(config.storageType())) {
            getLogger().info("Vote storage: MYSQL");
            return new MysqlVoteStorage(config.mysql());
        }
        getLogger().info("Vote storage: YML");
        return new YamlVoteStorage(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        voteService.setOnlinePlayer(event.getPlayer().getName());
        voteService.flushPendingForPlayer(event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        voteService.clearOnlinePlayer(event.getPlayer().getName());
    }

    @Override
    public void onDisable() {
        if (voteService != null) {
            voteService.shutdown();
        }
    }
}
