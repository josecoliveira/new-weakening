package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.OntologyPreparationService;
import io.github.josecoliveira.newweakening.repair.OntologyBestShapleyWeakening;
import io.github.josecoliveira.newweakening.repair.ShapleyInconsistencyScorer;
import www.ontologyutils.toolbox.Ontology;

public class ShapleyRepairApp {
    private static final int DEFAULT_APPROXIMATION_SAMPLES = 4096;
    private static final long DEFAULT_APPROXIMATION_SEED = 13L;

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.out.println(
                    "Example: ShapleyRepairApp libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl");
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

            System.out.println("Loaded ontology: " + options.ontologyPath());
            System.out.println("Prepared axioms: " + prepared.axioms().count());
            System.out.println("Consistent before repair: " + prepared.isConsistent());

            OntologyBestShapleyWeakening repair = new OntologyBestShapleyWeakening(
                    options.mode(),
                    options.approximationSamples(),
                    options.approximationSeed());
            if (options.verbose()) {
                System.out.println("Shapley mode: " + options.mode().name().toLowerCase());
                if (options.mode() == ShapleyInconsistencyScorer.Mode.APPROXIMATE) {
                    System.out.println("Approximation samples: " + options.approximationSamples());
                    System.out.println("Approximation seed: " + options.approximationSeed());
                }
                repair.setInfoCallback(System.out::println);
            }
            repair.repair(prepared);

            System.out.println("Consistent after repair: " + prepared.isConsistent());
            System.out.println("Repaired axioms: " + prepared.axioms().count());
        }
    }

    private static void printUsage() {
        System.out.println(
                "Usage: ShapleyRepairApp <ontology-file-path> [--mode exact|approximate] [--samples N] [--seed N] [--verbose]");
    }

    static CliOptions parseCliOptions(String[] args) {
        String ontologyPath = args[0];
        boolean verbose = false;
        ShapleyInconsistencyScorer.Mode mode = ShapleyInconsistencyScorer.Mode.EXACT;
        int approximationSamples = DEFAULT_APPROXIMATION_SAMPLES;
        long approximationSeed = DEFAULT_APPROXIMATION_SEED;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--verbose".equalsIgnoreCase(arg)) {
                verbose = true;
            } else if ("--mode".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --mode.");
                }
                mode = parseMode(args[++i]);
            } else if ("--samples".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --samples.");
                }
                approximationSamples = parsePositiveInt(args[++i], "--samples");
            } else if ("--seed".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --seed.");
                }
                approximationSeed = parseLong(args[++i], "--seed");
            } else if ("exact".equalsIgnoreCase(arg) || "approximate".equalsIgnoreCase(arg)) {
                // Backward-compatible positional mode (e.g., path approximate --verbose).
                mode = parseMode(arg);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return new CliOptions(ontologyPath, verbose, mode, approximationSamples, approximationSeed);
    }

    private static ShapleyInconsistencyScorer.Mode parseMode(String value) {
        if ("exact".equalsIgnoreCase(value)) {
            return ShapleyInconsistencyScorer.Mode.EXACT;
        }
        if ("approximate".equalsIgnoreCase(value)) {
            return ShapleyInconsistencyScorer.Mode.APPROXIMATE;
        }
        throw new IllegalArgumentException("Unsupported mode: " + value + ". Expected exact or approximate.");
    }

    private static int parsePositiveInt(String value, String optionName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(optionName + " must be positive.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for " + optionName + ": " + value, ex);
        }
    }

    private static long parseLong(String value, String optionName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid long for " + optionName + ": " + value, ex);
        }
    }

    record CliOptions(
            String ontologyPath,
            boolean verbose,
            ShapleyInconsistencyScorer.Mode mode,
            int approximationSamples,
            long approximationSeed) {
    }
}


