package io.github.josecoliveira.newweakening.repair;

import org.semanticweb.owlapi.dlsyntax.renderer.DLSyntaxObjectRenderer;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;

/**
 * Renders OWL objects in DL / ALC Unicode notation (⊑, ⊓, ⊔, ¬, ∃, ∀, ⊤, ⊥, …)
 * using OWLAPI's built-in {@link DLSyntaxObjectRenderer} with local (short) names.
 */
public final class AlcPrinter {

    private AlcPrinter() {}

    private static final DLSyntaxObjectRenderer RENDERER = new DLSyntaxObjectRenderer();

    static {
        RENDERER.setShortFormProvider(new SimpleShortFormProvider());
    }

    public static String print(OWLObject object) {
        return RENDERER.render(object);
    }
}
