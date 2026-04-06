package fr.mrjuju0319.pvoteparty.scheduler;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class SchedulerAdapter {

    private SchedulerAdapter() {
    }

    public static void runRepeating(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Method globalSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object globalScheduler = globalSchedulerMethod.invoke(null);
            Method runAtFixedRate = globalScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class
            );
            runAtFixedRate.invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) task -> runnable.run(), delayTicks, periodTicks);
            return;
        } catch (Exception ignored) {
            // fallback Paper/Spigot
        }

        Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public static void runLater(Plugin plugin, Runnable runnable, long delayTicks) {
        try {
            Method globalSchedulerMethod = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object globalScheduler = globalSchedulerMethod.invoke(null);
            Method runDelayed = globalScheduler.getClass().getMethod(
                    "runDelayed",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class
            );
            runDelayed.invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) task -> runnable.run(), delayTicks);
            return;
        } catch (Exception ignored) {
            // fallback Paper/Spigot
        }

        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }
}
