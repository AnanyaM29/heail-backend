package com.example.heail_backend.util;

import java.util.Random;
import java.util.UUID;

/**
 * Deterministically permutes a question's A/B/C/D option slots based on the
 * question's ID *and* the session it's being shown in, so a given question
 * displays a different option order in every session — but stays fixed
 * within one session (build the dto, score the answer, resume: all agree).
 * Without the session in the seed, every respondent across every session
 * would see the exact same order for a given question, and a test-taker
 * quickly learns "slot B is always right" from a friend who took it earlier.
 *
 * Stateless and reproducible from (questionId, sessionId) alone — no
 * separate per-session storage needed. Used both when building the
 * QuestionDto shown to the client (to decide what text goes in slot
 * A/B/C/D) and when scoring an answer (to translate the displayed letter
 * the client selected back to the original A/B/C/D letter that
 * scoreFor()/the DB row actually means).
 */
public final class OptionOrder {
    private OptionOrder() {}

    /** order[i] = the ORIGINAL letter (A/B/C/D) shown in display slot i (0=A, 1=B, 2=C, 3=D). */
    public static char[] displayOrder(String questionId, UUID sessionId) {
        char[] order = {'A', 'B', 'C', 'D'};
        long seed = (long) questionId.hashCode() * 31 + sessionId.hashCode();
        Random r = new Random(seed);
        for (int i = order.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            char tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        return order;
    }

    /** Given the letter the client displayed/selected (e.g. 'B'), returns the original A/B/C/D letter it represents. */
    public static char toOriginal(String questionId, UUID sessionId, char displayedLetter) {
        char[] order = displayOrder(questionId, sessionId);
        int slot = displayedLetter - 'A';
        if (slot < 0 || slot >= order.length)
            throw new IllegalArgumentException("Invalid option: " + displayedLetter);
        return order[slot];
    }
}
