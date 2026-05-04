package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SoutenanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/export")
public class ExportPageController {

    @Autowired private SoutenanceRepository soutenanceRepository;

    @GetMapping
    public String page(Model model) {
        long nbSoutenances = soutenanceRepository.count();
        model.addAttribute("nbSoutenances", nbSoutenances);
        model.addAttribute("planningGenere", nbSoutenances > 0);

        // Versions sauvegardées
        String dossierBase = System.getProperty("user.home") + "/pfe-uploads";
        java.io.File base = new java.io.File(dossierBase);
        int nbVersions = 0;
        if (base.exists()) {
            java.io.File[] dossiers = base.listFiles(
                f -> f.isDirectory() && f.getName().startsWith("version_"));
            if (dossiers != null) nbVersions = dossiers.length;
        }
        model.addAttribute("nbVersions", nbVersions);
        model.addAttribute("dossierBase", dossierBase);
        return "export/page";
    }
}
