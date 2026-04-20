package ma.ensa.pfe.model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
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
    // Étudiants encadrés par ce professeur
    @OneToMany(mappedBy = "encadrant", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Etudiant> etudiantsEncadres = new ArrayList<>();
    // Contraintes d'indisponibilité du professeur
    @OneToMany(mappedBy = "professeur", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contrainte> contraintes = new ArrayList<>();
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