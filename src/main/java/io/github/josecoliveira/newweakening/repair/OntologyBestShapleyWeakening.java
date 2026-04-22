package io.github.josecoliveira.newweakening.repair;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWeakening;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

/**
 * Repairs an ontology by repeatedly weakening the axiom with the highest
 * Shapley inconsistency value, while selecting the weakest replacement by the
 * lowest Shapley inconsistency value.
 */
public class OntologyBestShapleyWeakening extends OntologyRepairWeakening {

    private final ShapleyInconsistencyScorer scorer;

    public static final int TROQUARD2018_FLAGS = AxiomWeakener.FLAG_SROIQ_STRICT
            | AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT
            | AxiomWeakener.FLAG_RIA_ONLY_SIMPLE
            | AxiomWeakener.FLAG_NNF_STRICT
            | AxiomWeakener.FLAG_ALC_STRICT
            | AxiomWeakener.FLAG_NO_ROLE_REFINEMENT
            | AxiomWeakener.FLAG_OWL2_SET_OPERANDS;

    public OntologyBestShapleyWeakening() {
        this(ShapleyInconsistencyScorer.Mode.EXACT, 4096, 13L);
    }

    public OntologyBestShapleyWeakening(ShapleyInconsistencyScorer.Mode mode, int approximationSamples,
            long approximationSeed) {
        this(Ontology::isConsistent, mode, approximationSamples, approximationSeed,
                RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_SOME_MUS, TROQUARD2018_FLAGS, false);
    }

    public OntologyBestShapleyWeakening(ShapleyInconsistencyScorer.Mode mode, int approximationSamples,
            long approximationSeed, int weakeningFlags) {
        this(Ontology::isConsistent, mode, approximationSamples, approximationSeed,
                RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_SOME_MUS, weakeningFlags, false);
    }

    public OntologyBestShapleyWeakening(Predicate<Ontology> isRepaired, ShapleyInconsistencyScorer.Mode mode,
            int approximationSamples, long approximationSeed, RefOntologyStrategy refOntologyStrategy,
            BadAxiomStrategy badAxiomStrategy, int weakeningFlags, boolean enhanceRef) {
        super(isRepaired, refOntologyStrategy, badAxiomStrategy, weakeningFlags, enhanceRef);
        this.scorer = new ShapleyInconsistencyScorer(mode, approximationSamples, approximationSeed);
    }

    @Override
    public void repair(Ontology ontology) {
        infoMessage(formatOntologyState("Initial ontology state:", ontology));

        var refAxioms = Utils.randomChoice(getRefAxioms(ontology));
        infoMessage("Selected a reference ontology with " + refAxioms.size() + " axioms.");
        infoMessage(formatAxiomSet("Reference ontology:", refAxioms));
        if (enhanceRef) {
            ontology.addStaticAxioms(refAxioms);
        }

        try (var refOntology = ontology.cloneWithRefutable(refAxioms).withSeparateCache()) {
            var weakener = getWeakener(refOntology, ontology);
            while (!isRepaired(ontology)) {
                var currentAxioms = ontology.refutableAxioms().collect(Collectors.toSet());
                var badAxiomScores = computeBadAxiomScores(findBadAxiomCandidates(ontology), currentAxioms);
                infoMessage("Found " + badAxiomScores.size() + " possible bad axioms.");
                infoMessage(formatShapleyTable("Shapley values for possible bad axioms:",
                        badAxiomScores.entrySet().stream(), false));
                var badAxiom = selectBadAxiom(badAxiomScores);
                var weakerAxioms = collectWeakeningCandidates(badAxiom, weakener);
                while (weakerAxioms.isEmpty()) {
                    badAxiomScores.remove(badAxiom);
                    if (badAxiomScores.isEmpty()) {
                        throw new IllegalStateException("Could not find a weakenable bad axiom in ontology.");
                    }
                    badAxiom = selectBadAxiom(badAxiomScores);
                    weakerAxioms = collectWeakeningCandidates(badAxiom, weakener);
                }
                infoMessage("Selected the bad axiom " + renderAxiom(badAxiom) + ".");

                var weakerAxiomScores = scoreWeakeningCandidates(weakerAxioms, currentAxioms, badAxiom);
                infoMessage("Found " + weakerAxioms.size() + " weaker axioms.");
                infoMessage(formatShapleyTable(
                        "Candidate weakenings and Shapley values for " + renderAxiom(badAxiom) + ":",
                        weakerAxiomScores.entrySet().stream(), true));
                var weakerAxiom = selectWeakening(weakerAxiomScores, badAxiom);
                infoMessage("Selected the weaker axiom " + renderAxiom(weakerAxiom) + ".");

                ontology.replaceAxiom(badAxiom, weakerAxiom);
                infoMessage(formatOntologyState("Ontology state after weakening:", ontology));
            }
        }

        infoMessage(formatOntologyState("Final ontology state after repair:", ontology));
    }

