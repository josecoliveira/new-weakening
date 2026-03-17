package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import www.ontologyutils.toolbox.Ontology;

class ShapleyWeakeningRepairTest {

    @Test
    void repairMakesPreparedOntologyConsistent() {
        try (Ontology ontology = Ontology
                .loadOntology("libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            Ontology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

            assertFalse(prepared.isConsistent(), "Prepared ontology should start inconsistent for this fixture.");

            ShapleyWeakeningRepair repair = new ShapleyWeakeningRepair();
            repair.repair(prepared);

            assertTrue(prepared.isConsistent(), "Repaired ontology should be consistent.");
        }
    }
}

