"""New weakening/repair prototype"""

from itertools import chain, combinations
import numpy


Axiom = str  # Placeholder
Ontology = set[Axiom]  # An ontology is a set of axioms
Concept = str  # Placeholder
Individual = str  # Placeholder


def powerset(s: set) -> set[set]:
    """Returns an iterator over the powerset of a set s."""
    s: list = list(s)
    return (
        set(combo)
        for combo in chain.from_iterable(combinations(s, r) for r in range(len(s) + 1))
    )


def is_consistent(subset: Ontology) -> bool:
    """Returns True if the subset of axioms is consistent, and False otherwise."""
    pass


def maximally_consistent_subset(o: Ontology) -> Ontology:
    """Returns a maximally consistent subset of o."""
    return max(filter(is_consistent, powerset(o)), key=len)


def drastic_inconsistency_value(subset: Ontology) -> int:
    """Returns 0 if the subset is consistent, and 1 if it is inconsistent."""
    return 0 if is_consistent(subset) else 1


def shapley_inconsistency_value(K: Ontology, alpha: Axiom) -> int:
    """Calculates the Shapley inconsistency value of an axiom alpha with respect to a set of axioms K."""
    n: int = len(K)
    # return (1 / numpy.factorial(n)) * numpy.sum( # Normalization omitted
    return numpy.sum(
        (
            numpy.factorial(c - 1)
            * numpy.factorial(n - c)
            * (
                drastic_inconsistency_value(C)
                - drastic_inconsistency_value(C - {alpha})
            )
            for S in powerset(K - {alpha})
            for C in [S | {alpha}]
            for c in [len(C)]
        )
    )


def is_t_box_axiom(axiom: Axiom) -> bool:
    """Returns True if the axiom is a TBox axiom, and False otherwise."""
    pass


def axiom_left_concept(axiom: Axiom) -> Concept:
    """Returns the left concept of a TBox axiom."""
    pass


def axiom_right_concept(axiom: Axiom) -> Concept:
    """Returns the right concept of a TBox axiom."""
    pass


def is_a_box_axiom(axiom: Axiom) -> bool:
    """Returns True if the axiom is an ABox axiom, and False otherwise."""
    pass


def axiom_concept(axiom: Axiom) -> Concept:
    """Returns the concept of an ABox axiom."""
    pass


def axiom_individual(axiom: Axiom) -> Individual:
    """Returns the individual of an ABox axiom."""
    pass


def subsumes(specialization: Concept, generalization: Concept) -> Axiom:
    """Returns the axiom that specialization subsumes generalization."""
    pass


def assign(specialization: Concept, individual: Individual) -> Axiom:
    """Returns the axiom that assigns specialization to individual."""
    pass


def specializations(concept: Concept, o_ref: Ontology) -> set[Concept]:
    """Returns a set of specializations of a concept with respect to a reference ontology."""

    pass


def generalizations(concept: Concept, o_ref: Ontology) -> set[Concept]:
    """Returns a set of generalizations of a concept with respect to a reference ontology."""
    pass


def weakenings(axiom: Axiom, o_ref: Ontology) -> set[Axiom]:
    """Returns a set of weakenings of an axiom with respect to a reference ontology."""
    if is_t_box_axiom(axiom):
        left_concept: Concept = axiom_left_concept(axiom)
        right_concept: Concept = axiom_right_concept(axiom)
        return set(
            subsumes(specialization, generalization)
            for specialization in specializations(left_concept, o_ref)
            for generalization in generalizations(right_concept, o_ref)
        )
    elif is_a_box_axiom(axiom):
        concept: Concept = axiom_concept(axiom)
        individual: Individual = axiom_individual(axiom)
        return set(
            assign(specialization, individual)
            for specialization in specializations(concept, o_ref)
        )
    pass


def weaken_axiom(bad_axiom: Axiom, o_ref: Ontology) -> Axiom:
    """Returns the weakening of bad_axiom with the lowest Shapley inconsistency value with respect to o_ref."""
    weakenings_of_bad_axiom: set[Axiom] = weakenings(bad_axiom, o_ref)
    return min(
        weakenings_of_bad_axiom,
        key=lambda axiom: shapley_inconsistency_value(o_ref, axiom),
    )


def find_bad_axiom(o: Ontology) -> Axiom:
    """Finds the axiom in o with the highest Shapley inconsistency value."""
    return max(o, key=lambda axiom: shapley_inconsistency_value(o, axiom))


def repair_ontology_weaken(o: Ontology) -> Ontology:
    """Repairs an ontology by weakening axioms until it becomes consistent."""
    o_ref: Ontology = maximally_consistent_subset(o)
    while not is_consistent(o):
        bad_axiom: Axiom = find_bad_axiom(o)
        weaker_axiom: Axiom = weaken_axiom(bad_axiom, o_ref)
        o.remove(bad_axiom)
        o.add(weaker_axiom)
    return o
