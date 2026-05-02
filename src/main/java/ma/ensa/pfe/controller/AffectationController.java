package ma.ensa.pfe.controller;

import ma.ensa.pfe.service.AffectationService;
import ma.ensa.pfe.service.AffectationService.AffectationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Module 1 du schéma synoptique : AFFECTATION DES ENCADRANTS
 * URL de base : /affectation
 */
@Controller
@RequestMapping("/affectation")
public class AffectationController {

    @Autowired private AffectationService affectationService;

    /**
     * Page d'affectation : affiche la charge actuelle des encadrants
     * et le nombre d'étudiants sans encadrant.
     */
    @GetMapping
    public String afficherPage(Model model) {
        model.addAttribute("chargeEncadrants",       affectationService.getChargeEncadrants());
        model.addAttribute("nbSansEncadrant",        affectationService.getNbEtudiantsSansEncadrant());
        model.addAttribute("activePage", "affectation");
        return "affectation/index";
    }

    /**
     * Lance l'affectation équitable des encadrants.
     * Redirige vers la même page avec un message de succès ou d'erreur.
     */
    @PostMapping("/affecter")
    public String affecterEncadrants(RedirectAttributes redirectAttrs) {
        try {
            AffectationResult result = affectationService.affecterEncadrants();

            if (result.hasErreurs()) {
                redirectAttrs.addFlashAttribute("erreurs",  result.getErreurs());
            }
            redirectAttrs.addFlashAttribute("nbAffectes", result.getNbAffectes());
            redirectAttrs.addFlashAttribute("nbEchecs",   result.getNbEchecs());
            redirectAttrs.addFlashAttribute("details",    result.getDetails());
            redirectAttrs.addFlashAttribute("success",
                    result.getNbAffectes() + " étudiant(s) affecté(s) avec succès.");

        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("erreur",
                    "Erreur lors de l'affectation : " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/affectation";
    }
}