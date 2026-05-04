package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.toolbox.Ontology;

class OntologyInteractiveShapleyWeakeningTest {

    @Test
    void alwaysOffersChoiceForBadAxiomEvenWithSingleCandidate() {
        try (Ontology ontology = Ontology
                .loadOntology("libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            OWLAxiom axiom = ontology.refutableAxioms().findFirst().orElseThrow();
            AtomicInteger calls = new AtomicInteger();

            var repair = new ExposedInteractive((prompt, candidates) -> {
                calls.incrementAndGet();
                return candidates.get(0).getKey();
            });

            OWLAxiom selected = repair.selectBad(Map.of(axiom, 0.42d));
            assertEquals(1, calls.get());
            assertEquals(axiom, selected);
        }
    }

    @Test
    void alwaysOffersChoiceForWeakeningEvenWithSingleCandidate() {
        try (Ontology ontology = Ontology
                .loadOntology("libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            List<OWLAxiom> axioms = ontology.refutableAxioms().limit(2).toList();
            OWLAxiom badAxiom = axioms.get(0);
            OWLAxiom weakening = axioms.get(1);
            AtomicInteger calls = new AtomicInteger();

            var repair = new ExposedInteractive((prompt, candidates) -> {
                calls.incrementAndGet();
                return candidates.get(0).getKey();
            });

            OWLAxiom selected = repair.selectWeak(Map.of(weakening, 0.11d), badAxiom);
            assertEquals(1, calls.get());
            assertEquals(weakening, selected);
        }
    }

    private static final class ExposedInteractive extends OntologyInteractiveShapleyWeakening {
        ExposedInteractive(AxiomChoiceStrategy choiceStrategy) {
            super(Ontology::isConsistent, ShapleyInconsistencyScorer.Mode.EXACT, 128, 13L,
                    RefOntologyStrategy.ONE_MCS, BadAxiomStrategy.IN_SOME_MUS,
                    OntologyBestShapleyWeakening.TROQUARD2018_FLAGS, false, choiceStrategy);
        }

        OWLAxiom selectBad(Map<OWLAxiom, Double> candidates) {
            return selectBadAxiom(candidates);
        }

        OWLAxiom selectWeak(Map<OWLAxiom, Double> candidates, OWLAxiom badAxiom) {
            return selectWeakening(candidates, badAxiom);
        }
    }
}

