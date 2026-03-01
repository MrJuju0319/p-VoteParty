# p-voteparty

Plugin **Paper/Folia** de vote-party cross-serveur, développé par **MrJuJu0319**.

**Version Minecraft cible : 1.21.8 et +**.

> Le plugin est déclaré avec **paper-plugin.yml** (Paper plugin) et utilise les API natives Paper/Folia (scheduler global Folia + fallback Paper/Bukkit).

---

## 1) Fonctionnement global

Le plugin gère :

- votes par joueur,
- progression de vote-party,
- rewards de vote,
- rewards de vote-party,
- distribution différée (pending) si le joueur est hors-ligne,
- stockage partagé **YML** ou **MySQL**,
- synchronisation de configuration depuis un serveur **master**.

### Cross-serveur (Paper + réseau Velocity/Bungee)

Le plugin fonctionne en réseau via un **storage partagé** (recommandé: MySQL) :

- `/vp add vote X joueur` peut être exécuté depuis n'importe quel serveur,
- si le joueur est en ligne localement, la récompense est donnée tout de suite,
- sinon la récompense est mise en pending,
- dès que le joueur se connecte sur un serveur du réseau, les pending rewards sont exécutées.

---

## 2) Installation

1. Compiler le plugin (`build/libs`).
2. Déposer le JAR sur chaque serveur Paper/Folia.
3. Configurer un storage partagé (fortement conseillé: MySQL).
4. Redémarrer les serveurs.

---

## 3) Configuration (`config.yml`)

```yml
server-name: lobby-1
master: false

sync:
  interval-seconds: 5

storage:
  type: yml # yml ou mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: pvoteparty
    username: root
    password: ""
    ssl: false

vote:
  goal: 10
  rewards:
    - "eco give {player} 100"
  party-rewards:
    - "say &aVote party terminee sur {player}!"
    - "crate key giveall vote 1"

messages:
  no-online-players: "&cAucun joueur connecte sur ce serveur pour executer une commande avec {player}."
```

### Options importantes

- `master: true` : ce serveur publie `goal`, `rewards`, `party-rewards` dans le storage partagé.
- `master: false` : ce serveur consomme la config publiée par le master.
- `sync.interval-seconds` : fréquence de heartbeat/synchronisation.

---

## 4) Toutes les commandes

### Commande joueur

- `/vp`
  - Affiche les votes du joueur et la progression de vote-party.

### Commandes admin (`p-voteparty.vote.admin`)

- `/vp add vote <nombre> <joueur>`
  - Ajoute `<nombre>` votes au joueur.
  - Applique les rewards de vote.
  - Si le joueur est hors-ligne, rewards stockées en pending.

- `/vp party`
  - Déclenche immédiatement les rewards de vote-party.

- `/vp setpallier <pallier> <true|false>`
  - Active/désactive un pallier booléen.

- `/vp reset pallier <pallier|all>`
  - Reset un pallier précis, ou tous les palliers avec `all`.

---

## 5) Liste complète PlaceholderAPI

Identifiant: `p-voteparty`

### Vote / party

- `%p-voteparty_vote_party%` → `progression/objectif`
- `%p-voteparty_vote_vote%` → total des votes du joueur
- `%p-voteparty_vote_total%` → total des votes du joueur (alias)

### Stats temporelles par joueur

- `%p-voteparty_vote_day%`
- `%p-voteparty_vote_week%`
- `%p-voteparty_vote_month%`
- `%p-voteparty_vote_year%`

### Palliers

- `%p-voteparty_pallier_<X>%`
  - Ex: `%p-voteparty_pallier_10%`
  - Retourne `true` ou `false`.

---

## 6) Performance / APIs natives utilisées

- Scheduler natif **Folia GlobalRegionScheduler** détecté automatiquement.
- Fallback natif Paper/Bukkit si Folia indisponible.
- Backend YML optimisé : cache mémoire + flush périodique (moins d'I/O disque).
- Backend MySQL avec tables dédiées (profiles/state/pending/online/palliers/stats).
- Dépendances Paper plugin déclarées dans `paper-plugin.yml` (`dependencies.server`).

---

## 7) Permission

- `p-voteparty.vote.admin`

---

## 8) Notes Paper plugin

`paper-plugin.yml` inclut :

- `bootstrapper`
- `loader`
- `dependencies.server`

pour coller au modèle Paper Plugin moderne.


---


## 9) Build / Toolchain Java 21 (important)

Si vous avez l'erreur Gradle:

- `Cannot find a Java installation ... matching languageVersion=21`
- `Undefined Toolchain Download Repositories`

Le projet est maintenant configuré pour télécharger automatiquement un JDK 21 via Gradle Toolchains (Foojay resolver).

Fichiers concernés:

- `settings.gradle` (plugin `org.gradle.toolchains.foojay-resolver-convention`)
- `gradle.properties` (`org.gradle.java.installations.auto-download=true`)

Commandes recommandées:

```bash
gradle --stop
gradle clean build
```

Sur un environnement sans accès internet, installez manuellement Java 21 puis relancez le build.

---

## 10) Résumé rapide des variables

- Variable commandes config: `{player}`
- Placeholder pallier: `%p-voteparty_pallier_<X>%`
- Placeholders vote: 
  - `%p-voteparty_vote_party%`
  - `%p-voteparty_vote_vote%`
  - `%p-voteparty_vote_total%`
  - `%p-voteparty_vote_day%`
  - `%p-voteparty_vote_week%`
  - `%p-voteparty_vote_month%`
  - `%p-voteparty_vote_year%`


---

## 11) Compatibilité 1.21.8+ et sécurité dépendances

- API Paper utilisée : `1.21.8-R0.1-SNAPSHOT`.
- `paper-plugin.yml` utilise `api-version: 1.21.8`.
- Le build Gradle applique des contraintes de versions pour limiter des dépendances transitives signalées vulnérables par certains scanners IDE:
  - `org.apache.commons:commons-lang3:3.18.0`
  - `com.google.protobuf:protobuf-java:3.25.8`

Si votre scanner affiche encore un warning, faites un `gradle --refresh-dependencies` puis rescan du projet.
