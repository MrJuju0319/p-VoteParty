package fr.mrjuju0319.pvoteparty.command;

import fr.mrjuju0319.pvoteparty.PVotePartyPlugin;
import fr.mrjuju0319.pvoteparty.vote.VoteService;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class VpDynamicCommand extends Command {

    private final VoteCommand delegate;

    public VpDynamicCommand(PVotePartyPlugin plugin, VoteService voteService) {
        super("vp", "Gestion des votes et de la vote-party", "/vp [reload|add vote <nombre> <joueur>|setpallier <joueur> <pallier> <true/false>|reset pallier <joueur|all> <pallier|all>|reset vote <total|days|hebdo|mois> <joueur|all>|party]", Collections.singletonList("voteparty"));
        this.delegate = new VoteCommand(plugin, voteService);
        setPermission("p-voteparty.vote.admin");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return delegate.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        List<String> out = delegate.onTabComplete(sender, this, alias, args);
        return out == null ? Collections.emptyList() : out;
    }
}
