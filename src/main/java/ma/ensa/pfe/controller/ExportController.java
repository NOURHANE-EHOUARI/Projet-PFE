package ma.ensa.pfe.controller;

import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public String page(Model model) {
        long nbSoutenances = soutenanceRepository.count();
        model.addAttribute("nbSoutenances", nbSoutenances);
        model.addAttribute("planningGenere", nbSoutenances > 0);
        String dossierBase = System.getProperty("user.home") + "/pfe-uploads";
        java.io.File base = new java.io.File(dossierBase);
        int nbVersions = 0;
        if (base.exists()) {
            java.io.File[] dossiers = base.listFiles(f -> f.isDirectory() && f.getName().startsWith("version_"));
            if (dossiers != null) nbVersions = dossiers.length;
        }
        model.addAttribute("nbVersions", nbVersions);
        model.addAttribute("dossierBase", dossierBase);
        return "export/page";
    }

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
    @GetMapping("/versions/pdf")
    public void voirVersionPdf(@RequestParam String nom, HttpServletResponse response) throws IOException {
      String dossierBase = System.getProperty("user.home") + "/pfe-uploads";
      java.io.File pdf = new java.io.File(dossierBase + "/" + nom + "/planning.pdf");
      if (!pdf.exists()) { response.sendError(404, "PDF introuvable"); return; }
      response.setContentType("application/pdf");
      response.setHeader("Content-Disposition", "inline; filename=" + nom + "_planning.pdf");
      java.nio.file.Files.copy(pdf.toPath(), response.getOutputStream());
    }

    @GetMapping("/versions/zip")
    public void telechargerVersionZip(@RequestParam String nom, HttpServletResponse response) throws IOException {
      String dossierBase = System.getProperty("user.home") + "/pfe-uploads";
      java.io.File dossier = new java.io.File(dossierBase + "/" + nom);
      if (!dossier.exists()) { response.sendError(404, "Version introuvable"); return; }
      response.setContentType("application/zip");
      response.setHeader("Content-Disposition", "attachment; filename=" + nom + ".zip");
      try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
        for (java.io.File f : dossier.listFiles()) {
            zipOut.putNextEntry(new java.util.zip.ZipEntry(f.getName()));
            java.nio.file.Files.copy(f.toPath(), zipOut);
            zipOut.closeEntry();
        }
    }
    }  
    @GetMapping("/calendrier/pdf")
    public void exportCalendrierPdf(HttpServletResponse response) throws IOException {
     List<Soutenance> soutenances = soutenanceRepository.findAllWithDetails();
     response.setContentType("application/pdf");
     response.setHeader("Content-Disposition", "attachment; filename=calendrier_soutenances.pdf");

     com.itextpdf.text.Document doc = new com.itextpdf.text.Document(
        com.itextpdf.text.PageSize.A4, 36, 36, 50, 40);
     try {
        com.itextpdf.text.pdf.PdfWriter.getInstance(doc, response.getOutputStream());
        doc.open();

        com.itextpdf.text.Font titleFont = com.itextpdf.text.FontFactory.getFont(
            com.itextpdf.text.FontFactory.HELVETICA_BOLD, 16);
        com.itextpdf.text.Font subFont = com.itextpdf.text.FontFactory.getFont(
            com.itextpdf.text.FontFactory.HELVETICA, 10);
        com.itextpdf.text.Font dayFont = com.itextpdf.text.FontFactory.getFont(
            com.itextpdf.text.FontFactory.HELVETICA_BOLD, 11,
            new com.itextpdf.text.BaseColor(99, 102, 241));
        com.itextpdf.text.Font cellFont = com.itextpdf.text.FontFactory.getFont(
            com.itextpdf.text.FontFactory.HELVETICA, 9);
        com.itextpdf.text.Font hFont = com.itextpdf.text.FontFactory.getFont(
            com.itextpdf.text.FontFactory.HELVETICA_BOLD, 8);

        com.itextpdf.text.Paragraph titre = new com.itextpdf.text.Paragraph(
            "CALENDRIER DES SOUTENANCES PFE 2024/2025", titleFont);
        titre.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
        titre.setSpacingAfter(4);
        doc.add(titre);

        com.itextpdf.text.Paragraph sub = new com.itextpdf.text.Paragraph(
            "ENSA Al Hoceima — Département Mathématiques & Informatique", subFont);
        sub.setAlignment(com.itextpdf.text.Element.ALIGN_CENTER);
        sub.setSpacingAfter(20);
        doc.add(sub);

        soutenances.sort(Comparator.comparing(Soutenance::getDate)
                                   .thenComparing(Soutenance::getHeure));
        java.util.Map<java.time.LocalDate, List<Soutenance>> parDate = soutenances.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Soutenance::getDate, java.util.TreeMap::new,
                java.util.stream.Collectors.toList()));

        java.time.format.DateTimeFormatter dateFmt =
            java.time.format.DateTimeFormatter.ofPattern("EEEE dd MMMM yyyy",
                new java.util.Locale("fr"));
        java.time.format.DateTimeFormatter heureFmt =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        for (java.util.Map.Entry<java.time.LocalDate, List<Soutenance>> entry : parDate.entrySet()) {
            com.itextpdf.text.Paragraph jour = new com.itextpdf.text.Paragraph(
                entry.getKey().format(dateFmt).toUpperCase() +
                "  (" + entry.getValue().size() + " soutenances)", dayFont);
            jour.setSpacingBefore(14);
            jour.setSpacingAfter(6);
            doc.add(jour);

            com.itextpdf.text.pdf.PdfPTable table =
                new com.itextpdf.text.pdf.PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.8f, 1f, 2.2f, 2f, 2f});
            table.setSpacingAfter(6);

            com.itextpdf.text.BaseColor headerBg = new com.itextpdf.text.BaseColor(230, 232, 255);
            com.itextpdf.text.BaseColor rowBg1   = com.itextpdf.text.BaseColor.WHITE;
            com.itextpdf.text.BaseColor rowBg2   = new com.itextpdf.text.BaseColor(245, 246, 255);

            for (String h : new String[]{"Heure","Salle","Etudiant","Encadrant","Jury"}) {
                com.itextpdf.text.pdf.PdfPCell c =
                    new com.itextpdf.text.pdf.PdfPCell(
                        new com.itextpdf.text.Phrase(h, hFont));
                c.setBackgroundColor(headerBg);
                c.setPadding(5);
                table.addCell(c);
            }

            boolean alt = false;
            for (Soutenance s : entry.getValue()) {
                com.itextpdf.text.BaseColor bg = alt ? rowBg2 : rowBg1;
                alt = !alt;
                String jury = "";
                if (s.getJury2() != null) jury += s.getJury2().getNom();
                if (s.getJury3() != null) jury += " / " + s.getJury3().getNom();
                for (String val : new String[]{
                    s.getHeure().format(heureFmt),
                    s.getSalle().getNom(),
                    s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom(),
                    s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom(),
                    jury
                }) {
                    com.itextpdf.text.pdf.PdfPCell c =
                        new com.itextpdf.text.pdf.PdfPCell(
                            new com.itextpdf.text.Phrase(val, cellFont));
                    c.setBackgroundColor(bg);
                    c.setPadding(5);
                    table.addCell(c);
                }
            }
            doc.add(table);
        }
        doc.close();
     } catch (Exception e) {
        throw new RuntimeException("Erreur PDF calendrier : " + e.getMessage(), e);
     }
 }

}
