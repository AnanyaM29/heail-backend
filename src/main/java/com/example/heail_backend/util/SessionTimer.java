package com.example.heail_backend.util;

import com.example.heail_backend.entity.AssessmentSession;

import java.time.LocalDateTime;

/**
 * Each pulse/leader-assessment session is time-boxed to 30 minutes
 * (AssessmentSession.prePersist sets the initial deadline). If a respondent
 * comes back after that deadline has already passed — connection dropped,
 * browser closed, whatever — they get one 5-minute grace extension from the
 * moment they resume, so a single interruption doesn't just lock them out.
 * A second time running out gets no further extension: resumeGrantsUsed
 * caps it at one grant per session, ever.
 */
public final class SessionTimer {
    private SessionTimer() {}

    private static final int GRACE_MINUTES = 5;

    /**
     * Call on every resume of an IN_PROGRESS session. Returns true if the
     * session's deadline was just extended (caller must persist it).
     */
    public static boolean applyResumeGrace(AssessmentSession session) {
        LocalDateTime deadline = session.getDeadlineAt();
        if (deadline == null) return false;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(deadline) && session.getResumeGrantsUsed() < 1) {
            session.setDeadlineAt(now.plusMinutes(GRACE_MINUTES));
            session.setResumeGrantsUsed(session.getResumeGrantsUsed() + 1);
            return true;
        }
        return false;
    }
}
