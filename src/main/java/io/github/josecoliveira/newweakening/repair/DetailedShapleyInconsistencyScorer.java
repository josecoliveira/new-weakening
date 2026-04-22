package io.github.josecoliveira.newweakening.repair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.toolbox.Utils;

/**
 * Computes Shapley inconsistency value with detailed breakdown by subset size.
 * Shows for each subset size how many subsets contribute and how much weight is added.
 */
public class DetailedShapleyInconsistencyScorer {

    /**
     * Records the contribution of subsets of a particular size.
     */
    public static class SizeContribution {
        public final int size;
        public final int count;
        public final long totalWeight;
        public final long totalMarginalContribution;
        public final double summationAddition;

        public SizeContribution(int size, int count, long totalWeight, long totalMarginalContribution,
                double summationAddition) {
            this.size = size;
            this.count = count;
            this.totalWeight = totalWeight;
            this.totalMarginalContribution = totalMarginalContribution;
            this.summationAddition = summationAddition;
        }
    }

    /**
     * Represents the complete breakdown of a Shapley calculation.
     */
    public static class DetailedShapleyResult {
        public final OWLAxiom axiom;
        public final int universeSize;
        public final List<SizeContribution> contributions;
        public final double absoluteValue; // before dividing by n!
        public final double relativeValue; // after dividing by n!
        public final long factorial_n;

        public DetailedShapleyResult(OWLAxiom axiom, int universeSize, List<SizeContribution> contributions,
                double absoluteValue, double relativeValue, long factorial_n) {
            this.axiom = axiom;
            this.universeSize = universeSize;
            this.contributions = contributions;
            this.absoluteValue = absoluteValue;
            this.relativeValue = relativeValue;
            this.factorial_n = factorial_n;
        }
    }

    private final Map<Set<OWLAxiom>, Integer> drasticCache;
    private final List<Long> factorialCache;

    public DetailedShapleyInconsistencyScorer() {
        this.drasticCache = new HashMap<>();
        this.factorialCache = new ArrayList<>();
        this.factorialCache.add(1L);
    }

    /**
     * Computes detailed Shapley inconsistency value with breakdown by subset size.
     */
    public DetailedShapleyResult detailedShapleyInconsistencyValue(Set<OWLAxiom> axioms, OWLAxiom axiom) {
        Set<OWLAxiom> universe = new HashSet<>(axioms);
        universe.add(axiom);

        int n = universe.size();

        List<OWLAxiom> baseCoalition = new ArrayList<>(universe);
        baseCoalition.remove(axiom);

        // Map from subset size to information about contributions
        Map<Integer, SizeContributionAccumulator> contributionsBySize = new HashMap<>();

        Map<Set<OWLAxiom>, Integer> inconsistencyWithAxiomBySubset = new HashMap<>();
        Set<Set<OWLAxiom>> visited = new HashSet<>();

        for (int subsetSize = 0; subsetSize <= baseCoalition.size(); subsetSize++) {
            generateSubsetsOfSize(baseCoalition, subsetSize, subset -> {
                Set<OWLAxiom> subsetKey = Set.copyOf(subset);
                if (visited.contains(subsetKey)) {
                    return;
                }

                Set<OWLAxiom> coalitionWithAxiom = new HashSet<>(subsetKey);
                coalitionWithAxiom.add(axiom);
                int inconsistencyWithAxiom = drasticInconsistencyValue(coalitionWithAxiom);

                inconsistencyWithAxiomBySubset.put(subsetKey, inconsistencyWithAxiom);
                visited.add(subsetKey);

                if (inconsistencyWithAxiom == 1) {
                    markSupersetsVisited(baseCoalition, subsetKey, visited, inconsistencyWithAxiomBySubset);
                }
            });
        }

        double totalSummation = 0.0d;
        for (Map.Entry<Set<OWLAxiom>, Integer> entry : inconsistencyWithAxiomBySubset.entrySet()) {
            Set<OWLAxiom> subset = entry.getKey();
            int c = subset.size() + 1;
            long weight = factorial(c - 1) * factorial(n - c);
            int marginalContribution = entry.getValue()
                    - drasticInconsistencyValue(subset);

            double contribution = weight * marginalContribution;
            totalSummation += contribution;

            SizeContributionAccumulator acc = contributionsBySize.computeIfAbsent(c,
                    k -> new SizeContributionAccumulator());
            if (marginalContribution != 0) {
                // Count only subontologies that actually contribute to the summation.
                acc.count++;
                acc.totalWeight += weight;
                acc.totalMarginalContribution += marginalContribution;
                acc.summationAddition += contribution;
            }
        }

        long factorial_n = factorial(n);
        double relativeValue = totalSummation / factorial_n;

        List<SizeContribution> contributions = new ArrayList<>();
        for (int size = 1; size <= n; size++) {
            SizeContributionAccumulator acc = contributionsBySize.get(size);
            if (acc != null) {
                contributions.add(new SizeContribution(size, acc.count, acc.totalWeight,
                        acc.totalMarginalContribution, acc.summationAddition));
            }
        }

        return new DetailedShapleyResult(axiom, n, contributions, totalSummation, relativeValue, factorial_n);
    }

    private long factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Factorial is undefined for negative numbers: " + n);
        }
        while (factorialCache.size() <= n) {
            int k = factorialCache.size();
            factorialCache.add(factorialCache.get(k - 1) * k);
        }
        return factorialCache.get(n);
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

    private static void markSupersetsVisited(List<OWLAxiom> universe,
            Set<OWLAxiom> subset,
            Set<Set<OWLAxiom>> visited,
            Map<Set<OWLAxiom>, Integer> inconsistencyWithAxiomBySubset) {
        List<OWLAxiom> remaining = new ArrayList<>();
        for (OWLAxiom candidate : universe) {
            if (!subset.contains(candidate)) {
                remaining.add(candidate);
            }
        }

        for (int size = 0; size <= remaining.size(); size++) {
            generateSubsetsOfSize(remaining, size, extension -> {
                Set<OWLAxiom> superset = new HashSet<>(subset);
                superset.addAll(extension);
                Set<OWLAxiom> key = Set.copyOf(superset);
                visited.add(key);
                inconsistencyWithAxiomBySubset.putIfAbsent(key, 1);
            });
        }
    }

    private static <T> void generateSubsetsOfSize(List<T> elements,
            int targetSize,
            java.util.function.Consumer<Set<T>> consumer) {
        generateSubsetsOfSize(elements, targetSize, 0, new HashSet<>(), consumer);
    }

    private static <T> void generateSubsetsOfSize(List<T> elements,
            int targetSize,
            int index,
            Set<T> current,
            java.util.function.Consumer<Set<T>> consumer) {
        if (current.size() == targetSize) {
            consumer.accept(new HashSet<>(current));
            return;
        }
        if (index >= elements.size()) {
            return;
        }
        if (current.size() + (elements.size() - index) < targetSize) {
            return;
        }

        T element = elements.get(index);
        current.add(element);
        generateSubsetsOfSize(elements, targetSize, index + 1, current, consumer);
        current.remove(element);
        generateSubsetsOfSize(elements, targetSize, index + 1, current, consumer);
    }

    static class SizeContributionAccumulator {
        int count = 0;
        long totalWeight = 0;
        long totalMarginalContribution = 0;
        double summationAddition = 0.0d;
    }
}

