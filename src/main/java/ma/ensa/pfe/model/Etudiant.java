package ma.ensa.pfe.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "etudiants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Etudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ NOUVEAU : Identifiant unique national (pour éviter les doublons à l'import)
    @NotBlank(message = "Le CNE est obligatoire")
    @Column(unique = true, nullable = false)
    private String cne;

    @NotBlank(message = "Le nom est obligatoire")
    @Column(nullable = false)
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Column(nullable = false)
    private String prenom;
 
    @Column(name = "email_academique")
    private String email;

    @NotNull(message = "La filière est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Filiere filiere;

    @NotNull(message = "La langue est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Langue langue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_id", nullable = true)
    private Professeur encadrant;

    @Column(name = "titre_projet")
    private String titreProjet;

    // ✅ NOUVEAU : Relation inverse pour le planning
    // Permet de savoir si un étudiant a déjà une soutenance planifiée
    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Soutenance> soutenances;

    // ===== MÉTHODES UTILITAIRES =====

    /**
     * Setter simple pour l'encadrant (utilisé lors de l'import Excel).
     * Ne synchronise pas la collection LAZY du professeur pour éviter les erreurs.
     */
    public void setEncadrant(Professeur encadrant) {
        this.encadrant = encadrant;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Etudiant)) return false;
        Etudiant that = (Etudiant) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); 
    }

    /**
     * Setter avec synchronisation bidirectionnelle.
     * À utiliser uniquement dans un service @Transactional quand on manipule l'objet en mémoire.
     */
    public void setEncadrantWithSync(Professeur encadrant) {
        if (this.encadrant != null) {
            this.encadrant.getEtudiantsEncadres().remove(this);
        }
        this.encadrant = encadrant;
        if (encadrant != null) {
            encadrant.getEtudiantsEncadres().add(this);
        }
    }

    // ===== ENUMS MIS À JOUR SELON TES DONNÉES =====
    public enum Filiere { 
        GI,   // Génie Informatique
        TDIA, // Transformation Digitale et IA
        DATA  // Ingénierie de Données
    }

    public enum Langue { 
        FR, // Français
        EN  // Anglais
    }
}