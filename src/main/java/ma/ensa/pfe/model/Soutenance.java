package ma.ensa.pfe.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "soutenances")
public class Soutenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'étudiant qui soutient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    // L'encadrant (prof qui a suivi l'étudiant)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_id", nullable = false)
    private Professeur encadrant;

    // Les 2 membres du jury
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jury1_id")
    private Professeur jury1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jury2_id")
    private Professeur jury2;

    // La salle
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    // Date et heure
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime heure;

    // Durée en minutes (généralement 30)
    private int dureeMn = 30;

    // Constructeurs
    public Soutenance() {}

    // Getters / Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Etudiant getEtudiant() { return etudiant; }
    public void setEtudiant(Etudiant etudiant) { this.etudiant = etudiant; }
    public Professeur getEncadrant() { return encadrant; }
    public void setEncadrant(Professeur encadrant) { this.encadrant = encadrant; }
    public Professeur getJury1() { return jury1; }
    public void setJury1(Professeur jury1) { this.jury1 = jury1; }
    public Professeur getJury2() { return jury2; }
    public void setJury2(Professeur jury2) { this.jury2 = jury2; }
    public Salle getSalle() { return salle; }
    public void setSalle(Salle salle) { this.salle = salle; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getHeure() { return heure; }
    public void setHeure(LocalTime heure) { this.heure = heure; }
    public int getDureeMn() { return dureeMn; }
    public void setDureeMn(int dureeMn) { this.dureeMn = dureeMn; }

    // Méthode utile pour l'algorithme
    public LocalTime getHeureFin() {
        return heure.plusMinutes(dureeMn);
    }
}