package ma.ensa.pfe.service;

import ma.ensa.pfe.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExportServiceTest {

    private ExportService exportService;
    private List<Soutenance> soutenances;

    @BeforeEach
    void setUp() {
        exportService = new ExportService();

        // Utiliser constructeurs directs sans setters Lombok
        Salle salle = new Salle("S4A", 30);
        salle.setId(1L);

        // Créer Professeur via réflexion ou builder — ici on crée Soutenance directement
        Soutenance s1 = creerSoutenance(1L, "CHENTOUF", "Ismail",
            Etudiant.Filiere.GI, Etudiant.Langue.FR,
            "BADI", "Imad", "Informatique", false,
            "BOUHAFER", "Fadwa",
            "ADDAM", "Mohamed",
            salle, LocalDate.of(2025, 6, 23), LocalTime.of(9, 0));

        Soutenance s2 = creerSoutenance(2L, "ARBAHI", "Jawad",
            Etudiant.Filiere.ID, Etudiant.Langue.EN,
            "BOUHAFER", "Fadwa", "Informatique", true,
            "BADI", "Imad",
            "ADDAM", "Mohamed",
            salle, LocalDate.of(2025, 6, 23), LocalTime.of(10, 0));

        soutenances = Arrays.asList(s1, s2);
    }

    private Soutenance creerSoutenance(Long id,
            String etudiantNom, String etudiantPrenom,
            Etudiant.Filiere filiere, Etudiant.Langue langue,
            String encNom, String encPrenom, String spec, boolean anglais,
            String j1Nom, String j1Prenom,
            String j2Nom, String j2Prenom,
            Salle salle, LocalDate date, LocalTime heure) {

        // Professeur encadrant
        Professeur enc = new Professeur();
        enc.setNom(encNom); enc.setPrenom(encPrenom);
        enc.setSpecialite(spec); enc.setParleAnglais(anglais);

        Professeur jury1 = new Professeur();
        jury1.setNom(j1Nom); jury1.setPrenom(j1Prenom);

        Professeur jury2 = new Professeur();
        jury2.setNom(j2Nom); jury2.setPrenom(j2Prenom);

        Etudiant etu = new Etudiant();
        etu.setNom(etudiantNom); etu.setPrenom(etudiantPrenom);
        etu.setFiliere(filiere); etu.setLangue(langue);
        etu.setEncadrant(enc);

        Soutenance s = new Soutenance();
        s.setId(id);
        s.setEtudiant(etu);
        s.setEncadrant(enc);
        s.setJury1(jury1);
        s.setJury2(jury2);
        s.setSalle(salle);
        s.setDate(date);
        s.setHeure(heure);
        return s;
    }

    @Test
    @DisplayName("exporterExcel - génère un fichier non vide")
    void exporterExcel_genereUnFichierNonVide() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exporterExcel(soutenances, out);
        assertTrue(out.size() > 0);
    }

    @Test
    @DisplayName("exporterExcel - fonctionne avec liste vide")
    void exporterExcel_avecListeVide() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> exportService.exporterExcel(new java.util.ArrayList<>(), out));
        assertTrue(out.size() > 0);
    }

    @Test
    @DisplayName("exporterPdf - génère un fichier non vide")
    void exporterPdf_genereUnFichierNonVide() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exporterPdf(soutenances, out);
        assertTrue(out.size() > 0);
    }

    @Test
    @DisplayName("exporterPdf - commence par la signature PDF")
    void exporterPdf_commenceParSignaturePdf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exporterPdf(soutenances, out);
        byte[] bytes = out.toByteArray();
        assertEquals("%PDF", new String(bytes, 0, 4));
    }

    @Test
    @DisplayName("exporterPdf - fonctionne avec liste vide")
    void exporterPdf_avecListeVide() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> exportService.exporterPdf(new java.util.ArrayList<>(), out));
    }

    @Test
    @DisplayName("genererPvsZip - génère une archive non vide")
    void genererPvsZip_genereUneArchiveNonVide() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.genererPvsZip(soutenances, out);
        assertTrue(out.size() > 0);
    }

    @Test
    @DisplayName("genererPvsZip - commence par signature ZIP PK")
    void genererPvsZip_commenceParSignatureZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.genererPvsZip(soutenances, out);
        byte[] bytes = out.toByteArray();
        assertEquals('P', (char) bytes[0]);
        assertEquals('K', (char) bytes[1]);
    }

    @Test
    @DisplayName("genererPvsZip - un PV par étudiant")
    void genererPvsZip_unPvParEtudiant() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.genererPvsZip(soutenances, out);

        ZipInputStream zip = new ZipInputStream(
            new ByteArrayInputStream(out.toByteArray()));
        int nbPvs = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.getName().endsWith(".pdf")) nbPvs++;
            zip.closeEntry();
        }
        assertEquals(soutenances.size(), nbPvs);
    }

    @Test
    @DisplayName("genererPvsZip - fonctionne avec liste vide")
    void genererPvsZip_avecListeVide() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> {
            try { exportService.genererPvsZip(new java.util.ArrayList<>(), out); }
            catch (IOException e) { throw new RuntimeException(e); }
        });
    }
}
