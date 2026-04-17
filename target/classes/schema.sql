-- ===== SCHEMA PFE PLANNING — ENSA AL HOCEIMA =====

DROP TABLE IF EXISTS contraintes;
DROP TABLE IF EXISTS soutenances;
DROP TABLE IF EXISTS etudiants;
DROP TABLE IF EXISTS professeurs;
DROP TABLE IF EXISTS salles;

CREATE TABLE professeurs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    specialite VARCHAR(100) NOT NULL,
    parle_anglais BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE etudiants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    filiere VARCHAR(10) NOT NULL,
    langue VARCHAR(5) NOT NULL,
    encadrant_id BIGINT NOT NULL,
    titre_projet VARCHAR(255),
    FOREIGN KEY (encadrant_id) REFERENCES professeurs(id)
);

CREATE TABLE salles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom VARCHAR(50) NOT NULL UNIQUE,
    capacite INT NOT NULL DEFAULT 30
);

CREATE TABLE contraintes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    professeur_id BIGINT NOT NULL,
    date_indisponible DATE NOT NULL,
    heure_debut TIME,
    heure_fin TIME,
    FOREIGN KEY (professeur_id) REFERENCES professeurs(id)
);