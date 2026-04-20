package io.github.josecoliveira.newweakening.repair;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepair;
import www.ontologyutils.toolbox.MaximalConsistentSubsets;
import www.ontologyutils.toolbox.Ontology;

/**
 * Repairs an ontology by repeatedly weakening the non-reference axiom with the
 * highest Shapley inconsistency value.
 */
public class OntologyBestShapleyWeakening extends OntologyRepair {

    private final ShapleyInconsistencyScorer scorer;

    public OntologyBestShapleyWeakening() {
        this(ShapleyInconsistencyScorer.Mode.EXACT, 4096, 13L);
    }

    public OntologyBestShapleyWeakening(ShapleyInconsistencyScorer.Mode mode, int approximationSamples,
            long approximationSeed) {
        super(Ontology::isConsistent);
        this.scorer = new ShapleyInconsistencyScorer(mode, approximationSamples, approximationSeed);
    }

    @Override
    public void repair(Ontology ontology) {
        var currentAxioms = ontology.refutableAxioms()
                .filter(OntologyBestShapleyWeakening::isSupportedRepairAxiom)
                .collect(Collectors.toSet());

        var unsupported = ontology.refutableAxioms()
                .filter(ax -> !isSupportedRepairAxiom(ax))
                .collect(Collectors.toSet());
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported logical axioms for Shapley weakening repair: " + unsupported);
        }

        infoMessage(printOntologyState("Initial ontology state:", ontology));

        var referenceAxioms = getRefAxioms(currentAxioms);
        infoMessage("Selected a reference ontology with " + referenceAxioms.size() + " axioms.");
        infoMessage(formatAxiomSet("Reference ontology:", referenceAxioms));
        try (var refOntology = Ontology.withAxioms(referenceAxioms)) {
            var weakener = new AxiomWeakener(refOntology);
            while (!isRepaired(ontology)) {
                var badAxiomScores = computeBadAxiomScores(currentAxioms, referenceAxioms);
                infoMessage("Found " + badAxiomScores.size() + " possible bad axioms.");
                infoMessage(formatShapleyTable("Shapley values for possible bad axioms:",
                        badAxiomScores.entrySet().stream(), false));
                var badAxiom = selectBadAxiom(badAxiomScores);
                infoMessage("Selected the bad axiom " + AlcPrinter.print(badAxiom) + ".");

                var weakerAxiomScores = computeWeakeningScores(badAxiom, referenceAxioms, weakener);
                infoMessage("Found " + weakerAxiomScores.size() + " weaker axioms.");
                infoMessage(formatShapleyTable(
                        "Candidate weakenings and Shapley values for " + AlcPrinter.print(badAxiom) + ":",
                        weakerAxiomScores.entrySet().stream(), true));
                var weakerAxiom = selectWeakening(weakerAxiomScores, badAxiom);
                infoMessage("Selected the weaker axiom " + AlcPrinter.print(weakerAxiom) + ".");

                ontology.replaceAxiom(badAxiom, weakerAxiom);
                currentAxioms.remove(badAxiom);
                currentAxioms.add(weakerAxiom);
                infoMessage(printOntologyState("Ontology state after weakening:", ontology));
            }
        }

