package ma.ensa.pfe.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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
import ma.ensa.pfe.service.PlanificationService;

/**
 * Module 2 du schéma synoptique : PLANNING DES SOUTENANCES
 * URL de base : /planning
 *
 * Pré-requis : les encadrants doivent être affectés via /affectation
 */
@Controller
@RequestMapping("/planning")
public class PlanificationController {

    @Autowired private PlanificationService      planificationService;
    @Autowired private SoutenanceRepository      soutenanceRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;

    /**
     * Affiche la liste des soutenances de la dernière version générée.
     */
    @GetMapping
    public String afficherPlanning(Model model) {
        VersionPlanning derniereVersion =
                versionPlanningRepository.findFirstByOrderByDateGenerationDesc();

        if (derniereVersion != null) {
            List<Soutenance> soutenances =
                    soutenanceRepository.findByVersion(derniereVersion);
            model.addAttribute("soutenances",    soutenances);
            model.addAttribute("versionActuelle", derniereVersion);
        } else {
            model.addAttribute("soutenances",  new ArrayList<>());
            model.addAttribute("messageInfo",
                    "Aucun planning généré. Commencez par affecter les encadrants.");
        }

        model.addAttribute("activePage", "planning");
        return "planning/tableau";
    }

    /**
     * Affiche le calendrier (vue graphique).
     */
    @GetMapping("/calendrier")
    public String afficherCalendrier(Model model) {
        VersionPlanning derniereVersion =
                versionPlanningRepository.findFirstByOrderByDateGenerationDesc();
        if (derniereVersion != null) {
            model.addAttribute("soutenances",
                    soutenanceRepository.findByVersion(derniereVersion));
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
        }
        model.addAttribute("activePage", "planning");
        return "planning/calendrier";
    }

    /**
     * Génère le planning complet (jury + dates + heures + salles).
     * Pré-requis : tous les étudiants doivent avoir un encadrant.
     *
     * @param joursStr dates séparées par virgule ex: "2026-05-20,2026-05-21"
     */
    @PostMapping("/generer")
    public String genererPlanning(
            @RequestParam(value = "jours", required = false) String joursStr,
            RedirectAttributes redirectAttrs) {

        // Vérifier que les encadrants sont affectés
        // (le service lèvera une exception si ce n'est pas le cas)

        if (joursStr == null || joursStr.isBlank()) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Veuillez sélectionner au moins un jour de soutenance.");
            return "redirect:/planning";
        }

        try {
            List<LocalDate> jours = Arrays.stream(joursStr.split(","))
                    .map(String::trim)
                    .map(LocalDate::parse)
                    .collect(Collectors.toList());

            if (jours.size() > 3) {
                redirectAttrs.addFlashAttribute("erreurPlanning",
                        "Maximum 3 jours de soutenance autorisés.");
                return "redirect:/planning";
            }

            String resultat = planificationService.genererPlanningComplet(jours);
            redirectAttrs.addFlashAttribute("successPlanning",
                    "Planning généré avec succès : " + resultat);

        } catch (RuntimeException e) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Erreur de planification : " + e.getMessage());
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Erreur inattendue : " + e.getMessage());
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
            model.addAttribute("soutenances",     soutenanceRepository.findByVersion(version));
            model.addAttribute("versionActuelle", version);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
        }
        model.addAttribute("activePage", "planning");
        return "planning/tableau";
    }
}