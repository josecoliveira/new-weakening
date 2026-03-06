package io.github.josecoliveira.newweakening.repair;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.MaximalConsistentSets;
import www.ontologyutils.toolbox.Utils;

/**
 * Repairs an ontology by repeatedly weakening the axiom with the highest
 * Shapley inconsistency value.
 */
public class ShapleyWeakeningRepair implements OntologyRepair {

    private final Set<OWLAxiom> fixedAxioms;
    private final Set<OWLAxiom> mutableAxioms;
    private final boolean verbose;
    private final ShapleyInconsistencyScorer scorer;

    public ShapleyWeakeningRepair(OWLOntology ontology) {
        this(ontology, false);
    }

    public ShapleyWeakeningRepair(OWLOntology ontology, boolean verbose) {
        this.verbose = verbose;
        this.scorer = new ShapleyInconsistencyScorer();
        this.fixedAxioms = ontology.axioms().filter(ax -> !ax.isLogicalAxiom()).collect(Collectors.toSet());
        this.mutableAxioms = ontology.logicalAxioms().filter(ShapleyWeakeningRepair::isSupportedRepairAxiom)
                .collect(Collectors.toSet());

        Set<OWLAxiom> unsupportedLogicalAxioms = ontology.logicalAxioms()
                .filter(ax -> !isSupportedRepairAxiom(ax))
                .collect(Collectors.toSet());
        if (!unsupportedLogicalAxioms.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported logical axioms for Shapley weakening repair: " + unsupportedLogicalAxioms);
        }
    }

    @Override
    public OWLOntology repair() {
        Set<OWLAxiom> currentAxioms = new HashSet<>(mutableAxioms);
        Set<OWLAxiom> referenceAxioms = maximallyConsistentSubset(currentAxioms);
        AxiomWeakener weakener = new AxiomWeakener(Utils.newOntology(referenceAxioms));
        try {
            while (!isConsistent(currentAxioms)) {
                OWLAxiom badAxiom = findBadAxiom(currentAxioms);
                OWLAxiom weakerAxiom = chooseWeakening(badAxiom, referenceAxioms, weakener);

                currentAxioms.remove(badAxiom);
                currentAxioms.add(weakerAxiom);

                log("- Weaken: \t " + Utils.prettyPrintAxiom(badAxiom) + "\n"
                        + "  Into:   \t " + Utils.prettyPrintAxiom(weakerAxiom) + "\n");
            }
        } finally {
            weakener.dispose();
        }

        Set<OWLAxiom> repaired = new HashSet<>(fixedAxioms);
        repaired.addAll(currentAxioms);
        return Utils.newOntology(repaired);
    }

    private OWLAxiom chooseWeakening(OWLAxiom badAxiom, Set<OWLAxiom> referenceAxioms, AxiomWeakener weakener) {
        Set<OWLAxiom> weakenings = new HashSet<>(weakener.getWeakerAxioms(badAxiom));
        weakenings.remove(badAxiom);
        if (weakenings.isEmpty()) {
            throw new IllegalStateException("No weakening found for axiom: " + badAxiom);
        }

        return weakenings.stream()
                .map(ax -> Map.entry(ax, scorer.shapleyInconsistencyValue(referenceAxioms, ax)))
                .min(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(e -> Utils.prettyPrintAxiom(e.getKey())))
                .orElseThrow(() -> new IllegalStateException("Could not select a weakening for axiom: " + badAxiom))
                .getKey();
    }

    private OWLAxiom findBadAxiom(Set<OWLAxiom> axioms) {
        return axioms.stream()
                .map(ax -> Map.entry(ax, scorer.shapleyInconsistencyValue(axioms, ax)))
                .max(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(e -> Utils.prettyPrintAxiom(e.getKey()), Comparator.reverseOrder()))
                .orElseThrow(() -> new IllegalStateException("Could not find a bad axiom in ontology."))
                .getKey();
    }

    private static Set<OWLAxiom> maximallyConsistentSubset(Set<OWLAxiom> axioms) {
        Set<Set<OWLAxiom>> mcss = MaximalConsistentSets.maximalConsistentSubsets(axioms);
        return mcss.stream()
                .max(Comparator.<Set<OWLAxiom>>comparingInt(Set::size)
                        .thenComparing(ShapleyWeakeningRepair::canonicalAxiomSet, Comparator.reverseOrder()))
                .orElseGet(HashSet::new);
    }

    private static String canonicalAxiomSet(Set<OWLAxiom> axioms) {
        return axioms.stream().map(Utils::prettyPrintAxiom).sorted().collect(Collectors.joining(" | "));
    }

    private static boolean isSupportedRepairAxiom(OWLAxiom axiom) {
        return axiom.isOfType(AxiomType.SUBCLASS_OF) || axiom.isOfType(AxiomType.CLASS_ASSERTION);
    }

    private boolean isConsistent(Set<OWLAxiom> currentAxioms) {
        Set<OWLAxiom> allAxioms = new HashSet<>(fixedAxioms);
        allAxioms.addAll(currentAxioms);
        return Utils.isConsistent(allAxioms);
    }

    private void log(String message) {
        if (verbose) {
            System.out.print(message);
        }
    }
}