    private List<OWLAxiom> findBadAxiomCandidates(Ontology ontology) {
        var candidates = Utils.toList(findBadAxioms(ontology));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Could not find a bad axiom in ontology.");
        }
        return candidates;
    }

    private Set<OWLAxiom> collectWeakeningCandidates(OWLAxiom axiom, AxiomWeakener weakener) {
        try {
            var weakenings = weakener.weakerAxioms(axiom).collect(Collectors.toSet());
            weakenings.remove(axiom);
            return weakenings;
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    private Map<OWLAxiom, Double> scoreWeakeningCandidates(Set<OWLAxiom> weakenings, Set<OWLAxiom> currentAxioms,
            OWLAxiom badAxiom) {
        if (weakenings == null || weakenings.isEmpty()) {
            throw new IllegalStateException("No weakening found for axiom: " + badAxiom);
        }
        return weakenings.stream()
                .collect(Collectors.toMap(ax -> ax, ax -> scorer.shapleyInconsistencyValue(currentAxioms, ax)));
    }

    private OWLAxiom selectWeakening(Map<OWLAxiom, Double> weakerAxiomScores, OWLAxiom badAxiom) {
        return weakerAxiomScores.entrySet().stream()
                .min(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(e -> renderAxiom(e.getKey())))
                .orElseThrow(() -> new IllegalStateException("Could not select a weakening for axiom: " + badAxiom))
                .getKey();
    }

    private Map<OWLAxiom, Double> computeBadAxiomScores(Iterable<OWLAxiom> candidates, Set<OWLAxiom> axioms) {
        Stream<OWLAxiom> stream = candidates instanceof List<?> list ? list.stream().map(OWLAxiom.class::cast)
                : StreamSupport.stream(candidates.spliterator(), false).map(OWLAxiom.class::cast);
        return stream
                .collect(Collectors.toMap(ax -> ax, ax -> scorer.shapleyInconsistencyValue(axioms, ax)));
    }

    private OWLAxiom selectBadAxiom(Map<OWLAxiom, Double> badAxiomScores) {
        return badAxiomScores.entrySet().stream()
                .max(Comparator.<Map.Entry<OWLAxiom, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing((e1, e2) -> renderAxiom(e2.getKey())
                                .compareTo(renderAxiom(e1.getKey()))))
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
            var axiomCmp = renderAxiom(e1.getKey()).compareTo(renderAxiom(e2.getKey()));
            return ascending ? axiomCmp : -axiomCmp;
        }).forEach(e -> sb.append(String.format("%-12.6f | %s%n", e.getValue(), renderAxiom(e.getKey()))));
        return sb.toString();
    }

    private String formatOntologyState(String title, Ontology ontology) {
        var sb = new StringBuilder();
        sb.append("\n").append(title).append("\n");
        sb.append(formatAxiomSet("Refutable axioms:", ontology.refutableAxioms().collect(Collectors.toSet())));
        return sb.toString();
    }

    private String formatAxiomSet(String title, Set<OWLAxiom> axioms) {
        var sb = new StringBuilder();
        var printableAxioms = axioms.stream()
                .map(OntologyBestShapleyWeakening::renderAxiom)
                .filter(rendered -> !rendered.isBlank())
                .sorted()
                .toList();

        sb.append(title).append("\n");
        sb.append("Total axioms: ").append(printableAxioms.size()).append("\n");
        printableAxioms.forEach(axiom -> sb.append("  ").append(axiom).append("\n"));
        return sb.toString();
    }

    private static String renderAxiom(OWLAxiom axiom) {
        var rendered = AlcPrinter.print(axiom);
        if (rendered == null || rendered.isBlank()) {
            return Utils.prettyPrintAxiom(axiom);
        }
        return rendered;
    }
}


