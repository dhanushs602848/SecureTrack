package com.example.securetrack;

import com.example.securetrack.utils.SessionManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThemeAndAppearanceTest {

    @Test
    public void testThemeConstantsDefined() {
        assertEquals("LIGHT", SessionManager.THEME_LIGHT);
        assertEquals("DARK", SessionManager.THEME_DARK);
        assertEquals("SYSTEM", SessionManager.THEME_SYSTEM);
    }

    @Test
    public void testThemeValidation() {
        String light = SessionManager.THEME_LIGHT;
        String dark = SessionManager.THEME_DARK;
        String system = SessionManager.THEME_SYSTEM;

        assertTrue(light.equalsIgnoreCase("light"));
        assertTrue(dark.equalsIgnoreCase("dark"));
        assertTrue(system.equalsIgnoreCase("system"));
    }
}
