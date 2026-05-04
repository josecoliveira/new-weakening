package io.github.josecoliveira.newweakening.repair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Interactive variant that asks the user to choose both the bad axiom and the
 * weakening at each iteration while keeping full Shapley diagnostics.
 */
public class OntologyInteractiveShapleyWeakening extends OntologyRepairWeakening {

    @FunctionalInterface
    public interface AxiomChoiceStrategy {
        OWLAxiom choose(String prompt, List<Map.Entry<OWLAxiom, Double>> orderedCandidates);
    }

    private final ShapleyInconsistencyScorer scorer;
    private final AxiomChoiceStrategy choiceStrategy;

    public OntologyInteractiveShapleyWeakening(Predicate<Ontology> isRepaired, ShapleyInconsistencyScorer.Mode mode,
            int approximationSamples, long approximationSeed, RefOntologyStrategy refOntologyStrategy,
            BadAxiomStrategy badAxiomStrategy, int weakeningFlags, boolean enhanceRef) {
        this(isRepaired, mode, approximationSamples, approximationSeed, refOntologyStrategy, badAxiomStrategy,
                weakeningFlags, enhanceRef, new ConsoleChoiceStrategy(System.in, System.out));
    }

    public OntologyInteractiveShapleyWeakening(Predicate<Ontology> isRepaired, ShapleyInconsistencyScorer.Mode mode,
            int approximationSamples, long approximationSeed, RefOntologyStrategy refOntologyStrategy,
            BadAxiomStrategy badAxiomStrategy, int weakeningFlags, boolean enhanceRef,
            AxiomChoiceStrategy choiceStrategy) {
        super(isRepaired, refOntologyStrategy, badAxiomStrategy, weakeningFlags, enhanceRef);
        this.scorer = new ShapleyInconsistencyScorer(mode, approximationSamples, approximationSeed);
        this.choiceStrategy = Objects.requireNonNull(choiceStrategy, "choiceStrategy");
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

    protected OWLAxiom selectBadAxiom(Map<OWLAxiom, Double> badAxiomScores) {
        var orderedCandidates = sortEntriesByScore(badAxiomScores, false);
        return chooseFromCandidates("Select bad axiom to weaken", orderedCandidates);
    }

    protected OWLAxiom selectWeakening(Map<OWLAxiom, Double> weakerAxiomScores, OWLAxiom badAxiom) {
        var orderedCandidates = sortEntriesByScore(weakerAxiomScores, true);
        return chooseFromCandidates("Select weakening for " + renderAxiom(badAxiom), orderedCandidates);
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

    private Map<OWLAxiom, Double> computeBadAxiomScores(Iterable<OWLAxiom> candidates, Set<OWLAxiom> axioms) {
        Stream<OWLAxiom> stream = candidates instanceof List<?> list ? list.stream().map(OWLAxiom.class::cast)
                : StreamSupport.stream(candidates.spliterator(), false).map(OWLAxiom.class::cast);
        return stream
                .collect(Collectors.toMap(ax -> ax, ax -> scorer.shapleyInconsistencyValue(axioms, ax)));
    }

    private List<Map.Entry<OWLAxiom, Double>> sortEntriesByScore(Map<OWLAxiom, Double> scores, boolean ascending) {
        Comparator<Map.Entry<OWLAxiom, Double>> comparator = Comparator.comparingDouble(Map.Entry::getValue);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        Comparator<String> axiomComparator = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        comparator = comparator.thenComparing(e -> renderAxiom(e.getKey()), axiomComparator);
        return scores.entrySet().stream().sorted(comparator).toList();
    }

    private String formatShapleyTable(String title, Stream<Map.Entry<OWLAxiom, Double>> entries, boolean ascending) {
        var sb = new StringBuilder();
        sb.append("\n").append(title).append("\n");
        sb.append(String.format("%-12s | %s%n", "Shapley", "Axiom"));
        sb.append(String.format("%s%n", "--------------|----------------------------------------------"));

        var sorted = sortEntriesByScore(entries.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                ascending);
        sorted.forEach(e -> sb.append(String.format("%-12.6f | %s%n", e.getValue(), renderAxiom(e.getKey()))));
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
                .map(OntologyInteractiveShapleyWeakening::renderAxiom)
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

    private OWLAxiom chooseFromCandidates(String prompt, List<Map.Entry<OWLAxiom, Double>> orderedCandidates) {
        if (orderedCandidates.isEmpty()) {
            throw new IllegalStateException("No candidates available for selection.");
        }
        OWLAxiom chosen = choiceStrategy.choose(prompt, orderedCandidates);
        if (chosen == null) {
            throw new IllegalStateException("Selection strategy returned null for prompt: " + prompt);
        }
        boolean knownCandidate = orderedCandidates.stream().anyMatch(entry -> entry.getKey().equals(chosen));
        if (!knownCandidate) {
            throw new IllegalStateException("Selection strategy returned an axiom outside the offered candidates.");
        }
        return chosen;
    }

    private static final class ConsoleChoiceStrategy implements AxiomChoiceStrategy {
        private final BufferedReader reader;
        private final PrintStream out;

        ConsoleChoiceStrategy(InputStream input, PrintStream out) {
            this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            this.out = out;
        }

        @Override
        public OWLAxiom choose(String prompt, List<Map.Entry<OWLAxiom, Double>> orderedCandidates) {
            out.println();
            out.println(prompt + ":");
            for (int i = 0; i < orderedCandidates.size(); i++) {
                var candidate = orderedCandidates.get(i);
                out.printf("  [%d] %.6f | %s%n", i + 1, candidate.getValue(), renderAxiom(candidate.getKey()));
            }

            while (true) {
                out.print("Enter choice [1-" + orderedCandidates.size() + "]: ");
                String input;
                try {
                    input = reader.readLine();
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to read interactive selection.", ex);
                }
                if (input == null) {
                    throw new IllegalStateException("Interactive input stream was closed.");
                }
                String trimmed = input.trim();
                try {
                    int selectedIndex = Integer.parseInt(trimmed);
                    if (selectedIndex < 1 || selectedIndex > orderedCandidates.size()) {
                        out.println("Invalid selection. Please choose a number in range.");
                        continue;
                    }
                    return orderedCandidates.get(selectedIndex - 1).getKey();
                } catch (NumberFormatException ex) {
                    out.println("Invalid selection. Please enter a number.");
                }
            }
        }
    }
}


