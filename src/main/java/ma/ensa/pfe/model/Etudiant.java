package ma.ensa.pfe.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Entity
@Table(name = "etudiants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Etudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
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

    @OneToMany(mappedBy = "etudiant", fetch = FetchType.LAZY)
    private List<Soutenance> soutenances;

    public void setEncadrant(Professeur encadrant) {
        this.encadrant = encadrant;
    }

    public void setEncadrantWithSync(Professeur encadrant) {
        if (this.encadrant != null) {
            this.encadrant.getEtudiantsEncadres().remove(this);
        }
        this.encadrant = encadrant;
        if (encadrant != null) {
            encadrant.getEtudiantsEncadres().add(this);
        }
    }

  
    public enum Filiere { 
        GI,   
        TDIA, 
        DATA 
    }

    public enum Langue { 
        FR, 
        EN 
    }
}