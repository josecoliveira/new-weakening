package io.github.josecoliveira.newweakening;

import io.github.josecoliveira.newweakening.repair.AlcPrinter;
import io.github.josecoliveira.newweakening.repair.OntologyBestShapleyWeakening;
import www.ontologyutils.normalization.SroiqNormalization;
import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.OntologyRepairWeakening;
import www.ontologyutils.toolbox.Ontology;

/**
 * Compares one run of the Shapley-guided repair and one run of random weakening.
 */
public class RandomShapleyComparerApp {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println(
					"Usage: RandomShapleyComparerApp <ontology-file-path> [--mode exact|approximate] [--samples N] [--seed N] [--verbose]");
			return;
		}

		ShapleyRepairApp.CliOptions options;
		try {
			options = ShapleyRepairApp.parseCliOptions(args);
		} catch (IllegalArgumentException ex) {
			System.out.println(ex.getMessage());
			System.out.println(
					"Usage: RandomShapleyComparerApp <ontology-file-path> [--mode exact|approximate] [--samples N] [--seed N] [--verbose]");
			return;
		}

		try (Ontology ontology = Ontology.loadOntology(options.ontologyPath());
				Ontology shapleyInput = ontology.cloneWithSeparateCache();
				Ontology randomInput = ontology.cloneWithSeparateCache()) {

			if (requiresSroiqNormalization(options.weakeningFlags())) {
				new SroiqNormalization(true, false).apply(shapleyInput);
				new SroiqNormalization(true, false).apply(randomInput);
			}

			System.out.println("Loaded ontology: " + options.ontologyPath());
			System.out.println("Axioms (all): " + shapleyInput.axioms().count());
			System.out.println("Axioms (refutable): " + shapleyInput.refutableAxioms().count());
			if (requiresSroiqNormalization(options.weakeningFlags())) {
				System.out.println("Normalization: SROIQ applied before comparison");
			}
			System.out.println("Consistent before repair: " + shapleyInput.isConsistent());

			var shapleyRepair = new OntologyBestShapleyWeakening(
					options.coherence() ? Ontology::isCoherent : Ontology::isConsistent,
					options.mode(),
					options.approximationSamples(),
					options.approximationSeed(),
					options.refOntologyStrategy(),
					options.badAxiomStrategy(),
					options.weakeningFlags(),
					options.enhanceRef());
			var randomRepair = new OntologyRepairWeakening(
					options.coherence() ? Ontology::isCoherent : Ontology::isConsistent,
					options.refOntologyStrategy(),
					options.badAxiomStrategy(),
					options.weakeningFlags(),
					options.enhanceRef());

			if (options.verbose()) {
				shapleyRepair.setInfoCallback(message -> System.out.println("[shapley] " + message));
				randomRepair.setInfoCallback(message -> System.out.println("[random] " + message));
			}

			long shapleyStart = System.nanoTime();
			shapleyRepair.apply(shapleyInput);
			long shapleyDurationMs = (System.nanoTime() - shapleyStart) / 1_000_000;

			long randomStart = System.nanoTime();
			randomRepair.apply(randomInput);
			long randomDurationMs = (System.nanoTime() - randomStart) / 1_000_000;

			double shapleyVsRandomIic = shapleyInput.iicWithRespectTo(randomInput);
			double randomVsShapleyIic = randomInput.iicWithRespectTo(shapleyInput);

			System.out.println();
			System.out.println("=== Comparison ===");
			System.out.println("Shapley repair consistent: " + shapleyInput.isConsistent());
			System.out.println("Shapley repair axioms: " + shapleyInput.axioms().count());
			System.out.println("Shapley repair runtime (ms): " + shapleyDurationMs);
			System.out.println("Random weakening consistent: " + randomInput.isConsistent());
			System.out.println("Random weakening axioms: " + randomInput.axioms().count());
			System.out.println("Random weakening runtime (ms): " + randomDurationMs);
			System.out.println("IIC shapleyInput w.r.t randomInput: " + shapleyVsRandomIic);
			if (shapleyVsRandomIic > 0.5d) {
				System.out.println("Verdict: good grounds to claim Shapley is a better repair than random.");
			} else if (shapleyVsRandomIic < 0.5d) {
				System.out.println("Verdict: good grounds for the contrary claim (random better than Shapley).");
			} else {
				System.out.println("Verdict: inconclusive at 0.5; we cannot claim either repair is better.");
			}

			System.out.println();
			System.out.println(formatOntologyState("Final ontology (Shapley repair):", shapleyInput));
			System.out.println(formatOntologyState("Final ontology (Random weakening repair):", randomInput));

			System.out.println(randomVsShapleyIic);
		}
	}

	private static String formatOntologyState(String title, Ontology ontology) {
		var sb = new StringBuilder();
		sb.append(title).append(System.lineSeparator());
		var axioms = ontology.axioms()
				.map(AlcPrinter::print)
				.filter(rendered -> !rendered.isBlank())
				.sorted()
				.toList();
		sb.append("Total axioms: ").append(axioms.size()).append(System.lineSeparator());
		axioms.forEach(ax -> sb.append("  ").append(ax).append(System.lineSeparator()));
		return sb.toString();
	}

	private static boolean requiresSroiqNormalization(int weakeningFlags) {
		int normalizationSensitiveFlags = AxiomWeakener.FLAG_SROIQ_STRICT
				| AxiomWeakener.FLAG_NNF_STRICT
				| AxiomWeakener.FLAG_ALC_STRICT;
		return (weakeningFlags & normalizationSensitiveFlags) != 0;
	}
}



