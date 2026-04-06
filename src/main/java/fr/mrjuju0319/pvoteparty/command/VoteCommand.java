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

    private static final String PREFIX = "&8[&6&lVoteParty&8] &7» ";

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

            int votes = voteService.getVotes(player.getName());
            int progress = voteService.getPartyProgress();
            int goal = voteService.getPartyGoal();
            int percent = Math.max(0, Math.min(100, (int) Math.round((progress * 100.0) / Math.max(1, goal))));

            sender.sendMessage(voteService.color("&8&m--------------------------------------------------"));
            sender.sendMessage(voteService.color("&6&lVoteParty &8• &7Statistiques de &f" + player.getName()));
            sender.sendMessage(voteService.color("&7- &fVotes personnels: &a" + votes));
            sender.sendMessage(voteService.color("&7- &fProgression globale: &e" + progress + "&7/&6" + goal + " &8(&f" + percent + "%&8)"));
            sender.sendMessage(voteService.color("&7- &fObjectif restant: &c" + Math.max(0, goal - progress)));
            sender.sendMessage(voteService.color("&8&m--------------------------------------------------"));
            return true;
        }

        if (!sender.hasPermission("p-voteparty.vote.admin")) {
            info(sender, "&cTu n'as pas la permission pour cette commande.");
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
            info(sender, "&aConfiguration rechargee avec succes.");
            return true;
        }

        if (args[0].equalsIgnoreCase("add") && args.length >= 4 && args[1].equalsIgnoreCase("vote")) {
            int amount;
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                info(sender, "&cNombre de votes invalide. Exemple: &f/vp add vote 3 Steve");
                return true;
            }
            String targetName = args[3];
            voteService.addVote(targetName, amount);
            info(sender, "&aAjout de &f" + amount + "&a vote(s) pour &f" + targetName + "&a.");
            return true;
        }

        if (args[0].equalsIgnoreCase("setpallier") && args.length >= 4) {
            String targetName = args[1];
            String pallier = args[2];
            if (!args[3].equalsIgnoreCase("true") && !args[3].equalsIgnoreCase("false")) {
                info(sender, "&cValeur invalide: utilise &ftrue &cou &ffalse&c.");
                return true;
            }
            boolean value = Boolean.parseBoolean(args[3]);
            voteService.setPallier(targetName, pallier, value);
            info(sender, "&aPallier &f" + pallier + " &amodifie pour &f" + targetName + "&a -> &f" + value + "&a.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && args.length >= 4 && args[1].equalsIgnoreCase("pallier")) {
            String targetName = args[2];
            String pallier = args[3];
            voteService.resetPallier(targetName, pallier);
            info(sender, "&aReset pallier applique pour &f" + targetName + "&a: &f" + pallier + "&a.");
            return true;
        }

        if (args[0].equalsIgnoreCase("reset") && args.length >= 4 && args[1].equalsIgnoreCase("vote")) {
            String period = args[2];
            String targetName = args[3];
            if (!voteService.resetVotes(period, targetName)) {
                info(sender, "&cType invalide. Utilise: &ftotal&7/&fdays&7/&fhebdo&7/&fmois");
                return true;
            }
            info(sender, "&aVotes reset (&f" + period + "&a) pour &f" + targetName + "&a.");
            return true;
        }

        if (args[0].equalsIgnoreCase("party")) {
            voteService.triggerPartyRewards();
            info(sender, "&aVote-party declenchee, rewards executes.");
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
        sender.sendMessage(voteService.color("&8&m------------------&r &6&lP-VoteParty &8&m------------------"));
        sender.sendMessage(voteService.color("&e/vp &8- &7Affiche les stats et la progression."));
        sender.sendMessage("");
        sender.sendMessage(voteService.color("&6&lAdministration"));
        sender.sendMessage(voteService.color("&e/vp reload &8- &7Recharge la configuration"));
        sender.sendMessage(voteService.color("&e/vp party &8- &7Declenche la vote-party"));
        sender.sendMessage(voteService.color("&e/vp add vote <nombre> <joueur> &8- &7Ajoute des votes"));
        sender.sendMessage(voteService.color("&e/vp setpallier <joueur> <pallier> <true|false>"));
        sender.sendMessage(voteService.color("&e/vp reset pallier <joueur|all> <pallier|all>"));
        sender.sendMessage(voteService.color("&e/vp reset vote <total|days|hebdo|mois> <joueur|all>"));
        sender.sendMessage(voteService.color("&8&m-------------------------------------------------------"));
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(voteService.color(PREFIX + message));
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
