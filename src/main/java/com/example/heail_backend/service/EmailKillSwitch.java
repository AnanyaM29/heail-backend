package com.example.heail_backend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TEMPORARY global kill-switch for outbound email. Every EmailService.send*
 * method checks this before sending; when disabled, sends are skipped and
 * logged instead of dispatched. Backed by an in-memory flag only — resets to
 * enabled on every app restart, and does not persist across instances if the
 * app ever runs with more than one replica.
 *
 * TODO: remove once no longer needed (see AdminEmailController).
 */
@Component
public class EmailKillSwitch {

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
