package io.github.josecoliveira.newweakening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.josecoliveira.newweakening.repair.ShapleyInconsistencyScorer;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;

class ShapleyRepairAppCliOptionsTest {

    @Test
    void parsesVerboseEvenWhenModeIsPositional() {
        var options = ShapleyRepairApp.parseCliOptions(new String[] { "path.owl", "approximate", "--verbose" });

        assertTrue(options.verbose());
        assertEquals(ShapleyInconsistencyScorer.Mode.APPROXIMATE, options.mode());
    }

    @Test
    void parsesModeAndVerboseInAnyOrder() {
        var options = ShapleyRepairApp.parseCliOptions(
                new String[] { "path.owl", "--verbose", "--mode", "approximate", "--seed", "42" });

        assertTrue(options.verbose());
        assertEquals(ShapleyInconsistencyScorer.Mode.APPROXIMATE, options.mode());
        assertEquals(42L, options.approximationSeed());
    }

    @Test
    void rejectsUnknownOption() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ShapleyRepairApp.parseCliOptions(new String[] { "path.owl", "--unknown" }));

        assertTrue(ex.getMessage().contains("Unknown argument"));
    }

    @Test
    void defaultsToTroquard2018PresetConfiguration() {
        var options = ShapleyRepairApp.parseCliOptions(new String[] { "path.owl" });

        assertEquals(RefOntologyStrategy.ONE_MCS, options.refOntologyStrategy());
        assertEquals(BadAxiomStrategy.IN_SOME_MUS, options.badAxiomStrategy());
        assertEquals(io.github.josecoliveira.newweakening.repair.OntologyBestShapleyWeakening.TROQUARD2018_FLAGS,
                options.weakeningFlags());
    }
}


