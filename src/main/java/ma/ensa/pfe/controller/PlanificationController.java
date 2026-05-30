package ma.ensa.pfe.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;

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


@Controller
@RequestMapping("/planning")
public class PlanificationController {

    @Autowired private PlanificationService      planificationService;
    @Autowired private SoutenanceRepository      soutenanceRepository;
    @Autowired private VersionPlanningRepository versionPlanningRepository;
    @Autowired private EtudiantRepository        etudiantRepository;

    
    @GetMapping
    public String afficherPlanning(Model model, HttpSession session) {
        VersionPlanning derniereVersion =
                versionPlanningRepository.findFirstByOrderByDateGenerationDesc();

        if (derniereVersion != null) {
            List<Soutenance> soutenances =
                    soutenanceRepository.findByVersion(derniereVersion);
            soutenances.sort(Comparator
                    .comparing(Soutenance::getDate)
                    .thenComparing(Soutenance::getHeure));

            model.addAttribute("soutenances",    soutenances);
            model.addAttribute("versionActuelle", derniereVersion);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
            model.addAttribute("messageInfo",
                "Aucun planning généré.");
        }

        
        long nbSansEncadrant = etudiantRepository.countByEncadrantIsNull();
        long nbEtudiants = etudiantRepository.count();
        boolean affectationFaite = nbEtudiants > 0 && nbSansEncadrant == 0;
        model.addAttribute("affectationFaite", affectationFaite);

        
        String derniersJours = (String) session.getAttribute("derniersJours");
        model.addAttribute("derniersJours", derniersJours);

        
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

   
    @PostMapping("/generer")
    public String genererPlanning(
            @RequestParam(value = "jours", required = false) String joursStr,
            HttpSession session,
            RedirectAttributes redirectAttrs) {

        
        long nbEtudiants = etudiantRepository.count();
        if (nbEtudiants == 0) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Aucun étudiant en base. <a href='/' style='color:#fbbf24;text-decoration:underline;font-weight:600;'>Importez</a> d'abord le fichier Excel.");
            return "redirect:/#importSection"; 
        }

        
        long nbSansEncadrant = etudiantRepository.countByEncadrantIsNull();
        if (nbSansEncadrant > 0) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    nbSansEncadrant + " étudiant(s) sans encadrant. Veuillez d'abord <a href='/affectation' style='color:#fbbf24;text-decoration:underline;font-weight:600;'>affecter les encadrants</a>.");
            return "redirect:/affectation"; 
        }

        
        if (joursStr == null || joursStr.isBlank()) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Veuillez sélectionner au moins un jour de soutenance.");
            return "redirect:/planning"; 
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
                return "redirect:/planning"; 
            }
            
            
            session.setAttribute("derniersJours", joursStr);

            
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

    
    @PostMapping("/regenerer")
    public String regenererPlanning(
            HttpSession session,
            RedirectAttributes redirectAttrs) {

        
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

   
    @GetMapping("/version/{id}")
    public String voirVersion(@PathVariable Long id, Model model,
                               HttpSession session) {  // ajouter session
        VersionPlanning version = versionPlanningRepository.findById(id).orElse(null);
        if (version != null) {
        	List<Soutenance> soutenances = soutenanceRepository.findByVersion(version);
            soutenances.sort(Comparator
                    .comparing(Soutenance::getDate)
                    .thenComparing(Soutenance::getHeure));
            model.addAttribute("soutenances", soutenances);
            model.addAttribute("versionActuelle", version);
        } else {
            model.addAttribute("soutenances", new ArrayList<>());
        }

    
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