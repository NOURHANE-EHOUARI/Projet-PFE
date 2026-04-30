package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.*;
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

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("nbEtudiants", etudiantRepository.count());
        model.addAttribute("nbProfs", professeurRepository.count());
        model.addAttribute("nbSalles", salleRepository.count());
        model.addAttribute("nbSoutenances", soutenanceRepository.count());
        return "index";
    }
}
