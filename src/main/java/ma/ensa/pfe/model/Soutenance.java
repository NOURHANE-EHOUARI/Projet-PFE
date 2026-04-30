package ma.ensa.pfe.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "soutenances")
public class Soutenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== LIENS VERS LES ACTEURS =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etudiant_id", nullable = false)
    private Etudiant etudiant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encadrant_id", nullable = false)
    private Professeur encadrant;

    // ✅ Jury 1 = encadrant (membre du jury)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jury1_id")
    private Professeur jury1;

    // ✅ Jury 2 = premier prof externe
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jury2_id")
    private Professeur jury2;

    // ✅ Jury 3 = deuxième prof externe
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jury3_id")
    private Professeur jury3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private VersionPlanning version;

    // ===== DONNÉES TEMPORELLES =====
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime heure;

    @Column(name = "duree_mn", nullable = false)
    private int dureeMn = 45;

    // ===== CONSTRUCTEURS =====
    public Soutenance() {}

    // ===== GETTERS / SETTERS =====
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

    public Professeur getJury3() { return jury3; }
    public void setJury3(Professeur jury3) { this.jury3 = jury3; }

    public Salle getSalle() { return salle; }
    public void setSalle(Salle salle) { this.salle = salle; }

    public VersionPlanning getVersion() { return version; }
    public void setVersion(VersionPlanning version) { this.version = version; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getHeure() { return heure; }
    public void setHeure(LocalTime heure) { this.heure = heure; }

    public int getDureeMn() { return dureeMn; }
    public void setDureeMn(int dureeMn) { this.dureeMn = dureeMn; }

    // ===== MÉTHODES UTILITAIRES =====

    /**
     * Retourne l'heure de fin de la soutenance.
     */
    public LocalTime getHeureFin() {
        if (heure == null) return null;
        return heure.plusMinutes(dureeMn);
    }

    /**
     * Vérifie si cette soutenance chevauche une autre dans la même salle.
     */
    public boolean chevauche(Soutenance autre) {
        if (!this.date.equals(autre.date)) return false;
        if (!this.salle.getId().equals(autre.salle.getId())) return false;
        LocalTime finThis  = this.getHeureFin();
        LocalTime finAutre = autre.getHeureFin();
        return (this.heure.isBefore(finAutre) && finThis.isAfter(autre.heure));
    }

    /**
     * Retourne tous les profs impliqués dans cette soutenance.
     * ✅ encadrant + jury1 (encadrant) + jury2 + jury3
     * Note : jury1 = encadrant, donc encadrant apparaît une seule fois.
     */
    public List<Professeur> getAllProfsImpliques() {
        List<Professeur> profs = new ArrayList<>();
        if (encadrant != null) profs.add(encadrant);
        // jury1 = encadrant, on l'ajoute pas en double
        if (jury2 != null) profs.add(jury2);
        if (jury3 != null) profs.add(jury3);
        return profs;
    }

    /**
     * Retourne les 3 membres du jury (jury1=encadrant, jury2, jury3).
     */
    public List<Professeur> getJury() {
        List<Professeur> jury = new ArrayList<>();
        if (jury1 != null) jury.add(jury1); // encadrant
        if (jury2 != null) jury.add(jury2);
        if (jury3 != null) jury.add(jury3);
        return jury;
    }

    @Override
    public String toString() {
        return String.format("Soutenance[%s %s %s | Étudiant: %s | Encadrant: %s]",
            date, heure, salle != null ? salle.getNom() : "?",
            etudiant != null ? etudiant.getNom() : "?",
            encadrant != null ? encadrant.getNom() : "?");
    }
}