package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;

class OntologyPreparationServiceTest {

    @Test
    void preparedOntologyContainsOnlySupportedLogicalAxioms() {
        try (Ontology ontology = Ontology.loadOntology(
                "libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            Ontology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

            boolean onlySupported = prepared.logicalAxioms().allMatch(this::isSupported);
            assertTrue(onlySupported,
                    "Prepared ontology should only have SUBCLASS_OF or CLASS_ASSERTION logical axioms.");
        }
    }

    private boolean isSupported(OWLAxiom axiom) {
        return axiom.isOfType(AxiomType.SUBCLASS_OF) || axiom.isOfType(AxiomType.CLASS_ASSERTION);
    }
}

