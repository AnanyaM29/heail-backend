package com.example.heail_backend.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OptionOrderTest {

    private final UUID sessionId = UUID.randomUUID();

    @Test
    void fiveOptionOrderIsAPermutationOfAThroughE() {
        char[] order = OptionOrder.displayOrder("ITEM-MCQ-0TO2-REF-011", sessionId, 5);

        assertThat(order).hasSize(5);
        assertThat(order).containsExactlyInAnyOrder('A', 'B', 'C', 'D', 'E');
    }

    @Test
    void fiveOptionOrderIsStableWithinTheSameSessionAndQuestion() {
        char[] first = OptionOrder.displayOrder("ITEM-MCQ-0TO2-REF-011", sessionId, 5);
        char[] second = OptionOrder.displayOrder("ITEM-MCQ-0TO2-REF-011", sessionId, 5);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void fiveOptionOrderDiffersAcrossSessionsForTheSameQuestion() {
        char[] a = OptionOrder.displayOrder("ITEM-MCQ-0TO2-REF-011", UUID.randomUUID(), 5);
        char[] b = OptionOrder.displayOrder("ITEM-MCQ-0TO2-REF-011", UUID.randomUUID(), 5);

        // Not a hard guarantee (a shuffle *can* coincide by chance), but with 120
        // possible permutations of 5 elements a collision on a handful of runs
        // would be a red flag that the seed isn't actually varying by session.
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toOriginalRoundTripsEveryDisplayedSlotForFiveOptions() {
        String questionId = "ITEM-SJT-0TO2-REF-042";
        char[] order = OptionOrder.displayOrder(questionId, sessionId, 5);

        for (int slot = 0; slot < 5; slot++) {
            char displayedLetter = (char) ('A' + slot);
            char original = OptionOrder.toOriginal(questionId, sessionId, displayedLetter, 5);
            assertThat(original).isEqualTo(order[slot]);
        }
    }

    @Test
    void fourOptionBehaviourIsUnchangedByTheGeneralization() {
        String questionId = "P07";
        char[] legacyCall = OptionOrder.displayOrder(questionId, sessionId);
        char[] explicitFour = OptionOrder.displayOrder(questionId, sessionId, 4);

        assertThat(legacyCall).isEqualTo(explicitFour);
        assertThat(legacyCall).containsExactlyInAnyOrder('A', 'B', 'C', 'D');
    }

    @Test
    void toOriginalRejectsALetterOutsideTheOptionCount() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> OptionOrder.toOriginal("ITEM-MCQ-0TO2-REF-011", sessionId, 'F', 5));
    }
}
