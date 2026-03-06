package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import www.ontologyutils.toolbox.Utils;

class ShapleyWeakeningRepairTest {

    @Test
    void repairMakesPreparedOntologyConsistent() {
        OWLOntology ontology = Utils.newOntology("libs/ontologyutils/resources/inconsistent-leftpolicies-small.owl");
        OWLOntology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

        assertFalse(Utils.isConsistent(prepared), "Prepared ontology should start inconsistent for this fixture.");

        ShapleyWeakeningRepair repair = new ShapleyWeakeningRepair(prepared);
        OWLOntology repaired = repair.repair();

        assertTrue(Utils.isConsistent(repaired), "Repaired ontology should be consistent.");
    }
}

