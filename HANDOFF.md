# HANDOFF — Projet Sync (traceur GPS Android)

Dernière mise à jour : 2026-08-27

## Objectif
App Android discrète : enregistre la position GPS toutes les 5 min, envoie le trajet
par mail (cashredac@gmail.com) toutes les 6 h au format GPX (lisible sur Google Maps /
My Maps). Pas d'icône après config, pas de notification, tourne en arrière-plan. Liée à
GitHub avec compilation + release automatiques. Objectif d'usage : marche/vélo, voir le
trajet du jour et augmenter les distances.

## État actuel : v1 livrée (à compiler via GitHub Actions)
Le code source complet + la CI sont prêts. La compilation de l'APK se fait sur GitHub
(pas testable dans l'environnement Cowork : pas de SDK Android ni d'accès à dl.google.com).
**Prochaine action côté utilisateur : pousser le code sur un dépôt GitHub privé et laisser
l'action « Build APK » produire `sync.apk`, puis installer/configurer sur le téléphone.**

## Décisions techniques (validées avec l'utilisateur le 2026-08-27)
- **Envoi mail : SMTP direct depuis le téléphone** (choix utilisateur), via Gmail +
  mot de passe d'application (JavaMail / `com.sun.mail:android-mail`). Le mot de passe est
  stocké chiffré sur l'appareil (EncryptedSharedPreferences).
- **Config initiale : un lancement unique accepté.** Écran de setup (permissions + identifiants),
  puis l'icône se masque (désactivation de l'`activity-alias` LauncherAlias).
- **Notification : aucune (choix utilisateur).** Conséquence technique : impossible d'utiliser
  un foreground service (qui imposerait une notification). On utilise donc des **alarmes exactes
  `AlarmManager.setExactAndAllowWhileIdle`** qui réveillent un BroadcastReceiver toutes les 5 min,
  prennent un point GPS, puis reprogramment l'alarme suivante.

## Architecture du code (module app, package com.phil.gpslog)
- `SetupActivity.kt` — écran de config unique (permissions A→D, identifiants, démarrage E,
  test mail, masquage icône F).
- `LocationAlarmReceiver.kt` — cœur : réarme l'alarme, prend un point GPS (LocationManager,
  timeout + last-known en secours), et tous les 6 h construit le GPX et l'envoie.
- `Scheduler.kt` — planification des alarmes (5 min) + constante d'envoi (6 h).
- `PointStore.kt` — stockage local CSV append-only des points.
- `GpxBuilder.kt` — génère le GPX 1.1 + calcul distance (haversine).
- `EmailSender.kt` — envoi SMTP Gmail (587/STARTTLS), pièce jointe GPX + corps avec
  distance, nombre de points et aperçu Google Maps.
- `BootReceiver.kt` — réarme l'alarme après redémarrage.
- `Prefs.kt` — identifiants chiffrés + état.
- CI : `.github/workflows/build.yml` compile à chaque push sur `main` et publie la release
  `latest` avec `sync.apk`. Signé avec `app/keystore/sync.jks` (clé fixe → mises à jour
  par-dessus possibles).

## Limites connues / compromis (à surveiller pour v2)
1. **Fiabilité du 5 min sans notification** : en mode économie d'énergie (Doze), Android peut
   décaler un relevé. L'exemption d'optimisation batterie (étape C) limite fortement le
   problème. C'est le meilleur compromis possible sans notification.
2. **Fenêtre du BroadcastReceiver** : le relevé GPS a un timeout de 40 s ; les alarmes sont
   des broadcasts d'arrière-plan (fenêtre ~60 s), donc OK, mais à surveiller sur certains OEM
   agressifs (Xiaomi, Huawei, Samsung) qui tuent les apps en fond → il peut falloir ajouter
   l'app à la liste « autoriser en arrière-plan / auto-start » du constructeur.
3. **« Mise à jour automatique de l'app »** : le build est automatique à chaque push, mais
   l'installation de l'APK sur un téléphone non-rooté demande un tap (Android l'exige). Une
   mise à jour 100 % silencieuse n'est pas possible sans root/appareil managé. Documenté.
4. **Reconfiguration après masquage d'icône** : actuellement il faut réinstaller l'APK pour
   rouvrir l'écran de config. V2 possible : code secret au clavier du téléphone (SECRET_CODE)
   ou petit lien de relance.
5. **Sécurité** : le dépôt doit rester privé (contient la clé de signature ; l'app stocke un
   mot de passe d'application Gmail).

## Idées v2 (backlog)
- Vérificateur de mise à jour intégré (télécharge la dernière release et propose l'install).
- Stats "distance du jour vs jours précédents" directement dans le mail (objectif de l'utilisateur :
  augmenter les distances). Facile à ajouter dans `EmailSender.buildBody` en agrégeant par jour.
- Lien direct "ouvrir dans Google Maps" plus riche, ou génération KML colorée.
- Filtrage des points aberrants (précision > X mètres) avant le GPX.
- Réglage des intervalles (5 min / 6 h) depuis l'écran de config.

## Comment reprendre
Tout le projet est dans le dossier livré `sync-gps` (voir README.md pour l'installation).
Si la CI échoue au premier build, lire le log de l'onglet Actions et corriger (dépendances /
versions AGP). Rien n'a pu être compilé côté Cowork faute de SDK Android.
