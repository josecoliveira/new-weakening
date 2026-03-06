package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import www.ontologyutils.toolbox.Utils;

class ShapleyInconsistencyScorerTest {

    @Test
    void scorerReturnsZeroForAxiomInConsistentOntology() {
        OWLOntology ontology = Utils.newOntology("libs/ontologyutils/resources/a-and-b.owl");
        Set<OWLAxiom> axioms = ontology.logicalAxioms().collect(Collectors.toSet());
        OWLAxiom axiom = axioms.iterator().next();

        ShapleyInconsistencyScorer scorer = new ShapleyInconsistencyScorer();
        double value = scorer.shapleyInconsistencyValue(axioms, axiom);

        assertEquals(0.0d, value, 0.0000001d, "Any axiom in a consistent ontology should have 0 drastic Shapley value.");
    }

    @Test
    void scorerDetectsPositiveContributionInInconsistentOntology() {
        OWLOntology ontology = Utils.newOntology("libs/ontologyutils/resources/inconsistent-leftpolicies-small.owl");
        OWLOntology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);
        Set<OWLAxiom> axioms = prepared.logicalAxioms().collect(Collectors.toSet());

        ShapleyInconsistencyScorer scorer = new ShapleyInconsistencyScorer();
        double max = axioms.stream()
                .map(ax -> scorer.shapleyInconsistencyValue(axioms, ax))
                .max(Comparator.naturalOrder())
                .orElse(0.0d);

        assertTrue(max > 0.0d, "At least one axiom should contribute positively in an inconsistent ontology.");
    }
}

