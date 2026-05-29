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
import jakarta.servlet.http.HttpSession;
import java.time.format.DateTimeFormatter;
import ma.ensa.pfe.dao.EtudiantRepository;
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
    @Autowired private EtudiantRepository        etudiantRepository;

    /**
     * Affiche la liste des soutenances de la dernière version générée.
     */
    @GetMapping
    public String afficherPlanning(Model model, HttpSession session) {
        VersionPlanning derniereVersion =
                versionPlanningRepository.findFirstByOrderByDateGenerationDesc();

        if (derniereVersion != null) {
            List<Soutenance> soutenances =
                    soutenanceRepository.findByVersion(derniereVersion);
            model.addAttribute("soutenances",    soutenances);
            model.addAttribute("versionActuelle", derniereVersion);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
            model.addAttribute("messageInfo",
                "Aucun planning généré.");
        }

        // ✅ Vérifier si l'affectation a été faite
        long nbSansEncadrant = etudiantRepository.countByEncadrantIsNull();
        long nbEtudiants = etudiantRepository.count();
        boolean affectationFaite = nbEtudiants > 0 && nbSansEncadrant == 0;
        model.addAttribute("affectationFaite", affectationFaite);

        // ✅ Récupérer les dates mémorisées depuis la session
        String derniersJours = (String) session.getAttribute("derniersJours");
        model.addAttribute("derniersJours", derniersJours);

        // ✅ Formater les dates pour affichage lisible
        if (derniersJours != null && !derniersJours.isBlank()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String joursFormates = Arrays.stream(derniersJours.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> LocalDate.parse(s).format(fmt))
                    .collect(Collectors.joining(" · "));
            model.addAttribute("joursFormatesAffichage", joursFormates);
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
     * Pré-requis : 
     * 1. Des étudiants doivent exister en base → sinon redirect /import
     * 2. Tous les étudiants doivent avoir un encadrant → sinon redirect /affectation
     *
     * @param joursStr dates séparées par virgule ex: "2026-05-20,2026-05-21"
     */
    @PostMapping("/generer")
    public String genererPlanning(
            @RequestParam(value = "jours", required = false) String joursStr,
            HttpSession session,
            RedirectAttributes redirectAttrs) {

        //  VALIDATION 1 : Vérifier si des étudiants existent en base
        long nbEtudiants = etudiantRepository.count();
        if (nbEtudiants == 0) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Aucun étudiant en base. <a href='/import' style='color:#fbbf24;text-decoration:underline;font-weight:600;'>Importez</a> d'abord le fichier Excel.");
            return "redirect:/import";
        }

        //  VALIDATION 2 : Vérifier si tous les étudiants ont un encadrant
        long nbSansEncadrant = etudiantRepository.countByEncadrantIsNull();
        if (nbSansEncadrant > 0) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    nbSansEncadrant + " étudiant(s) sans encadrant. Veuillez d'abord <a href='/affectation' style='color:#fbbf24;text-decoration:underline;font-weight:600;'>affecter les encadrants</a>.");
            return "redirect:/affectation";
        }

        //  VALIDATION 3 : Vérifier que des dates sont sélectionnées
        if (joursStr == null || joursStr.isBlank()) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Veuillez sélectionner au moins un jour de soutenance.");
            return "redirect:/affectation";
        }

        try {
            List<LocalDate> jours = Arrays.stream(joursStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(LocalDate::parse)
                    .collect(Collectors.toList());

            if (jours.size() > 3) {
                redirectAttrs.addFlashAttribute("erreurPlanning",
                        "Maximum 3 jours de soutenance autorisés.");
                return "redirect:/affectation";
            }
         // Sauvegarder les dates en session pour la régénération
            session.setAttribute("derniersJours", joursStr);

            // TOUT EST BON → Générer le planning
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
     * AJOUT : Régénère le planning avec les mêmes dates que la dernière version.
     * Appelé depuis le bouton "Régénérer (mêmes dates)" dans la modale.
     */
    @PostMapping("/regenerer")
    public String regenererPlanning(
            HttpSession session,
            RedirectAttributes redirectAttrs) {

        //  Garder tes validations existantes
        long nbEtudiants = etudiantRepository.count();
        if (nbEtudiants == 0) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Aucun étudiant en base.");
            return "redirect:/import";
        }

        long nbSansEncadrant = etudiantRepository.countByEncadrantIsNull();
        if (nbSansEncadrant > 0) {
            redirectAttrs.addFlashAttribute("erreurAffectation",
                    nbSansEncadrant + " étudiant(s) sans encadrant.");
            return "redirect:/affectation";
        }

        //  Récupérer les dates depuis la SESSION (pas la DB)
        String joursStr = (String) session.getAttribute("derniersJours");

        if (joursStr == null || joursStr.isBlank()) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Aucune date mémorisée. Veuillez relancer depuis la page Affectation.");
            return "redirect:/affectation";
        }

        try {
            List<LocalDate> jours = Arrays.stream(joursStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(LocalDate::parse)
                    .sorted()
                    .collect(Collectors.toList());

            String resultat = planificationService.genererPlanningComplet(jours);
            redirectAttrs.addFlashAttribute("successPlanning",
                    "Planning régénéré avec succès : " + resultat);

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
    public String voirVersion(@PathVariable Long id, Model model,
                               HttpSession session) {  // ajouter session
        VersionPlanning version = versionPlanningRepository.findById(id).orElse(null);
        if (version != null) {
            model.addAttribute("soutenances", soutenanceRepository.findByVersion(version));
            model.addAttribute("versionActuelle", version);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
        }

        //  Même logique que afficherPlanning()
        String derniersJours = (String) session.getAttribute("derniersJours");
        model.addAttribute("derniersJours", derniersJours);
        if (derniersJours != null && !derniersJours.isBlank()) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String joursFormates = Arrays.stream(derniersJours.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> LocalDate.parse(s).format(fmt))
                    .collect(Collectors.joining(" · "));
            model.addAttribute("joursFormatesAffichage", joursFormates);
        }

        long nbSansEncadrant = etudiantRepository.countByEncadrantIsNull();
        long nbEtudiants = etudiantRepository.count();
        model.addAttribute("affectationFaite", nbEtudiants > 0 && nbSansEncadrant == 0);

        model.addAttribute("activePage", "planning");
        return "planning/tableau";
    }
}