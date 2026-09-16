package io.github.lealoureiro.greekgods;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specification requirement: <em>Return records in ascending identifier order</em>.
 *
 * <p>Both tests deliberately disturb the physical row order before asserting, with
 * {@link AcceptanceTestBase#moveRowToEndOfPhysicalStorage(long)}. A freshly seeded
 * table returns rows in insertion order whether or not the query asks for an order,
 * so without that disturbance these assertions are satisfied by PostgreSQL's storage
 * layout rather than by the service's guarantee — the {@code ORDER BY} could be
 * deleted from the production query and both tests would still pass.
 *
 * <p>The names are also seeded in an order that is neither alphabetical nor reverse
 * alphabetical, so ordering by identifier and ordering by name disagree too.
 */
class CatalogueOrderingAT extends AcceptanceTestBase {

    @Test
    @DisplayName("Scenario: Records arrive in ascending identifier order")
    void recordsArriveInAscendingIdentifierOrder() {
        // WHEN an API consumer requests the Greek gods
        // and the catalogue holds more than one record
        long nyx = givenTheCatalogueHolds("Nyx");
        long eros = givenTheCatalogueHolds("Eros");
        long thanatos = givenTheCatalogueHolds("Thanatos");

        // Nyx holds the lowest identifier but now sits last in storage, so an
        // unordered query would return it last.
        moveRowToEndOfPhysicalStorage(nyx);

        var records = whenTheGreekGodsAreRequested();

        // THEN the records appear in ascending order of identifier
        assertThat(records).extracting(GreekGod::id).containsExactly(nyx, eros, thanatos);
        assertThat(records).extracting(GreekGod::id).isSorted();
        assertThat(records).extracting(GreekGod::name)
            .as("stored order would give Eros, Thanatos, Nyx; name order would give "
                + "Eros, Nyx, Thanatos")
            .containsExactly("Nyx", "Eros", "Thanatos");
    }

    @Test
    @DisplayName("Scenario: Repeating the request yields the same order")
    void repeatingTheRequestYieldsTheSameOrder() {
        long hypnos = givenTheCatalogueHolds("Hypnos");
        givenTheCatalogueHolds("Nemesis");
        givenTheCatalogueHolds("Iris");

        // WHEN an API consumer requests the Greek gods twice
        var first = whenTheGreekGodsAreRequested();

        // ...without the catalogue changing. Every identifier and every name is
        // exactly as it was; only the physical layout moved underneath. A consumer
        // cannot observe this, so it is not a change to the catalogue — but an
        // unordered query would answer the second request in a different sequence
        // than the first, which is precisely what this scenario forbids.
        moveRowToEndOfPhysicalStorage(hypnos);

        var second = whenTheGreekGodsAreRequested();

        // THEN both responses list the records in the same order
        assertThat(second).containsExactlyElementsOf(first);
        assertThat(second).extracting(GreekGod::id).isSorted();
    }
}
