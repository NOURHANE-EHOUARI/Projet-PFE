# PFE Planning — Application Web Spring MVC
## Stack Technologique
lien du Video Demo :    https://drive.google.com/file/d/1SVkMcKrq56maW7mjbHqubfOnMzdLLpcH/view?usp=sharing

Application web de planification automatique des soutenances de Projets de Fin d'Études (PFE) — ENSA Al Hoceima 2024/2025.

## Stack Technologique

| Couche | Technologie |
|--------|-------------|
| Framework | Spring MVC 6 + Spring Data JPA |
| Vue | Thymeleaf 3.1 + Bootstrap 5 |
| Persistance | Hibernate 6 + H2 (dev)   |
| Excel | Apache POI 5.2 |
| PDF | iText 5.5 |
| Build | Maven 3.6+ |
| Serveur | Apache Tomcat 10.1 |
| Java | Java 17 |

## Prérequis

- Java 17+
- Maven 3.6+
- Apache Tomcat 10.1+
- Git

## Installation & Lancement

### 1. Cloner le projet

```bash
git clone https://github.com/NOURHANE-EHOUARI/Projet-PFE.git
cd Projet-PFE
```

### 2. Compiler et packager

```bash
mvn clean package -DskipTests
```

### 3. Déployer sur Tomcat

```bash
cp target/pfe-planning.war /opt/tomcat/webapps/
/opt/tomcat/bin/startup.sh
```

### 4. Accéder à l'application

http://localhost:8080/pfe-planning/

## Fonctionnalités

- **Import Excel** — Upload d'un fichier .xlsx avec les feuilles : etudiants, professeurs, salles, contraintes
- **Algorithme de planification** — Répartition équitable avec respect des contraintes métier
- **Planning interactif** — Tableau filtrable par date, salle, professeur
- **Export Excel** — Planning en 3 feuilles (global, par salle, par prof)
- **Export PDF** — Planning formaté avec entête ENSA
- **Dossier PVs** — Archive ZIP avec un PV PDF par étudiant, groupés par encadrant

## Contraintes métier respectées

1. Aucun jury ne peut avoir 2 soutenances simultanément
2. Une salle ne peut être utilisée qu'une seule fois par créneau
3. Au moins 1h d'intervalle entre 2 soutenances du même jury
4. Au moins 2 professeurs de la spécialité dans chaque jury
5. Professeur anglophone obligatoire si présentation en anglais
6. Couverture en ~3 jours

## Structure du projet

src/
├── main/
│   ├── java/ma/ensa/pfe/
│   │   ├── config/          # Configuration Hibernate
│   │   ├── controller/      # Controllers Spring MVC
│   │   ├── dao/             # Repositories Spring Data JPA
│   │   ├── model/           # Entités JPA
│   │   └── service/         # Services métier
│   ├── resources/           # SQL scripts
│   └── webapp/WEB-INF/
│       ├── views/           # Templates Thymeleaf
│       ├── applicationContext.xml
│       ├── dispatcher-servlet.xml
│       └── web.xml
└── test/                    # Tests JUnit 5 (22 tests)

## Structure du fichier Excel d'entrée

| Feuille | Colonnes |
|---------|----------|
| etudiants | nom, prenom, filiere (GI/ID), langue (FR/EN), encadrant |
| professeurs | nom, prenom, specialite, parle_anglais (O/N) |
| salles | nom, capacite |
| contraintes | prof_nom, date_indisponible, heure_debut, heure_fin |

## Lancer les tests

```bash
mvn clean test
# 22 tests — 0 failures
```
---
ENSA Al Hoceima — 2025/2026
