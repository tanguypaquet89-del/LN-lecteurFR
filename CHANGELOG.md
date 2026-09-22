# 📜 Journal des modifications (Changelog)

Toutes les versions et fonctionnalités notables de **LN lecteurFR** sont répertoriées dans ce document.  
Le format suit les principes de [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/) et du [Semantic Versioning](https://semver.org/lang/fr/).

---

## [1.4.0-beta01] - Première Bêta Publique (2026-09-22)

> 🚀 **Bienvenue dans la première version publique de LN lecteurFR !**  
> Cette version regroupe l'ensemble des fondations de l'application : un lecteur puissant et hautement personnalisable, un navigateur intelligent capable d'extraire des romans depuis le web, un gestionnaire complet de téléchargement hors-ligne, ainsi qu'une suite complète d'outils d'import/export et de synchronisation (Google Drive & P2P local).

---

### 📖 1. Expérience de Lecture (Le Lecteur)
- **Modes d'affichage adaptatifs** :
  - **Défilement continu (Webtoon / Scroll)** : lecture fluide verticale sans rupture de page.
  - **Mode Pagination (Livre)** : navigation par simple toucher sur les côtés de l'écran ou glissement horizontal.
- **Thèmes de lecture immersifs** :
  - **Clair** : fond blanc avec contraste doux pour la journée.
  - **Sépia** : teinte chaude reposante pour limiter la fatigue oculaire.
  - **Sombre** : gris ardoise élégant pour les environnements peu éclairés.
  - **Noir OLED / AMOLED** : noir absolu (#000000) pour une immersion totale et une économie maximale de batterie.
- **Typographie & Mise en page avancée** :
  - Choix parmi plusieurs polices de caractères intégrées (*Cinzel*, *Merriweather*, *Roboto Serif*, *Lora*, ainsi que la police système).
  - Réglage précis de la taille de texte (avec prévisualisation en direct).
  - Contrôle de la hauteur d'interligne et de l'espacement des paragraphes.
  - Ajustement des marges latérales pour une largeur de lecture idéale sur smartphone ou tablette.
- **Navigation & Reprise instantanée** :
  - Mémorisation automatique et instantanée du chapitre et de la position exacte de lecture (scroll).
  - Table des matières intégrée avec recherche rapide de chapitre et accès direct.
  - Raccourcis tactiles pour passer rapidement au chapitre précédent ou suivant.

---

### 📚 2. Gestion de la Bibliothèque
- **Organisation sur-mesure** :
  - Catégorisation personnalisée des romans (possibilité de créer et nommer ses propres catégories).
  - Filtres par statut de lecture : *Tous*, *En cours*, *Non lu*, *Terminé*, *Favoris*.
  - Recherche multi-champs instantanée : filtrage par titre, auteur ou genres/tags.
  - Tri dynamique : par dernière lecture, date d'ajout, ordre alphabétique ou nombre total de chapitres.
- **Indicateurs visuels clairs** :
  - Badge de disponibilité hors-ligne sur les jaquettes.
  - Barre de progression de lecture pour chaque roman (ex: 45 / 120 chapitres).
  - Écran d'accueil convivial pour guider les nouveaux utilisateurs vers leurs premières découvertes.
- **Contrôle fin du stockage** :
  - Affichage précis de l'espace disque occupé par chaque roman (Mo / Ko).
  - Option de purge du cache : suppression des chapitres stockés en local sans retirer le roman de sa bibliothèque.

---

### 📥 3. Téléchargements & Mode Hors-ligne
- **Gestionnaire de téléchargements asynchrone** :
  - Téléchargement individuel d'un chapitre, par lot ou de l'intégralité d'un roman en un seul clic.
  - File d'attente intelligente avec reprise automatique après incident ou coupure réseau.
  - Service d'arrière-plan Android (*Foreground Service*) garantissant que les téléchargements se poursuivent même écran éteint.
  - Notification système interactive avec barre de progression en direct.
- **Mises à jour automatiques des chapitres** :
  - Tâche d'arrière-plan planifiée (*WorkManager*) vérifiant périodiquement les nouveaux chapitres disponibles pour vos romans enregistrés.
  - Notification discrète dès qu'un nouveau chapitre est détecté.

---

### 🌐 4. Sources de Romans & Navigateur Intégré
- **Sources natives intégrées** :
  - Prise en charge native et extraction optimisée pour les catalogues :
    - *NovelFrance.fr*
    - *ChiReads*
    - *Soreyawari*
    - *Trad-Index*
    - *Purrfiction*
- **Système de Parseurs Dynamiques (JSON)** :
  - Moteur d'extraction modulaire permettant d'ajouter ou d'éditer des règles de scraping sans devoir recompiler ni mettre à jour l'application.
- **Navigateur Web avec Auto-Détection** :
  - Navigateur intégré avec barre d'adresse, navigation classique et compatibilité JavaScript.
  - Système d'analyse en temps réel : dès qu'une page de roman est visitée, l'application détecte automatiquement le titre, l'auteur, le synopsis, la jaquette et la liste complète des chapitres et propose un bouton d'ajout en 1 clic.

---

### 📦 5. Formats, Importation & Exportation
- **Prise en charge native de l'EPUB** :
  - **Import EPUB** : chargez n'importe quel livre numérique au format standard `.epub` depuis votre appareil (les chapitres et métadonnées sont automatiquement parsés).
  - **Export EPUB** : transformez n'importe quel roman de votre bibliothèque en livre électronique standard pour l'ouvrir sur vos liseuses ou applications tierces.
- **Export PDF** :
  - Génération de documents PDF formatés et prêts à être lus, archivés ou imprimés.
- **Export Texte Brut (.txt)** :
  - Extraction rapide du texte pour une compatibilité universelle.
- **Sauvegarde & Restauration de Bibliothèque** :
  - Sauvegarde locale d'un instantané complet de votre bibliothèque (données, progression, catégories) dans un fichier JSON exportable et réimportable à tout moment.

---

### 🔄 6. Synchronisation Multi-Appareils
- **Google Drive Sync** :
  - Connexion sécurisée avec votre compte Google (Google Sign-In).
  - Sauvegarde automatique ou manuelle de la bibliothèque et des EPUB sur votre propre espace cloud Google Drive personnel.
  - Restauration de l'historique de lecture sur un nouvel appareil en quelques secondes.
- **Synchronisation P2P Locale (Nearby Connections)** :
  - Transfert direct d'un smartphone Android à un autre via Wi-Fi Direct et Bluetooth sans nécessiter d'accès à Internet.
  - Idéal pour partager des romans ou migrer rapidement vers un nouveau téléphone en voyage ou hors réseau.

---

### 🎨 7. Design, Ergonomie & Performances
- **100 % Jetpack Compose** avec les directives **Material Design 3 (M3)**.
- **Couleurs dynamiques Material You** : l'interface s'accorde harmonieusement avec le fond d'écran et la palette de votre système (Android 12+).
- **Icône adaptative personnalisée** : icône vectorielle soignée représentant un roman ouvert avec badge de téléchargement et support du mode monochrome.
- **Optimisation R8 / ProGuard** : code compacté, démarrage instantané et protection des modèles de données.
- **Interface épurée** : suppression de tout élément technique de débogage pour une expérience utilisateur prête pour le grand public.