        infoMessage(printOntologyState("Final ontology state after repair:", ontology));
    }

    private Set<OWLAxiom> getRefAxioms(Set<OWLAxiom> axioms) {
        return maximallyConsistentSubset(axioms);
    }

    private Map<OWLAxiom, Double> computeWeakeningScores(OWLAxiom badAxiom, Set<OWLAxiom> referenceAxioms,
            AxiomWeakener weakener) {
        var weakenings = weakener.weakerAxioms(badAxiom).collect(Collectors.toSet());
        weakenings.remove(badAxiom);
        if (weakenings.isEmpty()) {
            throw new IllegalStateException("No weakening found for axiom: " + badAxiom);
        }

        return weakenings.stream()
                .collect(Collectors.toMap(ax -> ax, ax -> scorer.shapleyInconsistencyValue(referenceAxioms, ax)));
    }

    private OWLAxiom selectWeakening(Map<OWLAxiom, Double> weakerAxiomScores, OWLAxiom badAxiom) {
        return weakerAxiomScores.entrySet().stream()
                .min(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .orElseThrow(() -> new IllegalStateException("Could not select a weakening for axiom: " + badAxiom))
                .getKey();
    }

    private Map<OWLAxiom, Double> computeBadAxiomScores(Set<OWLAxiom> axioms, Set<OWLAxiom> referenceAxioms) {
        var badAxiomCandidates = axioms.stream()
                .filter(ax -> !referenceAxioms.contains(ax))
                .collect(Collectors.toSet());
        if (badAxiomCandidates.isEmpty()) {
            throw new IllegalStateException("Could not find any non-reference axioms to weaken.");
        }

        return badAxiomCandidates.stream()
                .collect(Collectors.toMap(ax -> ax, ax -> scorer.shapleyInconsistencyValue(axioms, ax)));
    }

    private OWLAxiom selectBadAxiom(Map<OWLAxiom, Double> badAxiomScores) {
        return badAxiomScores.entrySet().stream()
                .max(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing((e1, e2) -> e2.getKey().compareTo(e1.getKey())))
                .orElseThrow(() -> new IllegalStateException("Could not find a bad axiom in ontology."))
                .getKey();
    }

    private String formatShapleyTable(String title, Stream<Map.Entry<OWLAxiom, Double>> entries, boolean ascending) {
        var sb = new StringBuilder();
        sb.append("\n").append(title).append("\n");
        sb.append(String.format("%-12s | %s%n", "Shapley", "Axiom"));
        sb.append(String.format("%s%n", "--------------|----------------------------------------------"));

        entries.sorted((e1, e2) -> {
            var cmp = Double.compare(e1.getValue(), e2.getValue());
            if (!ascending) {
                cmp = -cmp;
            }
            if (cmp != 0) {
                return cmp;
            }
            var axiomCmp = e1.getKey().compareTo(e2.getKey());
            return ascending ? axiomCmp : -axiomCmp;
        }).forEach(e -> sb.append(String.format("%-12.6f | %s%n", e.getValue(), AlcPrinter.print(e.getKey()))));
        return sb.toString();
    }

    private static Set<OWLAxiom> maximallyConsistentSubset(Set<OWLAxiom> axioms) {
        Set<Set<OWLAxiom>> mcss = MaximalConsistentSubsets.maximalConsistentSubsets(axioms);
        return mcss.stream()
                .max(Comparator.<Set<OWLAxiom>>comparingInt(Set::size)
                        .thenComparing(OntologyBestShapleyWeakening::canonicalAxiomSet, Comparator.reverseOrder()))
                .orElseGet(HashSet::new);
    }

    private static String canonicalAxiomSet(Set<OWLAxiom> axioms) {
        return axioms.stream().sorted().map(AlcPrinter::print).collect(Collectors.joining(" | "));
    }

    private static boolean isSupportedRepairAxiom(OWLAxiom axiom) {
        return axiom.isOfType(AxiomType.SUBCLASS_OF) || axiom.isOfType(AxiomType.CLASS_ASSERTION);
    }

    private String formatAxiomSet(String title, Set<OWLAxiom> axioms) {
        var sb = new StringBuilder();
        var printableAxioms = axioms.stream()
                .sorted()
                .map(AlcPrinter::print)
                .filter(rendered -> !rendered.isBlank())
                .collect(Collectors.toList());

        sb.append("\n").append(title).append("\n");
        sb.append("Total axioms: ").append(printableAxioms.size()).append("\n");

        printableAxioms.forEach(axiom -> sb.append("  ").append(axiom).append("\n"));
        return sb.toString();
    }

    private String printOntologyState(String title, Ontology ontology) {
        return formatAxiomSet(title, ontology.axioms().collect(Collectors.toSet()));
    }
}


