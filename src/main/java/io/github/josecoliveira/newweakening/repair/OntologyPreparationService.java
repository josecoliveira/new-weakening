package io.github.josecoliveira.newweakening.repair;
import java.util.HashSet;
import java.util.Set;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import www.ontologyutils.normalization.NormalizationTools;
import www.ontologyutils.toolbox.Utils;
/**
 * Converts an ontology into the fragment expected by the weakening repair:
 * subclass axioms and class assertions.
 */
public final class OntologyPreparationService {
    private OntologyPreparationService() {
    }
    public static OWLOntology prepareForWeakeningRepair(OWLOntology ontology) {
        Set<OWLAxiom> preparedAxioms = new HashSet<>();
        // Keep annotations and declaration axioms untouched.
        ontology.axioms().filter(ax -> !ax.isLogicalAxiom()).forEach(preparedAxioms::add);
        // Keep class assertions from the ABox.
        ontology.aboxAxioms(Imports.EXCLUDED)
                .filter(ax -> ax.isOfType(AxiomType.CLASS_ASSERTION))
                .forEach(preparedAxioms::add);
        // Convert each TBox axiom into one or more subclass axioms.
        ontology.tboxAxioms(Imports.EXCLUDED)
                .forEach(ax -> preparedAxioms.addAll(NormalizationTools.asSubClassOfAxioms(ax)));
        return Utils.newOntology(preparedAxioms);
    }
}
