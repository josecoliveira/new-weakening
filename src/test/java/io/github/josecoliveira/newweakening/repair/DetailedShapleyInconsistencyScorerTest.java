package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;

class DetailedShapleyInconsistencyScorerTest {

    private static final String INCONSISTENT_FIXTURE = "libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl";

    @Test
    void detailedRelativeValueMatchesExactScorerForEachAxiom() {
        try (Ontology ontology = Ontology.loadOntology(INCONSISTENT_FIXTURE)) {
            Set<OWLAxiom> axioms = ontology.refutableAxioms().collect(Collectors.toSet());
            printOntology("Ontology before detailed computation:", axioms);

            DetailedShapleyInconsistencyScorer detailedScorer = new DetailedShapleyInconsistencyScorer();
            ShapleyInconsistencyScorer exactScorer = new ShapleyInconsistencyScorer();

            for (OWLAxiom axiom : axioms) {
                double detailedValue = detailedScorer
                        .detailedShapleyInconsistencyValue(axioms, axiom)
                        .relativeValue;
                double exactValue = exactScorer.shapleyInconsistencyValue(axioms, axiom);
                assertEquals(exactValue, detailedValue, 1.0e-12,
                        "Detailed scorer relative value must match exact scorer value.");
            }
        }
    }

    @Test
    void detailedContributionsSumToAbsoluteValue() {
        try (Ontology ontology = Ontology.loadOntology(INCONSISTENT_FIXTURE)) {
            Set<OWLAxiom> axioms = ontology.refutableAxioms().collect(Collectors.toSet());
            printOntology("Ontology before detailed computation:", axioms);
            DetailedShapleyInconsistencyScorer detailedScorer = new DetailedShapleyInconsistencyScorer();

            for (OWLAxiom axiom : axioms) {
                var result = detailedScorer.detailedShapleyInconsistencyValue(axioms, axiom);
                double additions = result.contributions.stream()
                        .mapToDouble(contribution -> contribution.summationAddition)
                        .sum();
                assertEquals(result.absoluteValue, additions, 1.0e-12,
                        "Sum of per-size contribution additions must equal the absolute value.");
            }
        }
    }

    private static void printOntology(String title, Set<OWLAxiom> axioms) {
        System.out.println(title);
        axioms.stream()
                .map(AlcPrinter::print)
                .filter(rendered -> !rendered.isBlank())
                .sorted()
                .forEach(rendered -> System.out.println("  " + rendered));
    }
}


