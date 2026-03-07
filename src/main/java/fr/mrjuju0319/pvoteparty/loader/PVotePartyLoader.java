package fr.mrjuju0319.pvoteparty.loader;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

/**
 * Loader Paper natif (classpath dynamique possible ici).
 */
public final class PVotePartyLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        // Intentionnellement vide: dépendances gérées par Gradle.
    }
}
