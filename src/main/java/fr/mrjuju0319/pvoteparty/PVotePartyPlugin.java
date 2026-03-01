package fr.mrjuju0319.pvoteparty;

import fr.mrjuju0319.pvoteparty.command.VpDynamicCommand;
import fr.mrjuju0319.pvoteparty.placeholder.UtilitiesPlaceholderExpansion;
import fr.mrjuju0319.pvoteparty.scheduler.SchedulerAdapter;
import fr.mrjuju0319.pvoteparty.vote.MysqlVoteStorage;
import fr.mrjuju0319.pvoteparty.vote.PcoreVoteStorage;
import fr.mrjuju0319.pvoteparty.vote.VoteConfig;
import fr.mrjuju0319.pvoteparty.vote.VoteService;
import fr.mrjuju0319.pvoteparty.vote.VoteStorage;
import fr.mrjuju0319.pvoteparty.vote.YamlVoteStorage;
import java.io.File;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PVotePartyPlugin extends JavaPlugin implements Listener {

    private VoteService voteService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File languageFile = new File(getDataFolder(), "language.yml");
        if (!languageFile.exists()) {
            saveResource("language.yml", false);
        }

        VoteConfig voteConfig = VoteConfig.fromConfig(getConfig());
        VoteStorage storage = createStorage(voteConfig);
        this.voteService = new VoteService(this, voteConfig, storage);
        voteService.initializeRuntimeConfig();

        registerVpCommand();

        getServer().getPluginManager().registerEvents(this, this);

        long period = Math.max(20L, voteConfig.syncIntervalSeconds() * 20L);
        SchedulerAdapter.runRepeating(this, () -> voteService.tickSync(!voteConfig.master()), 20L, period);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new UtilitiesPlaceholderExpansion(this, voteService).register();
        }
    }

    private VoteStorage createStorage(VoteConfig config) {
        String type = config.storageType().toLowerCase();

        if ("pcore".equals(type) || "managed".equals(type) || "auto".equals(type)) {
            Object pcoreApi = tryLoadPcoreApi();
            if (pcoreApi != null) {
                getLogger().info("Vote storage: P-CORE MANAGED");
                return new PcoreVoteStorage(pcoreApi, "p-voteparty");
            }
            if (!"auto".equals(type)) {
                getLogger().warning("storage.type=" + type + " mais p-core indisponible, fallback MYSQL/YML.");
            }
        }

        if ("mysql".equals(type)) {
            getLogger().info("Vote storage: MYSQL");
            return new MysqlVoteStorage(config.mysql());
        }

        getLogger().info("Vote storage: YML");
        return new YamlVoteStorage(this);
    }

    private Object tryLoadPcoreApi() {
        try {
            Plugin pcore = Bukkit.getPluginManager().getPlugin("p-core");
            if (pcore == null || !pcore.isEnabled()) {
                return null;
            }
            Class<?> apiClass = Class.forName("dev.paracraft.pcore.api.PcoreApi");
            return Bukkit.getServicesManager().load(apiClass);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void registerVpCommand() {
        try {
            Method getCommandMap = getServer().getClass().getMethod("getCommandMap");
            Object commandMap = getCommandMap.invoke(getServer());
            Method register = commandMap.getClass().getMethod("register", String.class, Command.class);
            register.invoke(commandMap, getName(), new VpDynamicCommand(voteService));
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible d'enregistrer la commande /vp dynamiquement", exception);
        }
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
