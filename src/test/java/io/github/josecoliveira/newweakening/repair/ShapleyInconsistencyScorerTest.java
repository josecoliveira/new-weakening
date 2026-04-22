package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

class ShapleyInconsistencyScorerTest {

    private static final String INCONSISTENT_FIXTURE = "libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl";

    @Test
    void scorerReturnsZeroForAxiomInConsistentOntology() {
        try (Ontology ontology = Ontology.loadOntology("libs/ontologyutils/src/test/resources/el/a-and-b.owl")) {
            Set<OWLAxiom> axioms = ontology.logicalAxioms().collect(Collectors.toSet());
            OWLAxiom axiom = axioms.iterator().next();

            ShapleyInconsistencyScorer scorer = new ShapleyInconsistencyScorer();
            double value = scorer.shapleyInconsistencyValue(axioms, axiom);

            assertEquals(0.0d, value, 0.0000001d,
                    "Any axiom in a consistent ontology should have 0 drastic Shapley value.");
        }
    }

    @Test
    void scorerDetectsPositiveContributionInInconsistentOntology() {
        try (Ontology ontology = Ontology.loadOntology(INCONSISTENT_FIXTURE)) {
            Set<OWLAxiom> axioms = ontology.refutableAxioms().collect(Collectors.toSet());

            ShapleyInconsistencyScorer scorer = new ShapleyInconsistencyScorer();
            double max = axioms.stream()
                    .map(ax -> scorer.shapleyInconsistencyValue(axioms, ax))
                    .max(Comparator.naturalOrder())
                    .orElse(0.0d);

            assertTrue(max > 0.0d, "At least one axiom should contribute positively in an inconsistent ontology.");
        }
    }

    @Test
    void normalizedExactShapleySumsToOneForInconsistentOntology() {
        try (Ontology ontology = Ontology.loadOntology(INCONSISTENT_FIXTURE)) {
            Set<OWLAxiom> axioms = ontology.refutableAxioms().collect(Collectors.toSet());

            ShapleyInconsistencyScorer scorer = new ShapleyInconsistencyScorer();
            double total = axioms.stream()
                    .mapToDouble(ax -> scorer.shapleyInconsistencyValue(axioms, ax))
                    .sum();

            assertEquals(1.0d, total, 1.0e-9,
                    "Normalized Shapley values should sum to 1 for an inconsistent drastic game.");
        }
    }

    @Test
    void approximateShapleyIsCloseToExactOnFixture() {
        try (Ontology ontology = Ontology.loadOntology(INCONSISTENT_FIXTURE)) {
            Set<OWLAxiom> axioms = ontology.refutableAxioms().collect(Collectors.toSet());

            ShapleyInconsistencyScorer exactScorer = new ShapleyInconsistencyScorer();
            ShapleyInconsistencyScorer approximateScorer = new ShapleyInconsistencyScorer(
                    ShapleyInconsistencyScorer.Mode.APPROXIMATE,
                    16384,
                    13L);

            Map<OWLAxiom, Double> exactValues = axioms.stream()
                    .collect(Collectors.toMap(ax -> ax, ax -> exactScorer.shapleyInconsistencyValue(axioms, ax)));
            Map<OWLAxiom, Double> approximateValues = axioms.stream()
                    .collect(Collectors.toMap(ax -> ax, ax -> approximateScorer.shapleyInconsistencyValue(axioms, ax)));

            for (OWLAxiom axiom : axioms) {
                assertEquals(exactValues.get(axiom), approximateValues.get(axiom), 0.05d,
                        "Approximate Shapley should remain close to exact value for each axiom.");
            }
        }
    }

    @Test
    void optimizedExactMatchesBruteForceOracleOnFixture() {
        try (Ontology ontology = Ontology.loadOntology(INCONSISTENT_FIXTURE)) {
            Set<OWLAxiom> axioms = ontology.refutableAxioms().collect(Collectors.toSet());
            ShapleyInconsistencyScorer scorer = new ShapleyInconsistencyScorer();

            for (OWLAxiom axiom : axioms) {
                double optimized = scorer.shapleyInconsistencyValue(axioms, axiom);
                double bruteForce = bruteForceShapleyInconsistencyValue(axioms, axiom);
                assertEquals(bruteForce, optimized, 1.0e-12,
                        "Optimized exact computation must match brute-force Shapley value.");
            }
        }
    }

    private static double bruteForceShapleyInconsistencyValue(Set<OWLAxiom> axioms, OWLAxiom axiom) {
        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.add(axiom);

        List<OWLAxiom> baseCoalition = universe.stream()
                .filter(candidate -> !candidate.equals(axiom))
                .toList();
        int n = universe.size();

        final double[] total = new double[] { 0.0d };
        forEachSubset(baseCoalition, subset -> {
            Set<OWLAxiom> withAxiom = new HashSet<>(subset);
            withAxiom.add(axiom);
            int c = withAxiom.size();

            double weight = factorial(c - 1) * factorial(n - c);
            int marginal = drasticInconsistencyValue(withAxiom) - drasticInconsistencyValue(subset);
            total[0] += weight * marginal;
        });

        return total[0] / factorial(n);
    }

    private static int drasticInconsistencyValue(Set<OWLAxiom> subset) {
        return Utils.isConsistent(subset) ? 0 : 1;
    }

    private static double factorial(int n) {
        double result = 1.0d;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    private static <T> void forEachSubset(List<T> elements, Consumer<Set<T>> consumer) {
        forEachSubset(elements, 0, new HashSet<>(), consumer);
    }

    private static <T> void forEachSubset(List<T> elements, int index, Set<T> current, Consumer<Set<T>> consumer) {
        if (index == elements.size()) {
            consumer.accept(new HashSet<>(current));
            return;
        }

        T element = elements.get(index);
        forEachSubset(elements, index + 1, current, consumer);

        current.add(element);
        forEachSubset(elements, index + 1, current, consumer);
        current.remove(element);
    }
}

