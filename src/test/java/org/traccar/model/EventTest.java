package org.traccar.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EventTest {

    @Test
    public void testHashSourceTokenIsStableAndOpaque() {
        String token = "fcm-token-abc:123";
        String hash = Event.hashSourceToken(token);
        assertEquals(hash, Event.hashSourceToken(token));
        assertEquals(32, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
        assertFalse(hash.contains(token));
        assertNotEquals(hash, Event.hashSourceToken("otro-token"));
    }

    @Test
    public void testHashSourceTokenMatchesAppVector() {
        // Mismo vector que nivix_app/test/core/utils/source_token_test.dart: si cambia, cambiar ambos.
        assertEquals("b232ed569a49ecbc0a04d859a8ff0c74", Event.hashSourceToken("fcm-token-abc:123"));
    }

    @Test
    public void testSourceTokenHashSerializedAsAttribute() throws Exception {
        Event event = new Event(Event.TYPE_ALARM, 1);
        event.set(Position.KEY_ALARM, Position.ALARM_SOS);
        event.set(Event.KEY_SOURCE_TOKEN_HASH, Event.hashSourceToken("t"));
        String json = new ObjectMapper().writeValueAsString(event);
        assertTrue(json.contains("\"sourceTokenHash\""));
        assertFalse(json.contains("hashSourceToken"));
    }

}
