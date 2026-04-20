package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import www.ontologyutils.toolbox.Ontology;

class OntologyBestShapleyWeakeningTest {

    @Test
    void repairMakesPreparedOntologyConsistent() {
        try (Ontology ontology = Ontology
                .loadOntology("libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            Ontology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

            assertFalse(prepared.isConsistent(), "Prepared ontology should start inconsistent for this fixture.");

            OntologyBestShapleyWeakening repair = new OntologyBestShapleyWeakening();
            List<String> messages = new ArrayList<>();
            repair.setInfoCallback(messages::add);
            repair.repair(prepared);

            assertTrue(prepared.isConsistent(), "Repaired ontology should be consistent.");

            String output = String.join(System.lineSeparator(), messages);
            assertTrue(output.contains("Selected a reference ontology with 8 axioms."),
                    "Verbose output should report the reference ontology size.");
            assertTrue(output.contains("Reference ontology:"),
                    "Verbose output should print the selected reference ontology.");
            assertTrue(output.contains("Found 1 possible bad axioms."),
                    "Verbose output should only score non-reference axioms as bad-axiom candidates.");
            assertTrue(output.contains("Selected the bad axiom RaiseWelfare(Sweden)."),
                    "Verbose output should select the only non-reference assertion in this fixture.");
            int start = output.indexOf("Reference ontology:");
            int end = output.indexOf("Found 1 possible bad axioms.");
            assertTrue(start >= 0 && end > start,
                    "Verbose output should include a bounded reference ontology section.");

            String referenceSection = output.substring(start, end);
            assertTrue(referenceSection.contains("Total axioms: 8"),
                    "Reference ontology section should show its size.");
            assertTrue(referenceSection.contains("  LeftPolicy ⊑ RaiseWages ⊔ RaiseWelfare"),
                    "Reference ontology should include normalized TBox axioms.");
            assertTrue(referenceSection.contains("  RaiseWages(Sweden)"),
                    "Reference ontology should include the retained class assertion.");
            assertFalse(referenceSection.contains("  RaiseWelfare(Sweden)"),
                    "Reference ontology should omit the conflicting assertion that is left out of the selected MCS.");
        }
    }
}


