package fr.mrjuju0319.pvoteparty.placeholder;

import fr.mrjuju0319.pvoteparty.PVotePartyPlugin;
import fr.mrjuju0319.pvoteparty.vote.VotePlayerStats;
import fr.mrjuju0319.pvoteparty.vote.VoteService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UtilitiesPlaceholderExpansion extends PlaceholderExpansion {

    private final PVotePartyPlugin plugin;
    private final VoteService voteService;

    public UtilitiesPlaceholderExpansion(PVotePartyPlugin plugin, VoteService voteService) {
        this.plugin = plugin;
        this.voteService = voteService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "p-voteparty";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MrJuJu0319";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("pallier_")) {
            if (player == null || player.getName() == null) {
                return "false";
            }
            String pallierName = params.substring("pallier_".length());
            return String.valueOf(voteService.getPallier(player.getName(), pallierName));
        }

        if (params.equalsIgnoreCase("vote_party")) {
            return voteService.getPartyProgress() + "/" + voteService.getPartyGoal();
        }

        if (player == null || player.getName() == null) {
            return "0";
        }

        if (params.equalsIgnoreCase("vote_vote") || params.equalsIgnoreCase("vote_total")) {
            return String.valueOf(voteService.getVotes(player.getName()));
        }

        VotePlayerStats stats = voteService.getStats(player.getName());

        if (params.equalsIgnoreCase("vote_day")) {
            return String.valueOf(stats.day());
        }
        if (params.equalsIgnoreCase("vote_week")) {
            return String.valueOf(stats.week());
        }
        if (params.equalsIgnoreCase("vote_month")) {
            return String.valueOf(stats.month());
        }
        if (params.equalsIgnoreCase("vote_year")) {
            return String.valueOf(stats.year());
        }

        return null;
    }
}
