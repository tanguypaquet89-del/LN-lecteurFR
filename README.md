# 📖 LN lecteurFR (LecteurNovel)

> **Application Android moderne de lecture et de téléchargement de Webnovels & Light Novels en français.**  
> Profitez d'une lecture fluide, personnalisable et 100 % hors-ligne avec synchronisation cloud et locale.

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Beta Release](https://img.shields.io/badge/Release-v1.4.0--beta01-orange?style=for-the-badge)](https://github.com)

---

## ✨ Fonctionnalités Principales

### 📚 Bibliothèque & Gestion des Romans
- **Lecture hors-ligne complète** : Téléchargement rapide des chapitres en arrière-plan avec file d'attente intelligente.
- **Organisation par catégories & statut** : Classez vos lectures (En cours, Terminé, En pause, À lire).
- **Mise à jour automatique des chapitres** : Vérification périodique des nouveaux chapitres via WorkManager avec notifications.
- **Import & Export multiples formats** :
  - Import / Export **EPUB**
  - Export **PDF** et texte brut (**TXT**)
  - Sauvegarde et restauration complète de la bibliothèque (format JSON chiffrable).

### 🎨 Expérience de Lecture Hautement Personnalisable
- **Moteur de rendu Jetpack Compose** avec défilement fluide ou pagination.
- **Thèmes riches** : Mode Sombre, Clair, Sépia, Noir OLED (Amoled) et adaptation dynamique Material You (Android 12+).
- **Typographie personnalisée** : Choix de polices (Roboto, Serif, OpenDyslexic...), réglage de la taille, interligne et marges horizontales.
- **Sauvegarde automatique de la progression** : Reprise instantanée à la ligne exacte de votre dernière lecture.

### 🌐 Navigateur Intégré & Sources Françaises
- **Sources natives intégrées** :
  - *NovelFrance*
  - *ChiReads*
  - *Soreyawari*
  - *Trad-Index*
  - *Purrfiction*
- **Système de parseurs dynamiques** : Ajoutez ou modifiez des sources directement au format JSON sans devoir recompiler l'application.
- **Navigateur Web avec mode extraction** : Détecte et extrait automatiquement les métadonnées et chapitres d'un roman depuis n'importe quel site web.

### 🔄 Synchronisation Multiappareils
- **Google Drive** : Sauvegarde automatique de la bibliothèque et des fichiers EPUB sur votre espace Drive personnel.
- **Synchronisation P2P locale (Nearby Connections)** : Transférez vos romans et sauvegardes directement d'un smartphone Android à un autre en Wi-Fi Direct / Bluetooth sans connexion Internet.

---

## 📱 Téléchargement & Installation

1. Rendez-vous dans la section **[Releases](https://github.com)** du dépôt.
2. Téléchargez le dernier fichier **`LN-lecteurFR-v1.4.0-beta01.apk`**.
3. Sur votre smartphone Android :
   - Ouvrez le fichier téléchargé.
   - Autorisez l'installation d'applications de sources inconnues si demandé.
   - Profitez de votre lecture !

> **Configuration minimale requise :** Android 7.0 (Nougat, API 24) ou version supérieure.

---

## 🛠️ Stack Technique

- **Langage** : [Kotlin](https://kotlinlang.org/) (Coroutines, Flow, Serialization)
- **UI** : [Jetpack Compose](https://developer.android.com/jetpack/compose) avec Material Design 3
- **Base de données** : [Room Database](https://developer.android.com/training/data-storage/room) avec SQLite
- **Injection de dépendances** : [Hilt (Dagger)](https://dagger.dev/hilt/)
- **Tâches d'arrière-plan** : [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Réseau & Web scraping** : OkHttp, Jsoup, WebView
- **Services Google & P2P** : Google Drive REST API v3, Google Sign-In, Google Play Services Nearby Connections

---

## 🤝 Contribution & Retours de Bugs

Les contributions sont les bienvenues ! Pour signaler un bug ou proposer une nouvelle fonctionnalité :
1. Consultez les [Issues existantes](https://github.com) pour éviter les doublons.
2. Ouvrez un ticket en utilisant le modèle de rapport de bug ou de suggestion.
3. Pour soumettre du code, créez une branche dédiée et ouvrez une Pull Request.

---

## 📄 Licence

Ce projet est distribué sous licence **MIT**. Consultez le fichier [LICENSE](LICENSE) pour plus de détails.
