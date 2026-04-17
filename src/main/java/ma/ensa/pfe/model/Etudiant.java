package ma.ensa.pfe.model;

import jakarta.persistence.*;

@Entity
@Table(name = "etudiants")
public class Etudiant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    // GI ou ID
    @Column(nullable = false)
    private String filiere;

    // FR ou EN
    @Column(nullable = false)
    private String langue = "FR";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_id")
    private Professeur encadrant;

    public Etudiant() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public String getFiliere() { return filiere; }
    public void setFiliere(String filiere) { this.filiere = filiere; }
    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }
    public Professeur getEncadrant() { return encadrant; }
    public void setEncadrant(Professeur encadrant) { this.encadrant = encadrant; }

    @Override
    public String toString() {
        return nom + " " + prenom;
    }
}