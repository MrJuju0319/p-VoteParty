# Changelog

## 1.3.0

- Ajout d'un `paper-plugin.yml` aligné Paper plugins modernes:
  - `bootstrapper`
  - `loader`
  - `dependencies.server`
- Ajout de `PVotePartyBootstrap` et `PVotePartyLoader` (API native Paper plugin lifecycle).
- Optimisation MySQL stats (moins de requêtes SQL au moment d'incrémenter les votes).
- Validation stricte de `/vp setpallier <pallier> <true|false>`.
- Mise à jour complète du README avec:
  - toutes les commandes,
  - fonctionnement détaillé,
  - liste exhaustive des placeholders PlaceholderAPI,
  - section performance/APIs natives Paper/Folia.

## 1.2.0

- Optimisation globale du plugin (réduction I/O en backend YML avec cache mémoire + flush).
- Compatibilité **Folia** ajoutée via scheduler global avec fallback Paper/Bukkit.
- Ajout des commandes pallier:
  - `/vp setpallier <pallier> <true|false>`
  - `/vp reset pallier <pallier|all>`
- Ajout du placeholder pallier:
  - `%p-voteparty_pallier_X%`
- Ajout des stats joueur via placeholders:
  - `%p-voteparty_vote_day%`
  - `%p-voteparty_vote_week%`
  - `%p-voteparty_vote_month%`
  - `%p-voteparty_vote_year%`
  - `%p-voteparty_vote_total%`
- Ajout du suivi stats journalier/hebdo/mensuel/annuel/total en storage YML et MySQL.
- Mise à jour README + metadata commande.

## 1.1.0

- Refonte du storage pour supporter YML ou MySQL.
- Pending rewards cross-serveur et tracking online.

## 1.0.0

- Première version du module vote-party.
