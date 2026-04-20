package ma.ensa.pfe.service;

import ma.ensa.pfe.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour ExportService.
 * Vérifie la génération des exports Excel, PDF et ZIP des PVs.
 */
class ExportServiceTest {

    private ExportService exportService;
    private List<Soutenance> soutenances;

    @BeforeEach
    void setUp() {
        exportService = new ExportService();

        // Créer des données de test
        Professeur encadrant = new Professeur();
        encadrant.setId(1L);
        encadrant.setNom("BADI");
        encadrant.setPrenom("Imad");
        encadrant.setSpecialite("Informatique");
        encadrant.setParleAnglais(false);

        Professeur jury1 = new Professeur();
        jury1.setId(2L);
        jury1.setNom("BOUHAFER");
        jury1.setPrenom("Fadwa");
        jury1.setSpecialite("Informatique");
        jury1.setParleAnglais(true);

        Professeur jury2 = new Professeur();
        jury2.setId(3L);
        jury2.setNom("ADDAM");
        jury2.setPrenom("Mohamed");
        jury2.setSpecialite("Mathematiques");
        jury2.setParleAnglais(false);

        Salle salle = new Salle("S4A", 30);
        salle.setId(1L);

        Etudiant etudiant1 = new Etudiant();
        etudiant1.setId(1L);
        etudiant1.setNom("CHENTOUF");
        etudiant1.setPrenom("Ismail");
        etudiant1.setFiliere(Etudiant.Filiere.GI);
        etudiant1.setLangue(Etudiant.Langue.FR);
        etudiant1.setEncadrant(encadrant);

        Etudiant etudiant2 = new Etudiant();
        etudiant2.setId(2L);
        etudiant2.setNom("ARBAHI");
        etudiant2.setPrenom("Jawad");
        etudiant2.setFiliere(Etudiant.Filiere.ID);
        etudiant2.setLangue(Etudiant.Langue.EN);
        etudiant2.setEncadrant(jury1);

        Soutenance s1 = new Soutenance();
        s1.setId(1L);
        s1.setEtudiant(etudiant1);
        s1.setEncadrant(encadrant);
        s1.setJury1(jury1);
        s1.setJury2(jury2);
        s1.setSalle(salle);
        s1.setDate(LocalDate.of(2025, 6, 23));
        s1.setHeure(LocalTime.of(9, 0));

        Soutenance s2 = new Soutenance();
        s2.setId(2L);
        s2.setEtudiant(etudiant2);
        s2.setEncadrant(jury1);
        s2.setJury1(encadrant);
        s2.setJury2(jury2);
        s2.setSalle(salle);
        s2.setDate(LocalDate.of(2025, 6, 23));
        s2.setHeure(LocalTime.of(10, 0));

        soutenances = Arrays.asList(s1, s2);
    }

    // ── Tests Export Excel ──

    @Test
    @DisplayName("exporterExcel - génère un fichier non vide")
    void exporterExcel_genereUnFichierNonVide() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exporterExcel(soutenances, out);
        assertTrue(out.size() > 0, "Le fichier Excel ne doit pas être vide");
    }

    @Test
    @DisplayName("exporterExcel - fonctionne avec liste vide")
    void exporterExcel_avecListeVide() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> exportService.exporterExcel(List.of(), out));
        assertTrue(out.size() > 0, "Le fichier Excel vide doit quand même être généré");
    }

    @Test
    @DisplayName("exporterExcel - génère un fichier plus grand avec plus de données")
    void exporterExcel_tailleCroitAvecDonnees() throws IOException {
        ByteArrayOutputStream outVide = new ByteArrayOutputStream();
        ByteArrayOutputStream outPlein = new ByteArrayOutputStream();
        exportService.exporterExcel(List.of(), outVide);
        exportService.exporterExcel(soutenances, outPlein);
        assertTrue(outPlein.size() > outVide.size(),
            "Un fichier avec données doit être plus grand qu'un fichier vide");
    }

    // ── Tests Export PDF ──

    @Test
    @DisplayName("exporterPdf - génère un fichier non vide")
    void exporterPdf_genereUnFichierNonVide() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exporterPdf(soutenances, out);
        assertTrue(out.size() > 0, "Le PDF ne doit pas être vide");
    }

    @Test
    @DisplayName("exporterPdf - fonctionne avec liste vide")
    void exporterPdf_avecListeVide() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> exportService.exporterPdf(List.of(), out));
    }

    @Test
    @DisplayName("exporterPdf - commence par la signature PDF %PDF")
    void exporterPdf_commenceParSignaturePdf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.exporterPdf(soutenances, out);
        byte[] bytes = out.toByteArray();
        String debut = new String(bytes, 0, Math.min(4, bytes.length));
        assertEquals("%PDF", debut, "Le fichier doit commencer par %PDF");
    }

    // ── Tests Génération PVs ZIP ──

    @Test
    @DisplayName("genererPvsZip - génère une archive non vide")
    void genererPvsZip_genereUneArchiveNonVide() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.genererPvsZip(soutenances, out);
        assertTrue(out.size() > 0, "Le ZIP ne doit pas être vide");
    }

    @Test
    @DisplayName("genererPvsZip - commence par la signature ZIP PK")
    void genererPvsZip_commenceParSignatureZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.genererPvsZip(soutenances, out);
        byte[] bytes = out.toByteArray();
        assertEquals('P', (char) bytes[0], "Le ZIP doit commencer par PK");
        assertEquals('K', (char) bytes[1], "Le ZIP doit commencer par PK");
    }

    @Test
    @DisplayName("genererPvsZip - fonctionne avec liste vide")
    void genererPvsZip_avecListeVide() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> exportService.genererPvsZip(List.of(), out));
    }

    @Test
    @DisplayName("genererPvsZip - un PV par étudiant (unicité)")
    void genererPvsZip_unPvParEtudiant() throws IOException {
        // 2 soutenances = 2 PVs dans le ZIP
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exportService.genererPvsZip(soutenances, out);

        // Lire le ZIP et compter les entrées PDF
        java.util.zip.ZipInputStream zip =
            new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(out.toByteArray()));

        int nbPvs = 0;
        java.util.zip.ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (entry.getName().endsWith(".pdf")) nbPvs++;
            zip.closeEntry();
        }
        assertEquals(soutenances.size(), nbPvs,
            "Il doit y avoir exactement 1 PV par soutenance");
    }
}
