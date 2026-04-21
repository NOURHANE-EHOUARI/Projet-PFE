package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.PlanificationService;
import ma.ensa.pfe.service.PlanificationService.PlanificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrôleur gérant la génération, l'affichage tableau et la vue calendrier.
 * @author Membre A
 */
@Controller
@RequestMapping("/planning")
public class PlanificationController {

    @Autowired private PlanificationService planificationService;
    @Autowired private SoutenanceRepository soutenanceRepository;

    @GetMapping
    public String afficherPlanning(Model model) {
        model.addAttribute("soutenances", soutenanceRepository.findAll());
        model.addAttribute("activePage", "planning");
        return "planning/tableau";
    }

    @GetMapping("/calendrier")
    public String afficherCalendrier(Model model) {
        model.addAttribute("activePage", "planning");
        return "planning/calendrier";
    }

    @PostMapping("/generer")
    public String genererPlanning(
            @RequestParam(value = "jours", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            List<LocalDate> jours,
            RedirectAttributes redirectAttrs) {

        if (jours == null || jours.isEmpty()) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Veuillez sélectionner au moins un jour de soutenance.");
            return "redirect:/planning";
        }
        try {
            List<Soutenance> soutenances = planificationService.genererPlanning(jours);
            redirectAttrs.addFlashAttribute("successPlanning",
                    soutenances.size() + " soutenances planifiées avec succès !");
        } catch (PlanificationService.PlanificationException e) {
            redirectAttrs.addFlashAttribute("erreurPlanning", e.getMessage());
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("erreurPlanning",
                    "Erreur inattendue : " + e.getMessage());
        }
        return "redirect:/planning";
    }
}