package ma.ensa.pfe.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "etudiants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Etudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le nom est obligatoire")
    @Column(nullable = false)
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    @Column(nullable = false)
    private String prenom;

    @NotNull(message = "La filière est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Filiere filiere;

    @NotNull(message = "La langue est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Langue langue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_id", nullable = false)
    private Professeur encadrant;

    @Column(name = "titre_projet")
    private String titreProjet;

    // Enums
    public enum Filiere { GI, ID }
    public enum Langue { FR, EN }
}