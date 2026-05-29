package ma.ensa.pfe.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PdfContentByte;
import ma.ensa.pfe.model.Soutenance;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service responsable de la génération des exports du planning des soutenances PFE.
 * <p>
 * Ce service fournit trois fonctionnalités principales :
 * <ul>
 *   <li>Export du planning en fichier Excel (.xlsx) avec 3 feuilles (global, par salle, par prof)</li>
 *   <li>Export du planning en fichier PDF formaté</li>
 *   <li>Génération automatique du dossier PVs (un PDF par étudiant, groupés par encadrant) en archive ZIP</li>
 * </ul>
 *
 * @author Membre B — ENSA Al Hoceima 2024/2025
 * @version 1.0
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ══════════════════════════════════════════════
    //  EXPORT EXCEL
    // ══════════════════════════════════════════════
    /**
     * Génère un fichier Excel du planning des soutenances avec 3 feuilles.
     * <ul>
     *   <li>Feuille 1 : Planning global trié par date et heure</li>
     *   <li>Feuille 2 : Planning groupé par salle</li>
     *   <li>Feuille 3 : Planning groupé par professeur encadrant</li>
     * </ul>
     *
     * @param soutenances liste des soutenances à exporter
     * @param out         flux de sortie vers lequel écrire le fichier Excel
     * @throws IOException en cas d'erreur d'écriture
     */
    public void exporterExcel(List<Soutenance> soutenances, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            XSSFFont headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            XSSFCellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Feuille 1 - Planning global
            XSSFSheet sheet1 = workbook.createSheet("Planning Global");
            String[] headers = {"#","Date","Heure","Salle","Etudiant","Filiere","Langue","Encadrant","Jury 1","Jury 2"};
            creerEntete(sheet1, headers, headerStyle, 0);

            soutenances.sort(Comparator.comparing(Soutenance::getDate).thenComparing(Soutenance::getHeure));
            int rowNum = 1;
            for (Soutenance s : soutenances) {
                Row row = sheet1.createRow(rowNum++);
                creerCellule(row, 0, String.valueOf(rowNum - 1), dataStyle);
                creerCellule(row, 1, s.getDate().format(DATE_FMT), dataStyle);
                creerCellule(row, 2, s.getHeure().format(HEURE_FMT), dataStyle);
                creerCellule(row, 3, s.getSalle().getNom(), dataStyle);
                creerCellule(row, 4, s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom(), dataStyle);
                creerCellule(row, 5, s.getEtudiant().getFiliere().toString(), dataStyle);
                creerCellule(row, 6, s.getEtudiant().getLangue().toString(), dataStyle);
                creerCellule(row, 7, s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom(), dataStyle);
                creerCellule(row, 8, s.getJury2() != null ? s.getJury2().getNom() + " " + s.getJury2().getPrenom() : "", dataStyle);
                creerCellule(row, 9, s.getJury3() != null ? s.getJury3().getNom() + " " + s.getJury3().getPrenom() : "", dataStyle);
            }
            for (int i = 0; i < headers.length; i++) sheet1.autoSizeColumn(i);

            // Feuille 2 - Par salle
            XSSFSheet sheet2 = workbook.createSheet("Par Salle");
            Map<String, List<Soutenance>> parSalle = soutenances.stream()
                .collect(Collectors.groupingBy(s -> s.getSalle().getNom(), TreeMap::new, Collectors.toList()));
            int row2 = 0;
            for (Map.Entry<String, List<Soutenance>> entry : parSalle.entrySet()) {
                sheet2.createRow(row2++).createCell(0).setCellValue("Salle : " + entry.getKey());
                creerEntete(sheet2, new String[]{"Date","Heure","Etudiant","Encadrant"}, headerStyle, row2++);
                for (Soutenance s : entry.getValue()) {
                    Row r = sheet2.createRow(row2++);
                    creerCellule(r, 0, s.getDate().format(DATE_FMT), dataStyle);
                    creerCellule(r, 1, s.getHeure().format(HEURE_FMT), dataStyle);
                    creerCellule(r, 2, s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom(), dataStyle);
                    creerCellule(r, 3, s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom(), dataStyle);
                }
                row2++;
            }

            // Feuille 3 - Par prof
            XSSFSheet sheet3 = workbook.createSheet("Par Professeur");
            Map<String, List<Soutenance>> parProf = soutenances.stream()
                .collect(Collectors.groupingBy(
                    s -> s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom(),
                    TreeMap::new, Collectors.toList()));
            int row3 = 0;
            for (Map.Entry<String, List<Soutenance>> entry : parProf.entrySet()) {
                sheet3.createRow(row3++).createCell(0).setCellValue("Encadrant : " + entry.getKey());
                creerEntete(sheet3, new String[]{"Date","Heure","Salle","Etudiant"}, headerStyle, row3++);
                for (Soutenance s : entry.getValue()) {
                    Row r = sheet3.createRow(row3++);
                    creerCellule(r, 0, s.getDate().format(DATE_FMT), dataStyle);
                    creerCellule(r, 1, s.getHeure().format(HEURE_FMT), dataStyle);
                    creerCellule(r, 2, s.getSalle().getNom(), dataStyle);
                    creerCellule(r, 3, s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom(), dataStyle);
                }
                row3++;
            }
            workbook.write(out);
        }
    }

    // ══════════════════════════════════════════════
    //  EXPORT PDF
    // ══════════════════════════════════════════════
    /**
     * Génère un fichier PDF du planning des soutenances.
     * Le tableau est formaté avec alternance de couleurs et entête ENSA.
     *
     * @param soutenances liste des soutenances à exporter
     * @param out         flux de sortie vers lequel écrire le PDF
     */
    public void exporterPdf(List<Soutenance> soutenances, OutputStream out) {
        Document doc = new Document(PageSize.A4.rotate(), 25, 25, 45, 35);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // ── COULEURS INSTITUTIONNELLES ──
            BaseColor bleuMarine  = new BaseColor(26,  58,  92);   // #1a3a5c
            BaseColor bleuSecond  = new BaseColor(46, 109, 164);   // #2e6da4
            BaseColor or          = new BaseColor(240, 165,   0);   // #f0a500
            BaseColor grisClair   = new BaseColor(245, 247, 250);
            BaseColor grisMoyen   = new BaseColor(220, 228, 236);
            BaseColor blanc       = BaseColor.WHITE;
            BaseColor textSombre  = new BaseColor(20,  20,  20);
            BaseColor textMuted   = new BaseColor(80,  80,  80);

            // ── POLICES ──
            com.itextpdf.text.Font fontTitre   = FontFactory.getFont(FontFactory.TIMES_BOLD,   14, bleuMarine);
            com.itextpdf.text.Font fontSousTitre = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, bleuSecond);
            com.itextpdf.text.Font fontHeader  = FontFactory.getFont(FontFactory.TIMES_BOLD,    8, blanc);
            com.itextpdf.text.Font fontData    = FontFactory.getFont(FontFactory.TIMES_ROMAN,   8, textSombre);
            com.itextpdf.text.Font fontMuted   = FontFactory.getFont(FontFactory.TIMES_ROMAN,   7, textMuted);
            com.itextpdf.text.Font fontFooter  = FontFactory.getFont(FontFactory.TIMES_ROMAN,   7, textMuted);

            // ── EN-TÊTE INSTITUTIONNEL ──
            // Ligne dorée en haut
            PdfPTable topLine = new PdfPTable(1);
            topLine.setWidthPercentage(100);
            topLine.setSpacingAfter(4);
            PdfPCell topCell = new PdfPCell();
            topCell.setBackgroundColor(or);
            topCell.setFixedHeight(4);
            topCell.setBorder(Rectangle.NO_BORDER);
            topLine.addCell(topCell);
            doc.add(topLine);

            // Bloc entête bleu
            PdfPTable headerBlock = new PdfPTable(1);
            headerBlock.setWidthPercentage(100);
            headerBlock.setSpacingAfter(6);
            PdfPCell headerCell = new PdfPCell();
            headerCell.setBackgroundColor(bleuMarine);
            headerCell.setPadding(12);
            headerCell.setBorder(Rectangle.NO_BORDER);

            com.itextpdf.text.Font fH1 = FontFactory.getFont(FontFactory.TIMES_BOLD,  13, blanc);
            com.itextpdf.text.Font fH2 = FontFactory.getFont(FontFactory.TIMES_ROMAN,  9, grisMoyen);
            com.itextpdf.text.Font fH3 = FontFactory.getFont(FontFactory.TIMES_BOLD,  11, or);

            Phrase ph1 = new Phrase("Ecole Nationale des Sciences Appliquées - Al Hoceima\n", fH2);
            Phrase ph2 = new Phrase("Département Mathématiques et Informatique\n", fH2);
            Phrase ph3 = new Phrase("Planning des Soutenances des Projets de Fin d'Étude", fH3);
            Phrase ph4 = new Phrase("\nAnnée Universitaire 2024/2025", fH2);

            com.itextpdf.text.Paragraph headerPara = new com.itextpdf.text.Paragraph();
            headerPara.setAlignment(Element.ALIGN_CENTER);
            headerPara.add(ph1);
            headerPara.add(ph2);
            headerPara.add(ph3);
            headerPara.add(ph4);
            headerCell.addElement(headerPara);
            headerBlock.addCell(headerCell);
            doc.add(headerBlock);

            // ── TABLEAU PLANNING ──
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.6f, 2.4f, 2.2f, 2.2f, 1.8f, 0.9f, 1.4f, 2.2f, 2.0f});
            table.setSpacingBefore(4);

            // En-têtes colonnes
            String[] cols = {"ID", "Encadrant", "Membre de jury 1", "Membre de jury 2",
                    "Date", "Heure", "Salle", "Nom d'étudiant", "Prénom d'étudiant"};
            for (String col : cols) {
                PdfPCell cell = new PdfPCell(new Phrase(col, fontHeader));
                cell.setBackgroundColor(bleuMarine);
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBorderColor(bleuSecond);
                cell.setBorderWidth(0.5f);
                table.addCell(cell);
            }

            // Lignes de données
            soutenances.sort(Comparator.comparing(Soutenance::getDate)
                                       .thenComparing(Soutenance::getHeure));
            

            // Grouper par date pour alterner les blocs visuellement
            String dernierDate = "";
            boolean altLigne = false;
            int idx = 1;

            for (Soutenance s : soutenances) {
                String dateStr = s.getDate().format(DATE_FMT);

                // Ligne séparatrice de date — bleu secondaire pleine largeur
                if (!dateStr.equals(dernierDate)) {
                    dernierDate = dateStr;
                    altLigne = false;

                    com.itextpdf.text.Font fontDate =
                        FontFactory.getFont(FontFactory.TIMES_BOLD, 8, blanc);
                    PdfPCell fullCell = new PdfPCell(new Phrase("  " + dateStr, fontDate));
                    fullCell.setBackgroundColor(bleuSecond);
                    fullCell.setPadding(5);
                    fullCell.setBorder(Rectangle.NO_BORDER);
                    fullCell.setColspan(9);  // ← 9 colonnes maintenant
                    table.addCell(fullCell);
                }

                altLigne = !altLigne;
                BaseColor bg = altLigne ? blanc : grisClair;

                // Heure format "9h", "10h" comme dans l'exemple
                String heureStr = s.getHeure().format(HEURE_FMT);
                // Convertir "09:00" → "9h", "14:00" → "14h"
                int heure = s.getHeure().getHour();
                int minute = s.getHeure().getMinute();
                String heureAffichage = (minute == 0)
                    ? heure + "h"
                    : heure + "h" + String.format("%02d", minute);

                ajouterCelluleStyle(table, String.valueOf(idx++),
                    fontMuted, bg, Element.ALIGN_CENTER);
                ajouterCelluleStyle(table,
                    s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom(),
                    fontData, bg, Element.ALIGN_LEFT);
                ajouterCelluleStyle(table,
                    s.getJury2() != null ? s.getJury2().getNom() + " " + s.getJury2().getPrenom() : "-",
                    fontData, bg, Element.ALIGN_LEFT);
                ajouterCelluleStyle(table,
                    s.getJury3() != null ? s.getJury3().getNom() + " " + s.getJury3().getPrenom() : "-",
                    fontData, bg, Element.ALIGN_LEFT);
                ajouterCelluleStyle(table, dateStr,
                    fontData, bg, Element.ALIGN_CENTER);
                ajouterCelluleStyle(table, heureAffichage,
                    fontData, bg, Element.ALIGN_CENTER);
                ajouterCelluleStyle(table,
                    s.getSalle() != null ? s.getSalle().getNom() : "-",
                    fontData, bg, Element.ALIGN_CENTER);
                ajouterCelluleStyle(table,
                    s.getEtudiant().getNom(),
                    fontData, bg, Element.ALIGN_LEFT);
                ajouterCelluleStyle(table,
                    s.getEtudiant().getPrenom(),
                    fontData, bg, Element.ALIGN_LEFT);
            }

               
            doc.add(table);

            // ── PIED DE PAGE ──
            Paragraph footer = new Paragraph(
                "Document généré automatiquement — PFE Planning ENSA Al Hoceima  ·  " +
                java.time.LocalDate.now().format(DATE_FMT) +
                "  ·  " + soutenances.size() + " soutenance(s)",
                fontFooter);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(10);
            doc.add(footer);

            // Ligne dorée en bas
            PdfPTable bottomLine = new PdfPTable(1);
            bottomLine.setWidthPercentage(100);
            bottomLine.setSpacingBefore(6);
            PdfPCell bottomCell = new PdfPCell();
            bottomCell.setBackgroundColor(or);
            bottomCell.setFixedHeight(3);
            bottomCell.setBorder(Rectangle.NO_BORDER);
            bottomLine.addCell(bottomCell);
            doc.add(bottomLine);

            doc.close();

        } catch (Exception e) {
            throw new RuntimeException("Erreur PDF : " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════
    //  GENERATION PVs + ZIP
    // ══════════════════════════════════════════════
    /**
     * Génère une archive ZIP contenant les PVs de soutenance.
     * <p>
     * Structure du ZIP :
     * <pre>
     * PVs/
     *   NomProf_PrenomProf/
     *     PV_NomEtudiant_PrenomEtudiant.pdf
     *     ...
     * </pre>
     * Chaque étudiant possède un et un seul PV.
     *
     * @param soutenances liste des soutenances
     * @param out         flux de sortie vers lequel écrire le ZIP
     * @throws IOException en cas d'erreur d'écriture
     */
    public void genererPvsZip(List<Soutenance> soutenances, OutputStream out) throws IOException {
        Map<String, List<Soutenance>> parEncadrant = soutenances.stream()
            .collect(Collectors.groupingBy(
                s -> s.getEncadrant().getNom() + "_" + s.getEncadrant().getPrenom(),
                TreeMap::new, Collectors.toList()));

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, List<Soutenance>> entry : parEncadrant.entrySet()) {
                String dossier = "PVs/" + entry.getKey() + "/";
                for (Soutenance s : entry.getValue()) {
                    String fichier = dossier + "PV_" + s.getEtudiant().getNom() + "_" + s.getEtudiant().getPrenom() + ".pdf";
                    zip.putNextEntry(new ZipEntry(fichier));
                    genererUnPv(s, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void genererUnPv(Soutenance s, OutputStream out) {
      Document doc = new Document(PageSize.A4, 45, 45, 40, 35);
      try {
        PdfWriter.getInstance(doc, out).setCloseStream(false);
        doc.open();

        com.itextpdf.text.Font fontTitre    = FontFactory.getFont(FontFactory.TIMES_BOLD, 13);
        com.itextpdf.text.Font fontNormal   = FontFactory.getFont(FontFactory.TIMES_ROMAN, 11);
        com.itextpdf.text.Font fontBold     = FontFactory.getFont(FontFactory.TIMES_BOLD, 11);
        com.itextpdf.text.Font fontSmall    = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10);
        com.itextpdf.text.Font fontSmallB   = FontFactory.getFont(FontFactory.TIMES_BOLD, 10);

        // ── ENTÊTE UNIVERSITÉ ──
        Paragraph univ = new Paragraph("UNIVERSITE ABDELMALEK ESSAADI", fontBold);
        univ.setAlignment(Element.ALIGN_CENTER);
        doc.add(univ);

        Paragraph ecole = new Paragraph(
            "Ecole Nationale des Sciences Appliquées d'Al-Hoceima - Maroc", fontNormal);
        ecole.setAlignment(Element.ALIGN_CENTER);
        doc.add(ecole);

        Paragraph dept = new Paragraph(
            "Département de Mathématiques et Informatique", fontNormal);
        dept.setAlignment(Element.ALIGN_CENTER);
        dept.setSpacingAfter(8);
        doc.add(dept);

        // Ligne séparatrice
        PdfPTable ligne = new PdfPTable(1);
        ligne.setWidthPercentage(100);
        ligne.setSpacingAfter(6);
        PdfPCell ligneCell = new PdfPCell();
        ligneCell.setBorderWidthBottom(1.5f);
        ligneCell.setBorderWidthTop(0);
        ligneCell.setBorderWidthLeft(0);
        ligneCell.setBorderWidthRight(0);
        ligneCell.setFixedHeight(2);
        ligne.addCell(ligneCell);
        doc.add(ligne);

        // ── TITRE FICHE ──
        Paragraph titre = new Paragraph(
            "Fiche d'évaluation du Projet de Fin d'Étude", fontTitre);
        titre.setAlignment(Element.ALIGN_CENTER);
        titre.setSpacingAfter(2);
        doc.add(titre);

        Paragraph annee = new Paragraph(
            "Année Universitaire : 2024-2025", fontNormal);
        annee.setAlignment(Element.ALIGN_CENTER);
        annee.setSpacingAfter(8);
        doc.add(annee);

        // ── NOM ÉTUDIANT ──
        Paragraph nomLabel = new Paragraph();
        nomLabel.add(new Chunk("Nom - Prénom de l'élève ingénieur : ", fontBold));
        doc.add(nomLabel);

        Paragraph nomVal = new Paragraph(
            "        " + s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom(),
            fontNormal);
        nomVal.setSpacingAfter(6);
        doc.add(nomVal);

        // ── FILIÈRE ──
        Paragraph filiereLabel = new Paragraph();
        filiereLabel.add(new Chunk("Filière :          ", fontBold));
        String filiere = s.getEtudiant().getFiliere().toString();

        // Cases à cocher avec tableau
        filiereLabel.setSpacingAfter(6);
        doc.add(filiereLabel);

        PdfPTable filiereTable = new PdfPTable(6);
        filiereTable.setWidthPercentage(100);
        filiereTable.setWidths(new float[]{0.5f, 2.5f, 0.5f, 2f, 0.5f, 4f});
        filiereTable.setSpacingAfter(8);

        // Case ID/DATA
        PdfPCell cbID = new PdfPCell(new Phrase(
          (filiere.equals("ID") || filiere.equals("DATA")) ? "☑" : "☐", fontNormal));
        cbID.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        cbID.setPaddingTop(2);
        filiereTable.addCell(cbID);

        PdfPCell lblID = new PdfPCell(new Phrase("Ingénierie des Données", fontNormal));
        lblID.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        filiereTable.addCell(lblID);

        // Case GI
        PdfPCell cbGI = new PdfPCell(new Phrase(filiere.equals("GI") ? "☑" : "☐", fontNormal));
        cbGI.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        cbGI.setPaddingTop(2);
        filiereTable.addCell(cbGI);

        PdfPCell lblGI = new PdfPCell(new Phrase("Génie Informatique", fontNormal));
        lblGI.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        filiereTable.addCell(lblGI);

        // Case TDIA
        PdfPCell cbTDIA = new PdfPCell(new Phrase(
           (filiere.equals("TDIA") || filiere.equals("TD")) ? "☑" : "☐", fontNormal));
        cbTDIA.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        cbTDIA.setPaddingTop(2);
        filiereTable.addCell(cbTDIA);
 
        PdfPCell lblTDIA = new PdfPCell(new Phrase(
          "Transformation Digitale et Intelligence Artificielle", fontNormal));
        lblTDIA.setBorder(com.itextpdf.text.Rectangle.NO_BORDER);
        filiereTable.addCell(lblTDIA);

        doc.add(filiereTable);
        // ── INTITULÉ RAPPORT ──
        Paragraph rapportLabel = new Paragraph();
        rapportLabel.add(new Chunk("Intitulé du rapport :", fontBold));
        doc.add(rapportLabel);
        Paragraph rapportVal = new Paragraph(
            "        " + (s.getEtudiant().getTitreProjet() != null ?
                s.getEtudiant().getTitreProjet() : "…………………………………………………………………………."),
            fontNormal);
        rapportVal.setSpacingAfter(6);
        doc.add(rapportVal);

        // ── ENCADRANT INTERNE ──
        Paragraph encLabel = new Paragraph();
        encLabel.add(new Chunk("L'encadrant(e) interne :", fontBold));
        doc.add(encLabel);
        Paragraph encVal = new Paragraph(
            "        Pr.  " + s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom(),
            fontNormal);
        encVal.setSpacingAfter(6);
        doc.add(encVal);

        // ── MEMBRES DU JURY ──
        Paragraph juryLabel = new Paragraph("Membres du jury :", fontBold);
        juryLabel.setSpacingAfter(4);
        doc.add(juryLabel);

        // Tableau jury (encadrant = Président, jury2 et jury3 = Rapporteurs)
        PdfPTable juryTable = new PdfPTable(1);
        juryTable.setWidthPercentage(100);
        juryTable.setSpacingAfter(8);

        // Ligne 1 : Encadrant = Président
        String presidentNom = s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom();
        PdfPCell cellPresident = new PdfPCell(
            new Phrase("Pr.   " + presidentNom + "               Président", fontNormal));
        cellPresident.setPadding(6);
        juryTable.addCell(cellPresident);

        // Ligne 2 : Jury2 et Jury3 = Rapporteurs
        String rap1 = s.getJury2() != null ?
            "Pr.   " + s.getJury2().getNom() + " " + s.getJury2().getPrenom() +
            "               Rapporteur" : "Pr.   ………………………………………               Rapporteur";
        String rap2 = s.getJury3() != null ?
            "Pr.   " + s.getJury3().getNom() + " " + s.getJury3().getPrenom() +
            "               Rapporteur" : "Pr.   ………………………………………               Rapporteur";

        PdfPCell cellRapporteurs = new PdfPCell();
        cellRapporteurs.addElement(new Phrase(rap1, fontNormal));
        cellRapporteurs.addElement(new Phrase(rap2, fontNormal));
        cellRapporteurs.setPadding(6);
        juryTable.addCell(cellRapporteurs);

        doc.add(juryTable);

        // ── NOTES ──
        Paragraph noteContenu = new Paragraph();
        noteContenu.add(new Chunk("Note du Contenu", fontBold));
        noteContenu.add(new Chunk(
            " *(En prenant en compte l'appréciation de l'entreprise)*", fontSmall));
        doc.add(noteContenu);

        Paragraph noteC = new Paragraph("C  =  ____", fontNormal);
        noteC.setSpacingAfter(4);
        doc.add(noteC);

        Paragraph noteMemoireLabel = new Paragraph("Note du Mémoire", fontBold);
        doc.add(noteMemoireLabel);
        Paragraph noteM = new Paragraph("M  =  ____", fontNormal);
        noteM.setSpacingAfter(4);
        doc.add(noteM);

        Paragraph noteSoutLabel = new Paragraph("Note de la Soutenance", fontBold);
        doc.add(noteSoutLabel);
        Paragraph noteS = new Paragraph("S  =  ____", fontNormal);
        noteS.setSpacingAfter(4);
        doc.add(noteS);

        // ── TABLEAU MOYENNE ──
        PdfPTable moyTable = new PdfPTable(1);
        moyTable.setWidthPercentage(100);
        moyTable.setSpacingAfter(10);

        PdfPCell moyCell = new PdfPCell();
        moyCell.addElement(new Phrase("MOYENNE", fontBold));
        moyCell.addElement(new Phrase(
            "Moyenne   = C × 0,5 + M × 0,2 + S × 0,3  =  ", fontNormal));
        moyCell.setPadding(8);
        moyTable.addCell(moyCell);
        doc.add(moyTable);

        // ── DATE ET SIGNATURES ──
        Paragraph dateLine = new Paragraph(
            "Le :      " + s.getDate().format(DATE_FMT), fontNormal);
        dateLine.setSpacingAfter(16);
        doc.add(dateLine);

        Paragraph sigLabel = new Paragraph(
            "Signature des membres du jury :", fontNormal);
        sigLabel.setSpacingAfter(10);
        doc.add(sigLabel);

        // Tableau signatures 3 colonnes
        PdfPTable sigTable = new PdfPTable(3);
        sigTable.setWidthPercentage(100);

        String[] sigNoms = {
            "Pr.  " + s.getEncadrant().getNom(),
            s.getJury2() != null ? "Pr.  " + s.getJury2().getNom() : "Pr.  …………",
            s.getJury3() != null ? "Pr.  " + s.getJury3().getNom() : "Pr.  …………"
        };

        for (String nom : sigNoms) {
            PdfPCell sig = new PdfPCell();
            sig.addElement(new Phrase(nom, fontSmall));
            sig.addElement(new Phrase("\n\n\n_____________________", fontSmall));
            sig.setBorder(Rectangle.NO_BORDER);
            sig.setPaddingTop(6);
            sigTable.addCell(sig);
        }
        doc.add(sigTable);

        doc.close();
     } catch (Exception e) {
        throw new RuntimeException("Erreur PV : " + e.getMessage(), e);
     }
  }
    // ── Helpers ──
    private void creerEntete(Sheet sheet, String[] headers, CellStyle style, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void creerCellule(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void ajouterCelluleStyle(PdfPTable table, String text,
            com.itextpdf.text.Font font,
            BaseColor bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4.5f);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(new BaseColor(200, 210, 220));
        cell.setBorderWidth(0.4f);
        table.addCell(cell);
    }

    private void ajouterLignePv(PdfPTable table, String label, String value,
                                  com.itextpdf.text.Font labelFont, com.itextpdf.text.Font valueFont) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, labelFont));
        c1.setBackgroundColor(new BaseColor(230, 236, 255));
        c1.setPadding(5);
        PdfPCell c2 = new PdfPCell(new Phrase(value, valueFont));
        c2.setPadding(5);
        table.addCell(c1);
        table.addCell(c2);
    }
    
    public void genererPvsDocxZip(List<Soutenance> soutenances, OutputStream out) throws IOException {
        Map<String, List<Soutenance>> parEncadrant = soutenances.stream()
            .collect(Collectors.groupingBy(
                s -> s.getEncadrant().getNom() + "_" + s.getEncadrant().getPrenom(),
                TreeMap::new, Collectors.toList()));

        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, List<Soutenance>> entry : parEncadrant.entrySet()) {
                String dossier = "PVs_DOCX/" + entry.getKey() + "/";
                for (Soutenance s : entry.getValue()) {
                    String fichier = dossier + "PV_" +
                        s.getEtudiant().getNom() + "_" +
                        s.getEtudiant().getPrenom() + ".docx";
                    zip.putNextEntry(new ZipEntry(fichier));
                    genererUnPvDocx(s, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void genererUnPvDocx(Soutenance s, OutputStream out) throws IOException {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc =
                 new org.apache.poi.xwpf.usermodel.XWPFDocument()) {

            // Entête
            org.apache.poi.xwpf.usermodel.XWPFParagraph header = doc.createParagraph();
            header.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun r = header.createRun();
            r.setBold(true); r.setFontSize(14);
            r.setText("ENSA Al Hoceima — Département Mathématiques & Informatique");
            r.addBreak();
            r.setText("Année Universitaire 2024/2025");

            doc.createParagraph(); // ligne vide

            // Titre PV
            org.apache.poi.xwpf.usermodel.XWPFParagraph titre = doc.createParagraph();
            titre.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun rt = titre.createRun();
            rt.setBold(true); rt.setFontSize(16);
            rt.setText("PROCÈS-VERBAL DE SOUTENANCE PFE");

            doc.createParagraph();

            // Tableau infos
            org.apache.poi.xwpf.usermodel.XWPFTable table = doc.createTable(8, 2);
            table.setWidth("100%");
            String[][] rows = {
                {"Étudiant(e)", s.getEtudiant().getNom() + " " + s.getEtudiant().getPrenom()},
                {"Filière", s.getEtudiant().getFiliere().toString()},
                {"Date", s.getDate().format(DATE_FMT)},
                {"Heure", s.getHeure().format(HEURE_FMT)},
                {"Salle", s.getSalle().getNom()},
                {"Encadrant", s.getEncadrant().getNom() + " " + s.getEncadrant().getPrenom()},
                {"Jury 1", s.getJury1() != null ? s.getJury1().getNom() + " " + s.getJury1().getPrenom() : "-"},
                {"Jury 2", s.getJury2() != null ? s.getJury2().getNom() + " " + s.getJury2().getPrenom() : "-"}
            };
            for (int i = 0; i < rows.length; i++) {
                table.getRow(i).getCell(0).setText(rows[i][0]);
                table.getRow(i).getCell(1).setText(rows[i][1]);
            }

            doc.createParagraph();

            // Note et signature
            org.apache.poi.xwpf.usermodel.XWPFParagraph note = doc.createParagraph();
            note.createRun().setText("Note finale : _______ / 20");
            doc.createParagraph().createRun().setText("Mention : _____________________________");
            doc.createParagraph().createRun().setText("Observations :");
            doc.createParagraph().createRun().setText("________________________________________________________________");
            doc.createParagraph().createRun().setText("________________________________________________________________");
            doc.createParagraph();

            // Signatures
            org.apache.poi.xwpf.usermodel.XWPFTable sigTable = doc.createTable(1, 3);
            sigTable.getRow(0).getCell(0).setText("Encadrant\n\n\n_____________");
            sigTable.getRow(0).getCell(1).setText("Président du jury\n\n\n_____________");
            sigTable.getRow(0).getCell(2).setText("Membre du jury\n\n\n_____________");

            doc.write(out);
        }
    }
        
        public void sauvegarderVersion(List<Soutenance> soutenances, String dossierBase) throws IOException {
            // Trouver le prochain numéro de version
            java.io.File base = new java.io.File(dossierBase);
            base.mkdirs();
            int version = 1;
            while (new java.io.File(dossierBase + "/version_" + version).exists()) {
                version++;
            }
            java.io.File dossierVersion = new java.io.File(dossierBase + "/version_" + version);
            dossierVersion.mkdirs();

            // Sauvegarder Excel
            try (java.io.FileOutputStream excelOut =
                     new java.io.FileOutputStream(new java.io.File(dossierVersion, "planning.xlsx"))) {
                exporterExcel(soutenances, excelOut);
            }

            // Sauvegarder PDF
            try (java.io.FileOutputStream pdfOut =
                     new java.io.FileOutputStream(new java.io.File(dossierVersion, "planning.pdf"))) {
                exporterPdf(soutenances, pdfOut);
            }

            // Sauvegarder PVs ZIP
            try (java.io.FileOutputStream zipOut =
                     new java.io.FileOutputStream(new java.io.File(dossierVersion, "PVs.zip"))) {
                genererPvsZip(soutenances, zipOut);
            }
        }
    
}