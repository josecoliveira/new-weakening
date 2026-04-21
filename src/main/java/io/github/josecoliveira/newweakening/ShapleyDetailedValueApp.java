package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.AlcPrinter;
import io.github.josecoliveira.newweakening.repair.DetailedShapleyInconsistencyScorer;
import io.github.josecoliveira.newweakening.repair.OntologyPreparationService;
import www.ontologyutils.toolbox.Ontology;

import java.util.Comparator;
import java.util.List;

import org.semanticweb.owlapi.model.OWLAxiom;

/**
 * Shows detailed step-by-step Shapley inconsistency value calculations for each axiom,
 * grouped by subontology size. Helps verify hand calculations.
 *
 * Usage: java ShapleyDetailedValueApp <ontology-file-path> [--axiom <axiom-index>] [--verbose]
 *
 * If no axiom is specified, shows all axioms. If --axiom is specified, shows detailed
 * breakdown only for that axiom (0-indexed).
 */
public class ShapleyDetailedValueApp {

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.out.println(
                    "Example: ShapleyDetailedValueApp libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl");
            return;
        }

        CliOptions options;
        try {
            options = parseCliOptions(args);
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
            printUsage();
            return;
        }

        try (Ontology ontology = Ontology.loadOntology(options.ontologyPath());
                Ontology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology)) {

            long preparedAxiomCount = prepared.axioms().count();
            long refutableAxiomCount = prepared.refutableAxioms().count();

            System.out.println("Loaded ontology: " + options.ontologyPath());
            System.out.println("Prepared axioms (all): " + preparedAxiomCount);
            System.out.println("Prepared axioms (refutable): " + refutableAxiomCount);
            System.out.println("Consistent: " + prepared.isConsistent());
            System.out.println();

            // Use the same repair universe: only refutable axioms.
            List<OWLAxiom> axioms = prepared.refutableAxioms()
                    .sorted(Comparator.comparing(Object::toString))
                    .toList();

            DetailedShapleyInconsistencyScorer scorer = new DetailedShapleyInconsistencyScorer();

            if (options.axiomIndex() >= 0) {
                // Show detailed calculation for a single axiom
                if (options.axiomIndex() >= axioms.size()) {
                    System.out.println("Error: axiom index " + options.axiomIndex() + " out of range (0-"
                            + (axioms.size() - 1) + ")");
                    return;
                }
                showDetailedCalculation(scorer, axioms, options.axiomIndex());
            } else {
                // Show detailed calculation for all axioms
                showDetailedForAllAxioms(scorer, axioms, options.verbose());
            }

        }
    }

    private static void showDetailedCalculation(DetailedShapleyInconsistencyScorer scorer,
            List<OWLAxiom> axioms, int axiomIndex) {
        OWLAxiom axiom = axioms.get(axiomIndex);

        System.out.println("=== DETAILED SHAPLEY VALUE CALCULATION ===");
        System.out.println("Axiom #" + axiomIndex + ": " + renderAxiom(axiom));
        System.out.println();

        var result = scorer.detailedShapleyInconsistencyValue(new java.util.HashSet<>(axioms), axiom);

        printDetailedResult(result);
    }

    private static void printDetailedResult(DetailedShapleyInconsistencyScorer.DetailedShapleyResult result) {
        System.out.println("Universe size (n): " + result.universeSize);
        System.out.println("n! = " + result.factorial_n);
        System.out.println();

        System.out.printf("%-15s %-20s %-20s %-25s %-20s%n",
                "Subont. Size", "# Matching", "Matching Weight", "Total Marginal Contr.", "Summation Add.");
        System.out.println("-".repeat(100));

        for (var contrib : result.contributions) {
            System.out.printf("%-15d %-20d %-20d %-25d %-20.2f%n",
                    contrib.size,
                    contrib.count,
                    contrib.totalWeight,
                    contrib.totalMarginalContribution,
                    contrib.summationAddition);
        }

        System.out.println("-".repeat(100));
        System.out.println();

        System.out.printf("Total (before dividing by n!): %.4f%n", result.absoluteValue);
        System.out.printf("Shapley value (after dividing by n!): %.4f%n", result.relativeValue);
        System.out.printf("Calculation: %.4f / %d = %.4f%n", result.absoluteValue, result.factorial_n, result.relativeValue);
    }

    private static void showDetailedForAllAxioms(DetailedShapleyInconsistencyScorer scorer,
            List<OWLAxiom> axioms, boolean verbose) {
        System.out.println("=== DETAILED SHAPLEY VALUE CALCULATION FOR ALL AXIOMS ===");
        System.out.println();

        for (int i = 0; i < axioms.size(); i++) {
            OWLAxiom axiom = axioms.get(i);
            var result = scorer.detailedShapleyInconsistencyValue(new java.util.HashSet<>(axioms), axiom);

            System.out.println("Axiom #" + i + ": " + renderAxiom(axiom));
            if (verbose) {
                System.out.printf("Quick value: %.4f%n", result.relativeValue);
            }
            printDetailedResult(result);
            if (i < axioms.size() - 1) {
                System.out.println();
            }
        }
    }

    private static String renderAxiom(OWLAxiom axiom) {
        String rendered = AlcPrinter.print(axiom);
        if (rendered == null || rendered.isBlank()) {
            return axiom.toString();
        }
        return rendered;
    }

    private static void printUsage() {
        System.out.println("Usage: ShapleyDetailedValueApp <ontology-file-path> [--axiom <index>] [--verbose]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --axiom <index>   Show detailed breakdown for axiom at index (0-indexed)");
        System.out.println("  --verbose         Show additional details (e.g., quick value per axiom in all-axioms mode)");
    }

    static CliOptions parseCliOptions(String[] args) {
        String ontologyPath = args[0];
        int axiomIndex = -1;
        boolean verbose = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--verbose".equalsIgnoreCase(arg)) {
                verbose = true;
            } else if ("--axiom".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --axiom.");
                }
                try {
                    axiomIndex = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid integer for --axiom: " + args[i], ex);
                }
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return new CliOptions(ontologyPath, axiomIndex, verbose);
    }

    record CliOptions(
            String ontologyPath,
            int axiomIndex,
            boolean verbose) {
    }
}
