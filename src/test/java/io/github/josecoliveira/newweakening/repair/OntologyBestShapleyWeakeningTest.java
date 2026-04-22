package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import www.ontologyutils.toolbox.Ontology;

class OntologyBestShapleyWeakeningTest {

    @Test
    void repairMakesOntologyConsistent() {
        try (Ontology ontology = Ontology
                .loadOntology("libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            assertFalse(ontology.isConsistent(), "Fixture ontology should start inconsistent.");

            OntologyBestShapleyWeakening repair = new OntologyBestShapleyWeakening();
            List<String> messages = new ArrayList<>();
            repair.setInfoCallback(messages::add);
            repair.apply(ontology);

            assertTrue(ontology.isConsistent(), "Repaired ontology should be consistent.");

            String output = String.join(System.lineSeparator(), messages);
            assertTrue(output.contains("Selected a reference ontology with"),
                    "Verbose output should report the reference ontology size.");
        }
    }
}


