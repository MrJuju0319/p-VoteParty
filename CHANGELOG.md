# Changelog

## 1.5.4

- Extension de la commande reset pallier pour supporter les deux axes `joueur|all` et `pallier|all`:
  - `/vp reset pallier <joueur|all> <pallier|all>`
- Ajout du reset global multi-joueurs dans l'API storage:
  - `resetPalliersForAllPlayers(String pallierOrAll)`
- Implémentation backend:
  - `YamlVoteStorage` (purge par pallier global ou purge totale)
  - `MysqlVoteStorage` (DELETE by name / TRUNCATE `vp_player_palliers`)
  - `PcoreVoteStorage` (DELETE by name / TRUNCATE `vp_player_palliers`)
- Mise à jour README et usage des commandes dynamiques `/vp`.

## 1.5.3

- Refonte des palliers en **mode par joueur** (plus global):
  - `/vp setpallier <joueur> <pallier> <true|false>`
  - `/vp reset pallier <joueur> <pallier|all>`
- Placeholder `%p-voteparty_pallier_<X>%` désormais évalué pour le joueur du placeholder.
- Mise à jour storage API + implémentations:
  - `YamlVoteStorage` (structure `palliers.<player>.<pallier>`)
  - `MysqlVoteStorage` (table `vp_player_palliers`)
  - `PcoreVoteStorage` (table `vp_player_palliers`)
- Mise à jour README (commandes, placeholders, fonctionnement palliers par joueur).

## 1.5.2

- Correction compatibilité p-core query results:
  - `PcoreVoteStorage` accepte maintenant `Map` **ou** `Row` (via réflexion)
  - suppression du cast direct `Row -> Map` qui causait `ClassCastException` sur `/vp add vote ...`
- Mise à jour README avec note explicite de compatibilité `Row/Map`.

## 1.5.1

- Ajout de la commande `/vp reload` (admin) pour recharger `config.yml` à chaud.
- Rechargement runtime appliqué dans `VoteService.reloadFromConfig(...)` (goal/rewards/master/messages) sans redémarrage.
- Mise à jour de `VpDynamicCommand` / `VoteCommand` pour supporter `reload` et tab-complete associé.
- Mise à jour README (commande reload + limites de reload pour `storage.type`).

## 1.5.0

- Ajout de l'intégration **p-core** (managed mode) via `PcoreApi` détecté dynamiquement.
- Nouveau backend `PcoreVoteStorage` utilisant `DbService` de p-core pour centraliser l'accès data (sans pool local dans p-voteparty).
- Nouveau support `storage.type`:
  - `pcore`
  - `managed`
  - `auto` (try p-core puis fallback)
- Mise à jour `paper-plugin.yml`:
  - dépendance serveur optionnelle `p-core` (`required: false`, `join-classpath: true`)
  - version plugin `1.5.0`
- Mise à jour `config.yml` pour documenter `storage.type: yml | mysql | pcore | auto`.
- Mise à jour README avec section complète d'intégration p-core (mode géré, fallback, bonnes pratiques).

## 1.4.3

- Correction startup warning `language.yml already exists`:
  - vérification explicite de l'existence du fichier avant `saveResource("language.yml", false)`
- Mise à jour de `paper-plugin.yml` version `1.4.3`.
- Mise à jour README avec section dépannage runtime Paper:
  - warning `language.yml already exists`
  - warning `Loading Paper plugin in the legacy plugin loading logic`

## 1.4.2

- Correction critique Paper plugin command system:
  - suppression de la section `commands` dans `paper-plugin.yml`
  - arrêt de l'usage de `JavaPlugin#getCommand(...)` (non supporté pour Paper plugins)
  - enregistrement dynamique de `/vp` via `CommandMap` au démarrage
- Ajout de `VpDynamicCommand` pour relier exécution + tab-complete à `VoteCommand`.
- Mise à jour README:
  - clarification sur le système de commandes des Paper plugins
  - ajout de notes sur les cycles de dépendances (`dependencies.server`).

## 1.4.1

- Suppression de l'usage d'API Bukkit dépréciée dans `VoteService` (color/strip color), ce qui retire le warning de compilation "uses or overrides a deprecated API".
- Optimisation build Gradle:
  - activation `org.gradle.configuration-cache=true`
  - activation `org.gradle.parallel=true`
- Mise à jour README avec section dédiée à l'optimisation build et rappel de contournement (`--no-configuration-cache`).

## 1.4.0

- Mise à jour compatibilité **Minecraft 1.21.8+**:
  - `paper-api` -> `1.21.8-R0.1-SNAPSHOT`
  - `paper-plugin.yml` -> `api-version: 1.21.8`
- Durcissement des dépendances Gradle pour réduire les alertes sécurité sur dépendances transitives:
  - contrainte `commons-lang3:3.18.0`
  - contrainte `protobuf-java:3.25.8`
- Mise à jour du README avec section compatibilité 1.21.8+ et notes sécurité dépendances.

## 1.3.1

- Correction build Gradle Java 21:
  - ajout du resolver toolchain Foojay dans `settings.gradle`
  - ajout de `gradle.properties` avec auto-download de Java 21 toolchain
- Mise à jour du README:
  - ajout d'une section de dépannage build (`Undefined Toolchain Download Repositories`)
  - ajout d'un résumé complet des variables/placeholders

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
