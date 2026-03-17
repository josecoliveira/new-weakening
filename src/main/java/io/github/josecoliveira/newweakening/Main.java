package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.OntologyPreparationService;
import io.github.josecoliveira.newweakening.repair.ShapleyWeakeningRepair;
import www.ontologyutils.toolbox.Ontology;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: Main <ontology-file-path> [--verbose]");
            System.out.println("Example: Main libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl");
            return;
        }

        String ontologyPath = args[0];
        boolean verbose = args.length > 1 && "--verbose".equalsIgnoreCase(args[1]);
        try (Ontology ontology = Ontology.loadOntology(ontologyPath)) {
            Ontology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

            System.out.println("Loaded ontology: " + ontologyPath);
            System.out.println("Prepared axioms: " + prepared.axioms().count());
            System.out.println("Consistent before repair: " + prepared.isConsistent());

            ShapleyWeakeningRepair repair = new ShapleyWeakeningRepair();
            if (verbose) {
                repair.setInfoCallback(System.out::println);
            }
            repair.repair(prepared);

            System.out.println("Consistent after repair: " + prepared.isConsistent());
            System.out.println("Repaired axioms: " + prepared.axioms().count());
        }
    }
}
