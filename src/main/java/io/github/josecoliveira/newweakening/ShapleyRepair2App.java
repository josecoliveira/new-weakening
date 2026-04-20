package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.OntologyBestShapleyWeakening2;
import io.github.josecoliveira.newweakening.repair.OntologyPreparationService;
import io.github.josecoliveira.newweakening.repair.ShapleyInconsistencyScorer;
import www.ontologyutils.toolbox.Ontology;

public class ShapleyRepair2App {
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.out.println(
                    "Example: ShapleyRepair2App libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl");
            return;
        }

        ShapleyRepairApp.CliOptions options;
        try {
            options = ShapleyRepairApp.parseCliOptions(args);
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

            OntologyBestShapleyWeakening2 repair = new OntologyBestShapleyWeakening2(
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
                "Usage: ShapleyRepair2App <ontology-file-path> [--mode exact|approximate] [--samples N] [--seed N] [--verbose]");
    }
}


