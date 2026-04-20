package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import www.ontologyutils.toolbox.Ontology;

class OntologyBestShapleyWeakening2Test {

    @Test
    void repairMakesPreparedOntologyConsistent() {
        try (Ontology ontology = Ontology
                .loadOntology("libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            Ontology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

            assertFalse(prepared.isConsistent(), "Prepared ontology should start inconsistent for this fixture.");

            OntologyBestShapleyWeakening2 repair = new OntologyBestShapleyWeakening2();
            List<String> messages = new ArrayList<>();
            repair.setInfoCallback(messages::add);
            repair.repair(prepared);

            assertTrue(prepared.isConsistent(), "Repaired ontology should be consistent.");

            String output = String.join(System.lineSeparator(), messages);
            assertTrue(output.contains("Selected a reference ontology with 8 axioms."),
                    "Verbose output should report the reference ontology size.");
        }
    }
}




