package ma.ensa.pfe.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "salles")
public class Salle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nom; // Ex: S4A, AMPHI A, Labo 1

    @Column(name = "capacite", nullable = false)
    private int capacite;

    // ===== RELATION INVERSE POUR LE PLANNING =====
    
    // Liste des soutenances qui se déroulent dans cette salle.
    // FetchType.LAZY est important pour ne pas charger tout l'historique quand on liste les salles.
    @OneToMany(mappedBy = "salle", fetch = FetchType.LAZY)
    private List<Soutenance> soutenances;

    // Constructeurs
    public Salle() {}

    public Salle(String nom, int capacite) {
        this.nom = nom;
        this.capacite = capacite;
    }

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public int getCapacite() { return capacite; }
    public void setCapacite(int capacite) { this.capacite = capacite; }

    public List<Soutenance> getSoutenances() { return soutenances; }
    public void setSoutenances(List<Soutenance> soutenances) { this.soutenances = soutenances; }

    @Override
    public String toString() {
        return "Salle{id=" + id + ", nom='" + nom + "', capacite=" + capacite + "}";
    }

    /**
     * Méthode utilitaire pour vérifier si la salle peut accueillir un jury de taille donnée.
     * Utile si tu as des contraintes de capacité strictes.
     */
    public boolean peutAccueillir(int nbPersonnes) {
        return this.capacite >= nbPersonnes;
    }
}