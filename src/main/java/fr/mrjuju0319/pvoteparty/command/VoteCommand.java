package fr.mrjuju0319.pvoteparty.command;

import fr.mrjuju0319.pvoteparty.vote.VoteService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class VoteCommand implements CommandExecutor, TabCompleter {

    private final VoteService voteService;

    public VoteCommand(VoteService voteService) {
        this.voteService = voteService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Utilise /vp add vote <nombre> <joueur> en console.");
                return true;
            }
            sender.sendMessage(voteService.color("&aVotes personnels: &f" + voteService.getVotes(player.getName())));
            sender.sendMessage(voteService.color("&aProgression vote-party: &f" + voteService.getPartyProgress() + "&7/&f" + voteService.getPartyGoal()));
            return true;
        }

        if (!sender.hasPermission("p-voteparty.vote.admin")) {
            sender.sendMessage(voteService.color("&cTu n'as pas la permission."));
            return true;
        }

        if (args[0].equalsIgnoreCase("add") && args.length >= 4 && args[1].equalsIgnoreCase("vote")) {
            int amount;
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(voteService.color("&cNombre de vote invalide."));
                return true;
            }
            String targetName = args[3];
            voteService.addVote(targetName, amount);
            sender.sendMessage(voteService.color("&a" + amount + " vote(s) ajoute(s) pour &f" + targetName));
            return true;
        }

        if (args[0].equalsIgnoreCase("setpallier") && args.length >= 3) {
            String pallier = args[1];
            if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
                sender.sendMessage(voteService.color("&cValeur invalide: utilise true ou false."));
                return true;
            }
            boolean value = Boolean.parseBoolean(args[2]);
            voteService.setPallier(pallier, value);
            sender.sendMessage(voteService.color("&aPallier &f" + pallier + " &aset a &f" + value));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && args.length >= 3 && args[1].equalsIgnoreCase("pallier")) {
            voteService.resetPallier(args[2]);
            sender.sendMessage(voteService.color("&aPallier reset: &f" + args[2]));
            return true;
        }

        if (args[0].equalsIgnoreCase("party")) {
            voteService.triggerPartyRewards();
            sender.sendMessage(voteService.color("&aRewards de vote-party executes."));
            return true;
        }

        sender.sendMessage(voteService.color("&eUsage: /vp [add vote <nombre> <joueur>|setpallier <pallier> <true/false>|reset pallier <pallier/all>|party]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("add");
            completions.add("party");
            completions.add("setpallier");
            completions.add("reset");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            completions.add("vote");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            completions.add("pallier");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("pallier")) {
            completions.add("all");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setpallier")) {
            completions.add("true");
            completions.add("false");
        }
        return completions;
    }
}
