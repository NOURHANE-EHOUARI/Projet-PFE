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

import java.util.Map;

@Controller
@RequestMapping("/affectation")
public class AffectationController {

    @Autowired private AffectationService affectationService;

    @GetMapping
    public String afficherPage(Model model) {
        Map<String, Long> charge = affectationService.getChargeEncadrants();
        model.addAttribute("chargeEncadrants", charge);
        model.addAttribute("nbSansEncadrant",  affectationService.getNbEtudiantsSansEncadrant());
        model.addAttribute("activePage", "affectation");
        long maxCharge = 1L;
        if (charge != null && !charge.isEmpty()) {
            maxCharge = charge.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(1L);
        }
        model.addAttribute("maxCharge", maxCharge);

        return "affectation/index";
    }

    @PostMapping("/affecter")
    public String affecterEncadrants(RedirectAttributes redirectAttrs) {
        try {
            AffectationResult result = affectationService.affecterEncadrants();
            if (result.hasErreurs()) {
                redirectAttrs.addFlashAttribute("erreurs", result.getErreurs());
            }
            redirectAttrs.addFlashAttribute("nbAffectes", result.getNbAffectes());
            redirectAttrs.addFlashAttribute("nbEchecs",   result.getNbEchecs());
            redirectAttrs.addFlashAttribute("details",    result.getDetails());
            redirectAttrs.addFlashAttribute("success",
                result.getNbAffectes() + " etudiant(s) affecte(s) avec succes.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("erreur",
                "Erreur lors de l'affectation : " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/affectation";
    }
}