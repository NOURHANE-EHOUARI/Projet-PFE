package ma.ensa.pfe.controller;

import ma.ensa.pfe.dao.SalleRepository;
import ma.ensa.pfe.model.Salle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour SalleController.
 * Utilise Mockito pour simuler le repository.
 */
class SalleControllerTest {

    @Mock
    private SalleRepository salleRepository;

    @InjectMocks
    private SalleController salleController;

    private Salle salle1;
    private Salle salle2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        salle1 = new Salle("S4A", 30);
        salle1.setId(1L);

        salle2 = new Salle("AMPHI A", 100);
        salle2.setId(2L);
    }

    @Test
    @DisplayName("liste - retourne la vue salles/liste")
    void liste_retourneLaBonneVue() {
        when(salleRepository.findAll()).thenReturn(Arrays.asList(salle1, salle2));
        ExtendedModelMap model = new ExtendedModelMap();

        String vue = salleController.liste(model);

        assertEquals("salles/liste", vue);
        verify(salleRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("liste - le modèle contient la liste des salles")
    void liste_modelContientLesSalles() {
        List<Salle> salles = Arrays.asList(salle1, salle2);
        when(salleRepository.findAll()).thenReturn(salles);
        ExtendedModelMap model = new ExtendedModelMap();

        salleController.liste(model);

        assertEquals(salles, model.get("salles"));
    }

    @Test
    @DisplayName("enregistrer - redirige vers /salles après succès")
    void enregistrer_redirigeSiNouveauSalle() {
        when(salleRepository.existsByNom("S16A")).thenReturn(false);
        Salle nouvelleSalle = new Salle("S16A", 25);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String result = salleController.enregistrer(nouvelleSalle, ra);

        assertEquals("redirect:/salles", result);
        verify(salleRepository, times(1)).save(nouvelleSalle);
    }

    @Test
    @DisplayName("enregistrer - redirige avec erreur si salle déjà existante")
    void enregistrer_erreurSiSalleExisteDeja() {
        when(salleRepository.existsByNom("S4A")).thenReturn(true);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

        String result = salleController.enregistrer(salle1, ra);

        assertEquals("redirect:/salles", result);
        verify(salleRepository, never()).save(any());
        assertNotNull(ra.getFlashAttributes().get("erreur"));
    }

    @Test
    @DisplayName("supprimer - appelle deleteById et redirige")
    void supprimer_supprimeLaSalleEtRedirige() {
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        doNothing().when(salleRepository).deleteById(1L);

        String result = salleController.supprimer(1L, ra);

        assertEquals("redirect:/salles", result);
        verify(salleRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("formulaireModifier - retourne la vue avec la salle")
    void formulaireModifier_retourneVueAvecSalle() {
        when(salleRepository.findById(1L)).thenReturn(Optional.of(salle1));
        ExtendedModelMap model = new ExtendedModelMap();

        String vue = salleController.formulaireModifier(1L, model);

        assertEquals("salles/formulaire", vue);
        assertEquals(salle1, model.get("salle"));
    }
}
