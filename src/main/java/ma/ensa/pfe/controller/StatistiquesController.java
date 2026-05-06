package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.StatistiquesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Contrôleur pour le tableau de bord statistiques et l'API JSON du calendrier.
 *
 * @author Membre A
 */
@Controller
public class StatistiquesController {

    @Autowired private StatistiquesService    statistiquesService;
    @Autowired private SoutenanceRepository   soutenanceRepository;

    // ══════════════════════════════════════════════
    //  TABLEAU DE BORD STATISTIQUES
    // ══════════════════════════════════════════════

    @GetMapping("/stats")
    public String dashboard(Model model) {
        model.addAttribute("chargeParProf",      statistiquesService.getChargeParProfesseur());
        model.addAttribute("soutenancesParJour", statistiquesService.getSoutenancesParJour());
        model.addAttribute("repartitionFiliere", statistiquesService.getRepartitionFiliere());
        model.addAttribute("repartitionLangue",  statistiquesService.getRepartitionLangue());
        model.addAttribute("totalSoutenances",   statistiquesService.getTotalSoutenances());
        model.addAttribute("totalProfesseurs",   statistiquesService.getTotalProfesseurs());
        model.addAttribute("totalEtudiants",     statistiquesService.getTotalEtudiants());
        model.addAttribute("nbJours",            statistiquesService.getNbJours());
        model.addAttribute("chargeMax",          statistiquesService.getChargeMax());
        model.addAttribute("activePage", "stats");
        return "stats/dashboard";
    }

    // ══════════════════════════════════════════════
    //  API JSON POUR FULLCALENDAR
    // ══════════════════════════════════════════════

    /**
     * Retourne les soutenances au format JSON attendu par FullCalendar 6.
     * Couleur par salle : chaque salle a une couleur distincte.
     * GET /planning/json
     */
    @GetMapping("/planning/json")
    @ResponseBody
    public List<Map<String, Object>> getPlanningJson() {
        List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
        List<Map<String, Object>> events = new ArrayList<>();

        // Palette de couleurs par salle (index tournant)
        Map<String, String> couleursSalles = new LinkedHashMap<>();
        String[] palette = {"#2e6da4", "#e67e22", "#27ae60", "#8e44ad", "#c0392b", "#16a085"};
        int[] idx = {0};

        for (Soutenance s : soutenances) {
            String nomSalle = s.getSalle().getNom();

            // Attribuer une couleur unique par salle
            couleursSalles.computeIfAbsent(nomSalle, k -> {
                String couleur = palette[idx[0] % palette.length];
                idx[0]++;
                return couleur;
            });

            String couleur = couleursSalles.get(nomSalle);

            // Titre affiché dans le calendrier
            String titre = s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom()
                    + " — " + nomSalle;

            // Heure de début et de fin (ISO 8601)
            String start = s.getDate() + "T" + s.getHeure();
            String end   = s.getDate() + "T" + s.getHeureFin();

            // Propriétés étendues pour le popup détail
            Map<String, Object> extendedProps = new LinkedHashMap<>();
            extendedProps.put("etudiant",   s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom());
            extendedProps.put("filiere",    s.getEtudiant().getFiliere().name());
            extendedProps.put("langue",     s.getEtudiant().getLangue().name());
            extendedProps.put("encadrant",  s.getEncadrant().getNomComplet());
            extendedProps.put("jury1",      s.getJury1() != null ? s.getJury1().getNomComplet() : "—");
            extendedProps.put("jury2", s.getJury2() != null ? s.getJury2().getNomComplet() : "—");
            extendedProps.put("jury3", s.getJury3() != null ? s.getJury3().getNomComplet() : "—");
            extendedProps.put("salle",      nomSalle);
            extendedProps.put("heure",      s.getHeure().toString());
            extendedProps.put("heureFin",   s.getHeureFin().toString());
            extendedProps.put("titre",      s.getEtudiant().getTitreProjet() != null
                                            ? s.getEtudiant().getTitreProjet() : "");

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id",             s.getId());
            event.put("title",          titre);
            event.put("start",          start);
            event.put("end",            end);
            event.put("backgroundColor", couleur);
            event.put("borderColor",    couleur);
            event.put("extendedProps",  extendedProps);

            events.add(event);
        }

        return events;
    }
}
