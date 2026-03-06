package io.github.josecoliveira.newweakening.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Utils;

/**
 * Computes inconsistency contributions using the (unnormalized) Shapley value.
 */
public class ShapleyInconsistencyScorer {

    private final Map<Set<OWLAxiom>, Integer> drasticCache;
    private final List<Double> factorialCache;

    public ShapleyInconsistencyScorer() {
        this.drasticCache = new HashMap<>();
        this.factorialCache = new ArrayList<>();
        this.factorialCache.add(1.0d);
    }

    public double shapleyInconsistencyValue(Set<OWLAxiom> axioms, OWLAxiom axiom) {
        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.add(axiom);
        int n = universe.size();

        List<OWLAxiom> baseCoalition = new ArrayList<>(universe);
        baseCoalition.remove(axiom);

        final double[] total = new double[] { 0.0d };
        forEachSubset(baseCoalition, subset -> {
            Set<OWLAxiom> coalitionWithAxiom = new HashSet<>(subset);
            coalitionWithAxiom.add(axiom);
            int c = coalitionWithAxiom.size();
            double weight = factorial(c - 1) * factorial(n - c);
            int marginalContribution = drasticInconsistencyValue(coalitionWithAxiom)
                    - drasticInconsistencyValue(subset);
            total[0] += weight * marginalContribution;
        });
        return total[0];
    }

    private int drasticInconsistencyValue(Set<OWLAxiom> subset) {
        Set<OWLAxiom> key = Collections.unmodifiableSet(new HashSet<>(subset));
        Integer cached = drasticCache.get(key);
        if (cached != null) {
            return cached;
        }
        int value = Utils.isConsistent(subset) ? 0 : 1;
        drasticCache.put(key, value);
        return value;
    }

    private double factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Factorial is undefined for negative numbers: " + n);
        }
        while (factorialCache.size() <= n) {
            int k = factorialCache.size();
            factorialCache.add(factorialCache.get(k - 1) * k);
        }
        return factorialCache.get(n);
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

