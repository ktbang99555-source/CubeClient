package com.cubeclient.launcher.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    // A record's generated toString would print the access token verbatim into any log line or
    // crash dump that happens to include a Session.
    @Test
    void toStringDoesNotLeakTheAccessToken() {
        Session session = Session.online("Steve", "abc123uuid", "SUPER_SECRET_TOKEN");

        String rendered = session.toString();

        assertFalse(rendered.contains("SUPER_SECRET_TOKEN"), "token leaked in: " + rendered);
        assertTrue(rendered.contains("redacted"), rendered);
        assertTrue(rendered.contains("Steve"), rendered);
    }

    @Test
    void offlineSessionIsMarkedOfflineAndCarriesNoRealToken() {
        Session session = Session.offline("manual-test");

        assertFalse(session.online());
        assertEquals("manual-test", session.username());
        assertFalse(session.accessToken().equals("SUPER_SECRET_TOKEN"));
    }

    @Test
    void onlineSessionKeepsTheSuppliedIdentity() {
        Session session = Session.online("Steve", "abc123uuid", "TOKEN");

        assertTrue(session.online());
        assertEquals("Steve", session.username());
        assertEquals("abc123uuid", session.uuid());
        assertEquals("TOKEN", session.accessToken());
    }
}
