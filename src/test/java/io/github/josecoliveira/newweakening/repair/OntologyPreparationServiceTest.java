package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import www.ontologyutils.toolbox.Utils;

class OntologyPreparationServiceTest {

    @Test
    void preparedOntologyContainsOnlySupportedLogicalAxioms() {
        OWLOntology ontology = Utils.newOntology("libs/ontologyutils/resources/inconsistent-leftpolicies-small.owl");
        OWLOntology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

        boolean onlySupported = prepared.logicalAxioms().allMatch(this::isSupported);
        assertTrue(onlySupported, "Prepared ontology should only have SUBCLASS_OF or CLASS_ASSERTION logical axioms.");
    }

    private boolean isSupported(OWLAxiom axiom) {
        return axiom.isOfType(AxiomType.SUBCLASS_OF) || axiom.isOfType(AxiomType.CLASS_ASSERTION);
    }
}

