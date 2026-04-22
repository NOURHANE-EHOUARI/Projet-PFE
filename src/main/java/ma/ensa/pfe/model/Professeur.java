package ma.ensa.pfe.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
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
    private String specialite;

    @NotNull(message = "Ce champ est obligatoire")
    @Column(name = "parle_anglais", nullable = false)
    private Boolean parleAnglais = false;

    // ✅ Étudiants encadrés par ce professeur (corrigé)
    @OneToMany(
        mappedBy = "encadrant", 
        fetch = FetchType.LAZY, 
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private Set<Etudiant> etudiantsEncadres = new HashSet<>();

    // ✅ Contraintes d'indisponibilité du professeur
    @OneToMany(
        mappedBy = "professeur", 
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL, 
        orphanRemoval = true
    )
    private Set<Contrainte> contraintes = new HashSet<>();

    // ✅ Méthodes utilitaires pour synchronisation bidirectionnelle
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

    // Méthode utilitaire
    public String getNomComplet() {
        return nom + " " + prenom;
    }

    // Nécessaire car Lombok génère getParleAnglais() pour Boolean (objet),
    // mais PlanificationService appelle isParleAnglais()
    public boolean isParleAnglais() {
        return Boolean.TRUE.equals(parleAnglais);
    }
}