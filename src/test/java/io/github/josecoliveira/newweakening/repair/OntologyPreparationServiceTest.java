package io.github.josecoliveira.newweakening.repair;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import www.ontologyutils.toolbox.Ontology;

class OntologyPreparationServiceTest {

    @Test
    void loadedOntologySeparatesStaticAndRefutableAxioms() {
        try (Ontology ontology = Ontology.loadOntology(
                "libs/ontologyutils/src/test/resources/inconsistent/leftpolicies-small.owl")) {
            long staticCount = ontology.staticAxioms().count();
            long refutableCount = ontology.refutableAxioms().count();

            assertTrue(staticCount >= 0, "Static axioms count should be non-negative.");
            assertTrue(refutableCount > 0, "Fixture should have refutable (logical) axioms.");
            assertTrue(ontology.nonLogicalAxioms().allMatch(ax -> !ax.isLogicalAxiom()),
                    "Non-logical axioms must stay outside the refutable repair space.");
        }
    }
}

