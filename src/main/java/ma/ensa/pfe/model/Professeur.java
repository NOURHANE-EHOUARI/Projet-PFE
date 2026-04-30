package ma.ensa.pfe.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// ✅ AJOUT DES IMPORTS MANQUANTS
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "professeurs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Professeur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom est obligatoire")
    @Column(nullable = false)
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Column(nullable = false)
    private String prenom;

    @NotBlank(message = "La spécialité est obligatoire")
    @Column(nullable = false)
    private String specialite; // Ex: GI, TDIA, DATA, AUTRE

    @NotNull(message = "Ce champ est obligatoire")
    @Column(name = "parle_anglais", nullable = false)
    private Boolean parleAnglais = false;

    // ===== RELATIONS EXISTANTES =====

    // Étudiants encadrés par ce professeur
    @OneToMany(
        mappedBy = "encadrant", 
        fetch = FetchType.LAZY, 
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<Etudiant> etudiantsEncadres = new HashSet<>();

    // Contraintes d'indisponibilité du professeur
    @OneToMany(
        mappedBy = "professeur", 
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL, 
        orphanRemoval = true
    )
    private Set<Contrainte> contraintes = new HashSet<>();

    // ===== NOUVELLES RELATIONS POUR LE PLANNING =====

    // Soutenances où ce prof est l'encadrant principal
    @OneToMany(mappedBy = "encadrant", fetch = FetchType.LAZY)
    private List<Soutenance> soutenancesEncadrees;

    // Soutenances où ce prof est membre du jury (1, 2 ou 3)
    @OneToMany(mappedBy = "jury1", fetch = FetchType.LAZY)
    private List<Soutenance> soutenancesJury1;

    @OneToMany(mappedBy = "jury2", fetch = FetchType.LAZY)
    private List<Soutenance> soutenancesJury2;

    @OneToMany(mappedBy = "jury3", fetch = FetchType.LAZY)
    private List<Soutenance> soutenancesJury3;

    // ===== MÉTHODES UTILITAIRES =====

    public void addEtudiant(Etudiant etudiant) {
        etudiantsEncadres.add(etudiant);
        etudiant.setEncadrant(this);
    }

    public void removeEtudiant(Etudiant etudiant) {
        etudiantsEncadres.remove(etudiant);
        etudiant.setEncadrant(null);
    }

    public void addContrainte(Contrainte contrainte) {
        contraintes.add(contrainte);
        contrainte.setProfesseur(this);
    }

    public void removeContrainte(Contrainte contrainte) {
        contraintes.remove(contrainte);
        contrainte.setProfesseur(null);
    }

    public String getNomComplet() {
        return nom + " " + prenom;
    }

    public boolean isParleAnglais() {
        return Boolean.TRUE.equals(parleAnglais);
    }

    /**
     * Vérifie si le professeur est disponible à une date/heure donnée.
     */
    public boolean estDisponible(LocalDate date, LocalTime heureDebut, LocalTime heureFin) {
        // 1. Vérifier les contraintes d'indisponibilité explicites
        for (Contrainte c : contraintes) {
            if (c.getDateIndisponible().equals(date)) {
                // Si la plage horaire de la contrainte chevauche celle de la soutenance
                if (!(heureFin.isBefore(c.getHeureDebut()) || heureDebut.isAfter(c.getHeureFin()))) {
                    return false;
                }
            }
        }

        // 2. Vérifier les autres soutenances où ce prof est impliqué
        List<Soutenance> toutesSoutenances = new java.util.ArrayList<>();
        if (soutenancesEncadrees != null) toutesSoutenances.addAll(soutenancesEncadrees);
        if (soutenancesJury1 != null) toutesSoutenances.addAll(soutenancesJury1);
        if (soutenancesJury2 != null) toutesSoutenances.addAll(soutenancesJury2);
        if (soutenancesJury3 != null) toutesSoutenances.addAll(soutenancesJury3);

        for (Soutenance s : toutesSoutenances) {
            if (s.getDate().equals(date)) {
                LocalTime finSoutenance = s.getHeure().plusMinutes(s.getDureeMn());
                // Conflit si les plages se chevauchent
                if (!(heureFin.isBefore(s.getHeure()) || heureDebut.isAfter(finSoutenance))) {
                    return false;
                }
            }
        }

        return true;
    }
}