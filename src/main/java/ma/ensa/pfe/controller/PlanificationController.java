package ma.ensa.pfe.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.dao.VersionPlanningRepository;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.model.VersionPlanning;
import ma.ensa.pfe.service.AffectationService;
import ma.ensa.pfe.service.PlanificationService;

@Controller
@RequestMapping("/planning")
public class PlanificationController {

    @Autowired private AffectationService affectationService;
    @Autowired private PlanificationService planificationService;
    @Autowired private SoutenanceRepository soutenanceRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;

    /**
     * Affiche la liste des soutenances de la DERNIÈRE version générée.
     */
    @GetMapping
    public String afficherPlanning(Model model) {
        // Récupérer la dernière version pour l'affichage
        VersionPlanning derniereVersion = versionPlanningRepository.findFirstByOrderByDateGenerationDesc();
        
        if (derniereVersion != null) {
            // Charger les soutenances de cette version spécifique
            List<Soutenance> soutenances = soutenanceRepository.findByVersion(derniereVersion);
            model.addAttribute("soutenances", soutenances);
            model.addAttribute("versionActuelle", derniereVersion);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
            model.addAttribute("messageInfo", "Aucun planning généré pour le moment.");
        }

        // Pour le formulaire de génération, on propose les 3 prochains jours par défaut
        List<LocalDate> joursDefaut = new ArrayList<>();
        joursDefaut.add(LocalDate.now().plusDays(1));
        joursDefaut.add(LocalDate.now().plusDays(2));
        joursDefaut.add(LocalDate.now().plusDays(3));
        model.addAttribute("joursDefaut", joursDefaut);
        
        model.addAttribute("activePage", "planning");
        return "planning/tableau";
    }

    /**
     * Affiche le calendrier (vue graphique).
     */
    @GetMapping("/calendrier")
    public String afficherCalendrier(Model model) {
        VersionPlanning derniereVersion = versionPlanningRepository.findFirstByOrderByDateGenerationDesc();
        if (derniereVersion != null) {
            model.addAttribute("soutenances", soutenanceRepository.findByVersion(derniereVersion));
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
        }
        model.addAttribute("activePage", "planning");
        return "planning/calendrier";
    }

    /**
     * ÉTAPE 1 : Affecter les Jurys aux étudiants (sans date ni heure).
     * Cela prépare les données pour la planification temporelle.
     */
    @PostMapping("/affecter-jurys")
    public String affecterJurys(RedirectAttributes redirectAttrs) {
        try {
            String resultat = affectationService.genererAffectations();
            redirectAttrs.addFlashAttribute("successPlanning", "Étape 1/2 : " + resultat);
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("erreurPlanning", "Erreur lors de l'affectation des jurys : " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/planning";
    }

    /**
     * ÉTAPE 2 : Générer le Planning Temporel (Dates, Heures, Salles).
     * Utilise les jurys déjà affectés ou les affecte à la volée si nécessaire.
     */
    @PostMapping("/generer-temporel")
    public String genererPlanningTemporel(
            @RequestParam(value = "jours", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            List<LocalDate> jours,
            RedirectAttributes redirectAttrs) {

        if (jours == null || jours.isEmpty()) {
            redirectAttrs.addFlashAttribute("erreurPlanning", "Veuillez sélectionner au moins un jour de soutenance.");
            return "redirect:/planning";
        }

        try {
            // On appelle le service qui crée la VersionPlanning et place les soutenances dans le temps
            String resultat = planificationService.genererPlanningComplet(jours);
            redirectAttrs.addFlashAttribute("successPlanning", "Étape 2/2 : " + resultat);
        } catch (PlanificationService.PlanificationException e) {
            redirectAttrs.addFlashAttribute("erreurPlanning", "Erreur de planification : " + e.getMessage());
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("erreurPlanning", "Erreur inattendue : " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/planning";
    }

    /**
     * Voir une version spécifique de l'historique.
     */
    @GetMapping("/version/{id}")
    public String voirVersion(@PathVariable Long id, Model model) {
        VersionPlanning version = versionPlanningRepository.findById(id).orElse(null);
        if (version != null) {
            model.addAttribute("soutenances", soutenanceRepository.findByVersion(version));
            model.addAttribute("versionActuelle", version);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
        }
        model.addAttribute("activePage", "planning");
        return "planning/tableau";
    }
}