package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/export")
public class ExportController {

    @Autowired private ExportService exportService;
    @Autowired private SoutenanceRepository soutenanceRepository;

    @GetMapping("/excel")
    public void exportExcel(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=planning_soutenances.xlsx");
        exportService.exporterExcel(soutenances, response.getOutputStream());
    }

    @GetMapping("/pdf")
    public void exportPdf(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=planning_soutenances.pdf");
        exportService.exporterPdf(soutenances, response.getOutputStream());
    }

    @GetMapping("/pvs")
    public void exportPvs(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=PVs.zip");
        exportService.genererPvsZip(soutenances, response.getOutputStream());
    }

    @GetMapping("/pvs/docx")
    public void exportPvsDocx(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=PVs_DOCX.zip");
        exportService.genererPvsDocxZip(soutenances, response.getOutputStream());
    }

    @GetMapping("/sauvegarder")
    public String sauvegarderVersion(RedirectAttributes ra) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
        String dossierBase = System.getProperty("user.home") + "/pfe-uploads";
        exportService.sauvegarderVersion(soutenances, dossierBase);
        ra.addFlashAttribute("succes", "Version sauvegardée dans " + dossierBase);
        return "redirect:/export";
    }

    @GetMapping("/versions")
    public String versions(Model model) {
        String dossierBase = System.getProperty("user.home") + "/pfe-uploads";
        java.io.File base = new java.io.File(dossierBase);
        java.util.List<Map<String, String>> versions = new java.util.ArrayList<>();

        if (base.exists()) {
            java.io.File[] dossiers = base.listFiles(
                f -> f.isDirectory() && f.getName().startsWith("version_"));
            if (dossiers != null) {
                Arrays.sort(dossiers, Comparator.comparingLong(
                    java.io.File::lastModified).reversed());
                for (java.io.File d : dossiers) {
                    Map<String, String> v = new java.util.HashMap<>();
                    v.put("nom", d.getName());
                    v.put("date", java.time.Instant.ofEpochMilli(d.lastModified())
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    long size = Arrays.stream(
                        d.listFiles() != null ? d.listFiles() : new java.io.File[0])
                        .mapToLong(java.io.File::length).sum();
                    v.put("taille", (size / 1024) + " Ko");
                    v.put("fichiers", String.valueOf(
                        d.listFiles() != null ? d.listFiles().length : 0));
                    versions.add(v);
                }
            }
        }
        model.addAttribute("versions", versions);
        model.addAttribute("dossierBase", dossierBase);
        return "export/versions";
    }
}
