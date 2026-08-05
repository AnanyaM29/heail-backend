package com.example.heail_backend.util;

import java.util.Random;

/**
 * Deterministically permutes a question's A/B/C/D option slots based on the
 * question's ID, so the same question always displays its options in the
 * same shuffled order — but that order isn't the authored A/B/C/D order the
 * question bank spreadsheet was written in. Without this, whichever option
 * was written first (in practice, usually the highest-scoring/"best" one)
 * shows up in slot A every single time, and a test-taker quickly learns to
 * just click the first option without reading any of them.
 *
 * Stateless and reproducible from the questionId alone — no per-session
 * storage needed. Used both when building the QuestionDto shown to the
 * client (to decide what text goes in slot A/B/C/D) and when scoring an
 * answer (to translate the displayed letter the client selected back to the
 * original A/B/C/D letter that scoreFor()/the DB row actually means).
 */
public final class OptionOrder {
    private OptionOrder() {}

    /** order[i] = the ORIGINAL letter (A/B/C/D) shown in display slot i (0=A, 1=B, 2=C, 3=D). */
    public static char[] displayOrder(String questionId) {
        char[] order = {'A', 'B', 'C', 'D'};
        Random r = new Random(questionId.hashCode());
        for (int i = order.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            char tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        return order;
    }

    /** Given the letter the client displayed/selected (e.g. 'B'), returns the original A/B/C/D letter it represents. */
    public static char toOriginal(String questionId, char displayedLetter) {
        char[] order = displayOrder(questionId);
        int slot = displayedLetter - 'A';
        if (slot < 0 || slot >= order.length)
            throw new IllegalArgumentException("Invalid option: " + displayedLetter);
        return order[slot];
    }
}
