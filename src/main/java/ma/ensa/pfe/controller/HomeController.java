package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.*;
import ma.ensa.pfe.service.StatistiquesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private SalleRepository salleRepository;
    @Autowired private SoutenanceRepository soutenanceRepository;
    @Autowired private StatistiquesService statistiquesService;

    @GetMapping("/")
    public String home(Model model) {
        long nbEtudiants  = etudiantRepository.count();
        long nbSoutenances = soutenanceRepository.count();

        model.addAttribute("nbEtudiants",        nbEtudiants);
        model.addAttribute("nbProfs",            professeurRepository.count());
        model.addAttribute("nbSalles",           salleRepository.count());
        model.addAttribute("nbSoutenances",      nbSoutenances);
        model.addAttribute("nbJours",            statistiquesService.getNbJours());
        model.addAttribute("donneesImportees",   nbEtudiants > 0);
        model.addAttribute("planningGenere",     nbSoutenances > 0);
        model.addAttribute("chargeParProf",      statistiquesService.getChargeParProfesseur());
        model.addAttribute("repartitionFiliere", statistiquesService.getRepartitionFiliere());
        model.addAttribute("repartitionLangue",  statistiquesService.getRepartitionLangue());
        model.addAttribute("soutenancesParJour", statistiquesService.getSoutenancesParJour());
        model.addAttribute("chargeMax",          statistiquesService.getChargeMax());
        return "index";
    }

    
    @GetMapping("/go/affectation")
    public String goAffectation() {
        if (etudiantRepository.count() == 0) return "redirect:/import";
        return "redirect:/affectation";
    }

    @GetMapping("/go/planning")
    public String goPlanning() {
        if (etudiantRepository.count() == 0) return "redirect:/import";
        return "redirect:/planning";
    }
}
