package ma.ensa.pfe.service;

import ma.ensa.pfe.model.*;
import ma.ensa.pfe.model.Etudiant.Filiere;
import ma.ensa.pfe.model.Etudiant.Langue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ma.ensa.pfe.dao.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires du PlanificationService.
 * Vérifie : génération sans conflit, délai 1h, contrainte anglais, équité.
 *
 * @author Membre A
 */
@ExtendWith(MockitoExtension.class)
class PlanificationServiceTest {

    @InjectMocks
    private PlanificationService planificationService;

    @Mock private EtudiantRepository etudiantRepository;
    @Mock private ProfesseurRepository professeurRepository;
    @Mock private SalleRepository salleRepository;
    @Mock private SoutenanceRepository soutenanceRepository;
    @Mock private ContrainteRepository contrainteRepository;

    // ── Données de test partagées ──
    private List<Professeur> professeurs;
    private List<Salle> salles;
    private List<LocalDate> jours;

    @BeforeEach
    void setUp() {
        professeurs = creerProfesseurs();
        salles      = creerSalles();
        jours       = List.of(
                LocalDate.of(2025, 6, 10),
                LocalDate.of(2025, 6, 11),
                LocalDate.of(2025, 6, 12)
        );

        // Par défaut : aucune contrainte d'indisponibilité
        when(contrainteRepository.findByProfesseur(any())).thenReturn(List.of());
        // Sauvegardes sans effet sur la logique
        when(soutenanceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    // ══════════════════════════════════════════════
    //  TEST 1 — Génération sans conflit
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("10 étudiants → planning généré sans aucun conflit de créneau")
    void generer_10Etudiants_AucunConflit() {
        List<Etudiant> etudiants = creerEtudiants(10, Langue.FR);

        List<Soutenance> result = planificationService.genererPlanning(
                etudiants, professeurs, salles, jours);

        assertEquals(10, result.size(), "Toutes les soutenances doivent être créées");

        // Vérifier l'absence de conflits : même salle + même heure + même jour = interdit
        for (int i = 0; i < result.size(); i++) {
            for (int j = i + 1; j < result.size(); j++) {
                Soutenance s1 = result.get(i);
                Soutenance s2 = result.get(j);
                if (s1.getDate().equals(s2.getDate()) && s1.getHeure().equals(s2.getHeure())) {
                    assertNotEquals(s1.getSalle().getId(), s2.getSalle().getId(),
                            "Deux soutenances au même créneau ne peuvent pas être dans la même salle");
                }
            }
        }
    }

    // ══════════════════════════════════════════════
    //  TEST 2 — Délai 1h respecté
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("Un prof ne peut pas avoir deux soutenances à moins d'1h d'intervalle")
    void delai1h_EntreDeuxSoutenancesMemeJury() {
        List<Etudiant> etudiants = creerEtudiants(6, Langue.FR);

        List<Soutenance> result = planificationService.genererPlanning(
                etudiants, professeurs, salles, jours);

        for (Soutenance s1 : result) {
            for (Soutenance s2 : result) {
                if (s1 == s2 || !s1.getDate().equals(s2.getDate())) continue;

                boolean memeJury = partageUnJury(s1, s2);
                if (!memeJury) continue;

                long diff = Math.abs(
                        s1.getHeure().toSecondOfDay() / 60
                                - s2.getHeure().toSecondOfDay() / 60);
                assertTrue(diff >= 60,
                        "Le délai minimum de 60 min entre soutenances du même jury n'est pas respecté. "
                                + "Différence : " + diff + " min");
            }
        }
    }

    // ══════════════════════════════════════════════
    //  TEST 3 — Contrainte anglais
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("Étudiant EN → au moins un jury parleAnglais=true")
    void etudiantEN_JuryAnglophones() {
        List<Etudiant> etudiants = creerEtudiants(3, Langue.EN);

        List<Soutenance> result = planificationService.genererPlanning(
                etudiants, professeurs, salles, jours);

        for (Soutenance s : result) {
            if (s.getEtudiant().getLangue() == Langue.EN) {
                boolean juryParleAnglais = s.getJury1().isParleAnglais()
                        || s.getJury2().isParleAnglais();
                assertTrue(juryParleAnglais,
                        "Soutenance EN : au moins un jury doit parler anglais pour "
                                + s.getEtudiant().getNom());
            }
        }
    }

    // ══════════════════════════════════════════════
    //  TEST 4 — Encadrant ne peut pas être jury
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("L'encadrant d'un étudiant ne peut pas être son jury")
    void encadrant_PasJuryDeSonEtudiant() {
        List<Etudiant> etudiants = creerEtudiants(5, Langue.FR);

        List<Soutenance> result = planificationService.genererPlanning(
                etudiants, professeurs, salles, jours);

        for (Soutenance s : result) {
            Long encId = s.getEncadrant().getId();
            assertNotEquals(encId, s.getJury1().getId(),
                    "L'encadrant ne peut pas être jury1");
            assertNotEquals(encId, s.getJury2().getId(),
                    "L'encadrant ne peut pas être jury2");
        }
    }

    // ══════════════════════════════════════════════
    //  TEST 5 — estProfDisponible respecte les contraintes BDD
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("estProfDisponible retourne false si contrainte d'indisponibilité bloque le créneau")
    void profIndisponible_CrenauBloque() {
        Professeur prof = professeurs.get(0);
        LocalDate jour = jours.get(0);
        LocalTime heureTest = LocalTime.of(9, 0);

        Contrainte contrainte = new Contrainte();
        contrainte.setId(1L);
        contrainte.setProfesseur(prof);
        contrainte.setDateIndisponible(jour);
        contrainte.setHeureDebut(LocalTime.of(8, 30));
        contrainte.setHeureFin(LocalTime.of(10, 0));

        when(contrainteRepository.findByProfesseur(prof)).thenReturn(List.of(contrainte));

        boolean dispo = planificationService.estProfDisponible(prof, jour, heureTest, List.of());

        assertFalse(dispo, "Le prof doit être indisponible sur ce créneau");
    }

    // ══════════════════════════════════════════════
    //  TEST 6 — Équité de la charge
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("La charge est équitablement répartie — aucun prof ne dépasse 8 soutenances")
    void equite_ChargeMaximaleRespectee() {
        List<Etudiant> etudiants = creerEtudiants(10, Langue.FR);

        List<Soutenance> result = planificationService.genererPlanning(
                etudiants, professeurs, salles, jours);

        for (Professeur prof : professeurs) {
            long charge = planificationService.compterSoutenances(prof, result);
            assertTrue(charge <= 8,
                    "Prof " + prof.getNom() + " a trop de soutenances : " + charge);
        }
    }

    // ══════════════════════════════════════════════
    //  TEST 7 — Exception si aucun jour fourni
    // ══════════════════════════════════════════════

    @Test
    @DisplayName("PlanificationException si aucun jour disponible")
    void exception_AucunJour() {
        List<Etudiant> etudiants = creerEtudiants(2, Langue.FR);

        assertThrows(PlanificationService.PlanificationException.class, () ->
                planificationService.genererPlanning(etudiants, professeurs, salles, List.of()));
    }

    // ══════════════════════════════════════════════
    //  HELPERS DE CRÉATION DE DONNÉES
    // ══════════════════════════════════════════════

    private List<Professeur> creerProfesseurs() {
        List<Professeur> list = new ArrayList<>();
        String[] noms = {"BADI", "BOUJRAF", "ALAMI", "IDRISSI", "BENNANI",
                "CHERKAOUI", "FILALI", "HAJJI", "MANSOURI", "ZOUHRI"};
        for (int i = 0; i < noms.length; i++) {
            Professeur p = new Professeur();
            p.setId((long) (i + 1));
            p.setNom(noms[i]);
            p.setPrenom("Prenom" + i);
            p.setSpecialite("GI");
            p.setParleAnglais(i % 3 == 0); // 1 prof sur 3 parle anglais
            p.setContraintes(new ArrayList<>());
            list.add(p);
        }
        return list;
    }

    private List<Salle> creerSalles() {
        List<Salle> list = new ArrayList<>();
        String[] noms = {"S4A", "S4B", "S5A"};
        for (int i = 0; i < noms.length; i++) {
            Salle s = new Salle();
            s.setId((long) (i + 1));
            s.setNom(noms[i]);
            s.setCapacite(30);
            list.add(s);
        }
        return list;
    }

    private List<Etudiant> creerEtudiants(int nombre, Langue langue) {
        List<Etudiant> list = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            Etudiant e = new Etudiant();
            e.setId((long) (i + 1));
            e.setNom("ETUDIANT" + i);
            e.setPrenom("Prenom" + i);
            e.setFiliere(i % 2 == 0 ? Filiere.GI : Filiere.ID);
            e.setLangue(langue);
            // Encadrant : chaque étudiant a un encadrant différent (rotation)
            e.setEncadrant(professeurs.get(i % professeurs.size()));
            list.add(e);
        }
        return list;
    }

    /** Vérifie si deux soutenances partagent au moins un jury (jury1 ou jury2). */
    private boolean partageUnJury(Soutenance s1, Soutenance s2) {
        return s1.getJury1().getId().equals(s2.getJury1().getId())
                || s1.getJury1().getId().equals(s2.getJury2().getId())
                || s1.getJury2().getId().equals(s2.getJury1().getId())
                || s1.getJury2().getId().equals(s2.getJury2().getId())
                || s1.getEncadrant().getId().equals(s2.getJury1().getId())
                || s1.getEncadrant().getId().equals(s2.getJury2().getId());
    }
}