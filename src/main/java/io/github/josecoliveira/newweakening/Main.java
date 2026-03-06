package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.OntologyPreparationService;
import io.github.josecoliveira.newweakening.repair.ShapleyWeakeningRepair;
import org.semanticweb.owlapi.model.OWLOntology;
import www.ontologyutils.toolbox.Utils;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: Main <ontology-file-path>");
            System.out.println("Example: Main libs/ontologyutils/resources/inconsistent-leftpolicies-small.owl");
            return;
        }

        String ontologyPath = args[0];
        OWLOntology ontology = Utils.newOntology(ontologyPath);
        OWLOntology prepared = OntologyPreparationService.prepareForWeakeningRepair(ontology);

        System.out.println("Loaded ontology: " + ontologyPath);
        System.out.println("Prepared axioms: " + prepared.getAxiomCount());
        System.out.println("Consistent before repair: " + Utils.isConsistent(prepared));

        ShapleyWeakeningRepair repair = new ShapleyWeakeningRepair(prepared, true);
        OWLOntology repaired = repair.repair();

        System.out.println("Consistent after repair: " + Utils.isConsistent(repaired));
        System.out.println("Repaired axioms: " + repaired.getAxiomCount());
    }
}
