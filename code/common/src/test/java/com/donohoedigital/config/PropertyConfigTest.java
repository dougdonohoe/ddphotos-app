package com.donohoedigital.config;

import junit.framework.TestCase;

/**
 * Created by IntelliJ IDEA.
 * User: donohoe
 * Date: Apr 6, 2008
 * Time: 4:43:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertyConfigTest extends TestCase
{
    public void testLoadClient()
    {
        System.getProperties().setProperty("user.name", "unit-tester");
        String[] modules = {"common", "testapp"};
        new PropertyConfig("testapp", modules, ApplicationType.CLIENT, null, true);

        assertTrue(PropertyConfig.getRequiredBooleanProperty("test.common"));
        assertTrue(PropertyConfig.getRequiredBooleanProperty("test.common.override"));

        assertTrue(PropertyConfig.getRequiredBooleanProperty("test.boolean.true"));
        assertFalse(PropertyConfig.getRequiredBooleanProperty("test.boolean.false"));
        assertTrue(PropertyConfig.getRequiredBooleanProperty("test.boolean.yes"));
        assertFalse(PropertyConfig.getRequiredBooleanProperty("test.boolean.no"));
        assertTrue(PropertyConfig.getRequiredBooleanProperty("test.boolean.+"));
        assertFalse(PropertyConfig.getRequiredBooleanProperty("test.boolean.-"));
        assertTrue(PropertyConfig.getRequiredBooleanProperty("test.boolean.1"));
        assertFalse(PropertyConfig.getRequiredBooleanProperty("test.boolean.0"));

        assertEquals("This is a string", PropertyConfig.getRequiredStringProperty("test.string"));
        assertEquals(42, PropertyConfig.getRequiredIntegerProperty("test.integer"));
        assertEquals(3.14159d, PropertyConfig.getRequiredDoubleProperty("test.double"), .0000001d);

        assertEquals("No replacement", PropertyConfig.getMessage("test.message"));
        assertEquals("Replace just one.", PropertyConfig.getMessage("test.message.one", "just"));
        assertEquals("Replace this and that.", PropertyConfig.getMessage("test.message.two", "this", "that"));

        // COPY=[key] redirects a lookup to another property's value
        assertEquals("This is a string", PropertyConfig.getStringProperty("test.copy.string"));
        assertEquals("This is a string", PropertyConfig.getStringProperty("test.copy.chain"));
        assertEquals("This is a string", PropertyConfig.getRequiredStringProperty("test.copy.string"));
        // COPY is resolved before MessageFormat substitution
        assertEquals("Replace this and that.", PropertyConfig.getMessage("test.copy.message", "this", "that"));
        // a COPY pointing at a missing key resolves to null
        assertNull(PropertyConfig.getStringProperty("test.copy.missing", null, false));

        // override in unit-tester.properties
        assertTrue(PropertyConfig.getRequiredBooleanProperty("override.set"));
    }
}
