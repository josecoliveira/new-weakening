package io.github.josecoliveira.newweakening.repair;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.normalization.TBoxNormalization;
import www.ontologyutils.toolbox.Ontology;

/**
 * Prepares an ontology for the weakening repair using ontologyutils
 * normalization, then keeps only the supported logical fragment.
 */
public final class OntologyPreparationService {
    private OntologyPreparationService() {
    }

    public static Ontology prepareForWeakeningRepair(Ontology ontology) {
        try (Ontology normalized = ontology.clone()) {
            new TBoxNormalization().apply(normalized);

            Set<OWLAxiom> fixedAxioms = new HashSet<>();
            Set<OWLAxiom> mutableAxioms = new HashSet<>();

            normalized.nonLogicalAxioms().forEach(fixedAxioms::add);
            normalized.logicalAxioms()
                    .filter(ax -> ax.isOfType(AxiomType.SUBCLASS_OF) || ax.isOfType(AxiomType.CLASS_ASSERTION))
                    .forEach(mutableAxioms::add);

            return Ontology.withAxioms(fixedAxioms, mutableAxioms);
        }
    }
}
