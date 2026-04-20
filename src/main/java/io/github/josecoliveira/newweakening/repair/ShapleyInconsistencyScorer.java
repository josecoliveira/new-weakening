package io.github.josecoliveira.newweakening.repair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Utils;

/**
 * Computes inconsistency contributions using the Shapley value.
 */
public class ShapleyInconsistencyScorer {

    public enum Mode {
        EXACT,
        APPROXIMATE
    }

    private static final int DEFAULT_APPROXIMATION_SAMPLES = 4096;
    private static final long DEFAULT_APPROXIMATION_SEED = 13L;

    private final Map<Set<OWLAxiom>, Integer> drasticCache;
    private final List<Double> factorialCache;
    private final Mode mode;
    private final int approximationSamples;
    private final long approximationSeed;

    public ShapleyInconsistencyScorer() {
        this(Mode.EXACT, DEFAULT_APPROXIMATION_SAMPLES, DEFAULT_APPROXIMATION_SEED);
    }

    public ShapleyInconsistencyScorer(Mode mode, int approximationSamples, long approximationSeed) {
        if (mode == null) {
            throw new IllegalArgumentException("Mode must not be null.");
        }
        if (approximationSamples <= 0) {
            throw new IllegalArgumentException("Approximation samples must be positive: " + approximationSamples);
        }

        this.drasticCache = new HashMap<>();
        this.factorialCache = new ArrayList<>();
        this.factorialCache.add(1.0d);
        this.mode = mode;
        this.approximationSamples = approximationSamples;
        this.approximationSeed = approximationSeed;
    }

    public double shapleyInconsistencyValue(Set<OWLAxiom> axioms, OWLAxiom axiom) {
        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.add(axiom);

        if (mode == Mode.APPROXIMATE) {
            return approximateShapleyInconsistencyValue(universe, axiom);
        }
        return exactShapleyInconsistencyValue(universe, axiom);
    }

    private double exactShapleyInconsistencyValue(Set<OWLAxiom> universe, OWLAxiom axiom) {
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
        return total[0] / factorial(n);
    }

    private double approximateShapleyInconsistencyValue(Set<OWLAxiom> universe, OWLAxiom axiom) {
        // Sort once so seeded sampling is deterministic across JVM runs.
        List<OWLAxiom> permutation = universe.stream().sorted().collect(Collectors.toCollection(ArrayList::new));
        double totalMarginal = 0.0d;
        Random random = new Random(seedFor(universe, axiom));

        for (int i = 0; i < approximationSamples; i++) {
            Collections.shuffle(permutation, random);

            Set<OWLAxiom> prefix = new HashSet<>();
            for (OWLAxiom current : permutation) {
                if (current.equals(axiom)) {
                    Set<OWLAxiom> prefixWithAxiom = new HashSet<>(prefix);
                    prefixWithAxiom.add(axiom);
                    totalMarginal += drasticInconsistencyValue(prefixWithAxiom)
                            - drasticInconsistencyValue(prefix);
                    break;
                }
                prefix.add(current);
            }
        }

        return totalMarginal / approximationSamples;
    }

    private long seedFor(Set<OWLAxiom> universe, OWLAxiom axiom) {
        long seed = approximationSeed;
        seed = 31L * seed + canonicalize(universe).hashCode();
        seed = 31L * seed + axiom.hashCode();
        return seed;
    }

    private static String canonicalize(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().map(Object::toString).collect(Collectors.joining("|"));
    }

    private int drasticInconsistencyValue(Set<OWLAxiom> subset) {
        Set<OWLAxiom> key = Set.copyOf(subset);
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

