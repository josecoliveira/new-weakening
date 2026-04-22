package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.OntologyBestShapleyWeakening;
import io.github.josecoliveira.newweakening.repair.ShapleyInconsistencyScorer;
import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
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
                Ontology working = ontology.cloneWithSeparateCache()) {

            if (requiresSroiqNormalization(options.weakeningFlags())) {
                new SroiqNormalization(true, false).apply(working);
            }

            System.out.println("Loaded ontology: " + options.ontologyPath());
            System.out.println("Axioms (all): " + working.axioms().count());
            System.out.println("Axioms (static): " + working.staticAxioms().count());
            System.out.println("Axioms (refutable): " + working.refutableAxioms().count());
            if (requiresSroiqNormalization(options.weakeningFlags())) {
                System.out.println("Normalization: SROIQ applied before repair");
            }
            System.out.println("Consistent before repair: " + working.isConsistent());

            OntologyBestShapleyWeakening repair = new OntologyBestShapleyWeakening(
                    options.coherence() ? Ontology::isCoherent : Ontology::isConsistent,
                    options.mode(),
                    options.approximationSamples(),
                    options.approximationSeed(),
                    options.refOntologyStrategy(),
                    options.badAxiomStrategy(),
                    options.weakeningFlags(),
                    options.enhanceRef());
            if (options.verbose()) {
                System.out.println("Shapley mode: " + options.mode().name().toLowerCase());
                if (options.mode() == ShapleyInconsistencyScorer.Mode.APPROXIMATE) {
                    System.out.println("Approximation samples: " + options.approximationSamples());
                    System.out.println("Approximation seed: " + options.approximationSeed());
                }
                repair.setInfoCallback(System.out::println);
            }
            repair.apply(working);

            System.out.println("Consistent after repair: " + working.isConsistent());
            System.out.println("Repaired axioms: " + working.axioms().count());
        }
    }

    private static void printUsage() {
        System.out.println(
                "Usage: ShapleyRepairApp <ontology-file-path> [--preset troquard2018|confalonieri2020|bernard2023]"
                        + " [--mode exact|approximate] [--samples N] [--seed N] [--coherence] [--fast]"
                        + " [--ref-ontology intersect|intersect-of-some|largest|any|random|random-of-some]"
                        + " [--bad-axiom one-mus|some-mus|most-mus|least-mcs|largest-mcs|one-mcs|some-mcs|random]"
                        + " [--strict-nnf] [--strict-alc] [--strict-sroiq] [--strict-simple-roles]"
                        + " [--uncached] [--basic-cache] [--strict-owl2] [--simple-ria-weakening]"
                        + " [--no-role-refinement] [--enhance-ref] [--verbose]");
    }

    static CliOptions parseCliOptions(String[] args) {
        String ontologyPath = args[0];
        boolean verbose = false;
        boolean coherence = false;
        boolean enhanceRef = false;
        ShapleyInconsistencyScorer.Mode mode = ShapleyInconsistencyScorer.Mode.EXACT;
        int approximationSamples = DEFAULT_APPROXIMATION_SAMPLES;
        long approximationSeed = DEFAULT_APPROXIMATION_SEED;
        RefOntologyStrategy refOntologyStrategy = Preset.TROQUARD_2018.refOntologyStrategy;
        BadAxiomStrategy badAxiomStrategy = Preset.TROQUARD_2018.badAxiomStrategy;
        int weakeningFlags = Preset.TROQUARD_2018.weakeningFlags;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--verbose".equalsIgnoreCase(arg)) {
                verbose = true;
            } else if ("--coherence".equalsIgnoreCase(arg)) {
                coherence = true;
            } else if ("--fast".equalsIgnoreCase(arg)) {
                refOntologyStrategy = RefOntologyStrategy.ONE_MCS;
                badAxiomStrategy = BadAxiomStrategy.IN_ONE_MUS;
            } else if ("--enhance-ref".equalsIgnoreCase(arg)) {
                enhanceRef = true;
            } else if ("--mode".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --mode.");
                }
                mode = parseMode(args[++i]);
            } else if ("--preset".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --preset.");
                }
                var preset = parsePreset(args[++i]);
                refOntologyStrategy = preset.refOntologyStrategy;
                badAxiomStrategy = preset.badAxiomStrategy;
                weakeningFlags = preset.weakeningFlags;
            } else if ("--ref-ontology".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --ref-ontology.");
                }
                refOntologyStrategy = parseRefOntologyStrategy(args[++i]);
            } else if ("--bad-axiom".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --bad-axiom.");
                }
                badAxiomStrategy = parseBadAxiomStrategy(args[++i]);
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
            } else if ("--strict-nnf".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_NNF_STRICT;
            } else if ("--strict-alc".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_ALC_STRICT;
            } else if ("--strict-sroiq".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_SROIQ_STRICT;
            } else if ("--strict-simple-roles".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT;
            } else if ("--uncached".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_UNCACHED;
            } else if ("--basic-cache".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_BASIC_CACHED;
            } else if ("--strict-owl2".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_OWL2_SET_OPERANDS;
            } else if ("--simple-ria-weakening".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_RIA_ONLY_SIMPLE;
            } else if ("--no-role-refinement".equalsIgnoreCase(arg)) {
                weakeningFlags |= AxiomWeakener.FLAG_NO_ROLE_REFINEMENT;
            } else if ("exact".equalsIgnoreCase(arg) || "approximate".equalsIgnoreCase(arg)) {
                // Backward-compatible positional mode (e.g., path approximate --verbose).
                mode = parseMode(arg);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return new CliOptions(ontologyPath, verbose, coherence, enhanceRef, mode, approximationSamples,
                approximationSeed, refOntologyStrategy, badAxiomStrategy, weakeningFlags);
    }

    private static RefOntologyStrategy parseRefOntologyStrategy(String value) {
        return switch (value.toLowerCase()) {
            case "intersect" -> RefOntologyStrategy.INTERSECTION_OF_MCS;
            case "intersect-of-some" -> RefOntologyStrategy.INTERSECTION_OF_SOME_MCS;
            case "largest" -> RefOntologyStrategy.LARGEST_MCS;
            case "any" -> RefOntologyStrategy.ONE_MCS;
            case "random" -> RefOntologyStrategy.RANDOM_MCS;
            case "random-of-some" -> RefOntologyStrategy.SOME_MCS;
            default -> throw new IllegalArgumentException(
                    "Unsupported value for --ref-ontology: " + value);
        };
    }

    private static BadAxiomStrategy parseBadAxiomStrategy(String value) {
        return switch (value.toLowerCase()) {
            case "one-mus" -> BadAxiomStrategy.IN_ONE_MUS;
            case "some-mus" -> BadAxiomStrategy.IN_SOME_MUS;
            case "most-mus" -> BadAxiomStrategy.IN_MOST_MUS;
            case "least-mcs" -> BadAxiomStrategy.IN_LEAST_MCS;
            case "largest-mcs" -> BadAxiomStrategy.NOT_IN_LARGEST_MCS;
            case "one-mcs" -> BadAxiomStrategy.NOT_IN_ONE_MCS;
            case "some-mcs" -> BadAxiomStrategy.NOT_IN_SOME_MCS;
            case "random" -> BadAxiomStrategy.RANDOM;
            default -> throw new IllegalArgumentException(
                    "Unsupported value for --bad-axiom: " + value);
        };
    }

    private static Preset parsePreset(String value) {
        return switch (value.toLowerCase()) {
            case "troquard2018" -> Preset.TROQUARD_2018;
            case "confalonieri2020" -> Preset.CONFALONIERI_2020;
            case "bernard2023" -> Preset.BERNARD_2023;
            default -> throw new IllegalArgumentException(
                    "Unsupported preset: " + value
                            + ". Expected troquard2018, confalonieri2020, or bernard2023.");
        };
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

    private static boolean requiresSroiqNormalization(int weakeningFlags) {
        int normalizationSensitiveFlags = AxiomWeakener.FLAG_SROIQ_STRICT
                | AxiomWeakener.FLAG_NNF_STRICT
                | AxiomWeakener.FLAG_ALC_STRICT;
        return (weakeningFlags & normalizationSensitiveFlags) != 0;
    }

    record CliOptions(
            String ontologyPath,
            boolean verbose,
            boolean coherence,
            boolean enhanceRef,
            ShapleyInconsistencyScorer.Mode mode,
            int approximationSamples,
            long approximationSeed,
            RefOntologyStrategy refOntologyStrategy,
            BadAxiomStrategy badAxiomStrategy,
            int weakeningFlags) {
    }

    private enum Preset {
        TROQUARD_2018(
                RefOntologyStrategy.ONE_MCS,
                BadAxiomStrategy.IN_SOME_MUS,
                OntologyBestShapleyWeakening.TROQUARD2018_FLAGS),
        CONFALONIERI_2020(
                RefOntologyStrategy.ONE_MCS,
                BadAxiomStrategy.IN_SOME_MUS,
                AxiomWeakener.FLAG_SROIQ_STRICT
                        | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                        | AxiomWeakener.FLAG_NNF_STRICT
                        | AxiomWeakener.FLAG_OWL2_SET_OPERANDS),
        BERNARD_2023(
                RefOntologyStrategy.ONE_MCS,
                BadAxiomStrategy.IN_SOME_MUS,
                AxiomWeakener.FLAG_SROIQ_STRICT
                        | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
                        | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
                        | AxiomWeakener.FLAG_OWL2_SET_OPERANDS);

        private final RefOntologyStrategy refOntologyStrategy;
        private final BadAxiomStrategy badAxiomStrategy;
        private final int weakeningFlags;

        Preset(RefOntologyStrategy refOntologyStrategy, BadAxiomStrategy badAxiomStrategy, int weakeningFlags) {
            this.refOntologyStrategy = refOntologyStrategy;
            this.badAxiomStrategy = badAxiomStrategy;
            this.weakeningFlags = weakeningFlags;
        }
    }
}


