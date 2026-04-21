package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SoutenanceRepository;
import ma.ensa.pfe.model.Soutenance;
import ma.ensa.pfe.service.ExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Contrôleur Spring MVC gérant les exports du planning des soutenances PFE.
 * <p>
 * Expose trois endpoints GET :
 * <ul>
 *   <li>{@code /export/excel} — téléchargement du planning en .xlsx</li>
 *   <li>{@code /export/pdf}   — téléchargement du planning en .pdf</li>
 *   <li>{@code /export/pvs}   — téléchargement du dossier PVs en .zip</li>
 * </ul>
 *
 * @author Membre B — ENSA Al Hoceima 2024/2025
 * @version 1.0
 */
@Controller
@RequestMapping("/export")
public class ExportController {

    @Autowired private ExportService exportService;
    @Autowired private SoutenanceRepository soutenanceRepository;

    @GetMapping("/excel")
    public void exportExcel(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=planning_soutenances.xlsx");
        exportService.exporterExcel(soutenances, response.getOutputStream());
    }

    @GetMapping("/pdf")
    public void exportPdf(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=planning_soutenances.pdf");
        exportService.exporterPdf(soutenances, response.getOutputStream());
    }

    @GetMapping("/pvs")
    public void exportPvs(HttpServletResponse response) throws IOException {
        List<Soutenance> soutenances = soutenanceRepository.findAll();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=PVs.zip");
        exportService.genererPvsZip(soutenances, response.getOutputStream());
    }
}