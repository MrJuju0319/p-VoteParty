package fr.mrjuju0319.pvoteparty.command;

import fr.mrjuju0319.pvoteparty.PVotePartyPlugin;
import fr.mrjuju0319.pvoteparty.vote.VoteConfig;
import fr.mrjuju0319.pvoteparty.vote.VoteService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class VoteCommand implements CommandExecutor, TabCompleter {

    private final PVotePartyPlugin plugin;
    private final VoteService voteService;

    public VoteCommand(PVotePartyPlugin plugin, VoteService voteService) {
        this.plugin = plugin;
        this.voteService = voteService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendHelp(sender);
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

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            VoteConfig updated = VoteConfig.fromConfig(plugin.getConfig());
            voteService.reloadFromConfig(updated);
            sender.sendMessage(voteService.color("&aConfiguration rechargee avec succes."));
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

        if (args[0].equalsIgnoreCase("setpallier") && args.length >= 4) {
            String targetName = args[1];
            String pallier = args[2];
            if (!args[3].equalsIgnoreCase("true") && !args[3].equalsIgnoreCase("false")) {
                sender.sendMessage(voteService.color("&cValeur invalide: utilise true ou false."));
                return true;
            }
            boolean value = Boolean.parseBoolean(args[3]);
            voteService.setPallier(targetName, pallier, value);
            sender.sendMessage(voteService.color("&aPallier &f" + pallier + " &apour &f" + targetName + " &aset a &f" + value));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && args.length >= 4 && args[1].equalsIgnoreCase("pallier")) {
            String targetName = args[2];
            String pallier = args[3];
            voteService.resetPallier(targetName, pallier);
            sender.sendMessage(voteService.color("&aPallier reset pour &f" + targetName + "&a: &f" + pallier));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && args.length >= 4 && args[1].equalsIgnoreCase("vote")) {
            String period = args[2];
            String targetName = args[3];
            if (!voteService.resetVotes(period, targetName)) {
                sender.sendMessage(voteService.color("&cType de reset invalide. Utilise: total/days/hebdo/mois."));
                return true;
            }
            sender.sendMessage(voteService.color("&aVotes reset (&f" + period + "&a) pour &f" + targetName));
            return true;
        }

        if (args[0].equalsIgnoreCase("party")) {
            voteService.triggerPartyRewards();
            sender.sendMessage(voteService.color("&aRewards de vote-party executes."));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
            completions.add("add");
            completions.add("party");
            completions.add("setpallier");
            completions.add("reset");
            completions.add("help");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            completions.add("vote");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("vote")) {
            return completePlayers(args[3], false);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            completions.add("pallier");
            completions.add("vote");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("pallier")) {
            return completePlayers(args[2], true);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("vote")) {
            completions.add("total");
            completions.add("days");
            completions.add("hebdo");
            completions.add("mois");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("pallier")) {
            completions.add("all");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("vote")) {
            return completePlayers(args[3], true);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setpallier")) {
            return completePlayers(args[1], false);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("setpallier")) {
            completions.add("true");
            completions.add("false");
        }
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(voteService.color("&6&m--------------------&r &e&lP-VoteParty &6&m--------------------"));
        sender.sendMessage(voteService.color("&e/vp"));
        sender.sendMessage(voteService.color("&7  Affiche tes stats perso + progression vote-party."));
        sender.sendMessage("");
        sender.sendMessage(voteService.color("&e/vp reload"));
        sender.sendMessage(voteService.color("&e/vp party"));
        sender.sendMessage(voteService.color("&e/vp add vote <nombre> <joueur>"));
        sender.sendMessage(voteService.color("&e/vp setpallier <joueur> <pallier> <true|false>"));
        sender.sendMessage(voteService.color("&e/vp reset pallier <joueur|all> <pallier|all>"));
        sender.sendMessage(voteService.color("&e/vp reset vote <total|days|hebdo|mois> <joueur|all>"));
        sender.sendMessage(voteService.color("&6&m-------------------------------------------------------"));
    }

    private List<String> completePlayers(String input, boolean includeAll) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (includeAll && "all".startsWith(needle)) {
            out.add("all");
        }
        for (String name : voteService.getKnownOnlinePlayers()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(name);
            }
        }
        out.sort(Comparator.comparing(String::toLowerCase));
        return out;
    }
}
