package ma.ensa.pfe.model;

import jakarta.persistence.*;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "professeurs")
public class Professeur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    // Ex: "Informatique", "Mathématiques"
    private String specialite;

    // true = peut être jury pour présentation EN
    @Column(nullable = false)
    private boolean parleAnglais = false;

    public Professeur() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getSpecialite() { return specialite; }
    public void setSpecialite(String specialite) { this.specialite = specialite; }
    public boolean isParleAnglais() { return parleAnglais; }
    public void setParleAnglais(boolean parleAnglais) { this.parleAnglais = parleAnglais; }

    @Override
    public String toString() {
        return nom + " " + prenom;
    }
}