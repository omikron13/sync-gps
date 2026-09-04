# Sync — Traceur GPS discret pour Android

Application Android minimaliste qui enregistre ta position **toutes les 5 minutes** et
envoie le trajet **toutes les 6 heures** par email (format **GPX**, affichable sur
Google Maps / Google My Maps). Aucune icône visible après la configuration, aucune
notification, tourne en arrière-plan.

> ⚠️ **Garde ce dépôt PRIVÉ.** Il contient une clé de signature (`app/keystore/sync.jks`)
> et l'app stockera ton mot de passe d'application Gmail sur le téléphone.

---

## 1. Mettre le code sur ton GitHub

1. Crée un dépôt **privé** sur GitHub, par exemple `sync-gps`.
2. Pousse ce dossier dedans :
   ```bash
   git init
   git add .
   git commit -m "Version initiale"
   git branch -M main
   git remote add origin https://github.com/TON_COMPTE/sync-gps.git
   git push -u origin main
   ```

## 2. GitHub compile l'APK tout seul

Dès que tu pousses sur `main`, l'action **Build APK** (`.github/workflows/build.yml`)
compile l'application et publie une *release* nommée **latest** avec le fichier
`sync.apk`.

- Lien de téléchargement toujours à jour :
  **`https://github.com/TON_COMPTE/sync-gps/releases/latest/download/sync.apk`**
- Ouvre ce lien depuis le navigateur de ton téléphone pour installer.

Tu n'as **pas besoin d'Android Studio**.

## 3. Préparer l'envoi de mail (une seule fois)

L'app envoie le mail via ton compte Gmail. Gmail exige un **mot de passe d'application** :

1. Active la validation en 2 étapes sur ton compte Google.
2. Va sur https://myaccount.google.com/apppasswords
3. Crée un mot de passe d'application (16 caractères) — tu le saisiras dans l'app.

## 4. Installer et configurer sur le téléphone (une seule fois)

1. Installe `sync.apk` (autorise « installer des applications inconnues » si demandé).
2. Ouvre l'app **Sync**. Suis les étapes dans l'ordre :
   - **A** — Autoriser la localisation.
   - **B** — Localisation en arrière-plan → choisis **« Toujours autoriser »**.
   - **C** — Désactiver l'optimisation batterie pour l'app (important pour la fiabilité).
   - **D** — Autoriser les alarmes exactes (Android 12+).
   - Saisis : ton Gmail d'envoi, le mot de passe d'application, le destinataire
     (`cashredac@gmail.com` par défaut).
   - **E** — Enregistrer et démarrer.
   - *(facultatif)* « Envoyer un mail de test » pour vérifier tout de suite.
   - **F** — Masquer l'icône. L'app disparaît du tiroir d'applications et continue
     de tourner en fond.

## 5. Voir ton trajet sur Google Maps

Chaque mail contient un fichier `.gpx` et un aperçu Google Maps :

1. Ouvre https://www.google.com/maps/d/ (Google My Maps).
2. **Créer une carte → Importer →** choisis le `.gpx` reçu par mail.
3. Ton trajet complet s'affiche comme une ligne sur la carte.

## 6. Mettre à jour l'app

Modifie le code, `git push` → GitHub recompile automatiquement → réinstalle
`sync.apk` (même lien `releases/latest`). Comme l'APK est signé avec la même clé,
l'installation se fait **par-dessus** l'ancienne sans désinstaller (tes réglages
sont conservés).

Pour rouvrir l'écran de configuration après avoir masqué l'icône : réinstalle
l'APK (l'icône réapparaît le temps de la config).

---

Voir **HANDOFF.md** pour l'état du projet, les décisions techniques et les limites connues.
