package fr.mrjuju0319.pvoteparty.bootstrap;

import fr.mrjuju0319.pvoteparty.PVotePartyPlugin;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bootstrap Paper natif (pré-démarrage).
 */
public final class PVotePartyBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLogger().info("Bootstrapping p-voteparty...");
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new PVotePartyPlugin();
    }
}
