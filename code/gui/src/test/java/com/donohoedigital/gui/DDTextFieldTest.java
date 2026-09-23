package com.donohoedigital.gui;

import com.donohoedigital.config.ApplicationType;
import com.donohoedigital.config.PropertyConfig;
import com.donohoedigital.config.StylesConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link DDTextField}'s validation: the regexp and the custom validator, alone and together.
 *
 * <p>Each test runs on the event thread, since {@link DDTextField#setText} requires it.  An empty
 * {@link PropertyConfig} and {@link StylesConfig} (no modules) are enough for the widget to build;
 * every tooltip and style lookup falls back to its default.
 */
public class DDTextFieldTest
{
    @BeforeAll
    public static void initConfig()
    {
        new PropertyConfig("test", new String[0], ApplicationType.COMMAND_LINE, null, false);
        new StylesConfig(new String[0]);
    }

    private static void onEdt(Runnable r) throws Exception
    {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    public void customValidatorRunsWithoutRegExp() throws Exception
    {
        onEdt(() -> {
            DDTextField field = new DDTextField();
            field.setCustomValidator(s -> s.equals("ok"));
            assertFalse(field.isValidData(), "empty text fails the validator as soon as it is set");

            field.setText("ok");
            assertTrue(field.isValidData());

            field.setText("abc");
            assertFalse(field.isValidData());
        });
    }

    @Test
    public void clearingCustomValidatorWithoutRegExpMakesFieldValid() throws Exception
    {
        onEdt(() -> {
            DDTextField field = new DDTextField();
            field.setCustomValidator(_ -> false);
            assertFalse(field.isValidData());

            field.setCustomValidator(null);
            assertTrue(field.isValidData());
        });
    }

    @Test
    public void regExpAndCustomValidatorMustBothPass() throws Exception
    {
        onEdt(() -> {
            DDTextField field = new DDTextField();
            field.setRegExp("[a-z]+");
            field.setCustomValidator(s -> !s.equals("bad"));

            field.setText("good");
            assertTrue(field.isValidData());

            field.setText("bad");
            assertFalse(field.isValidData(), "custom validator rejects");

            field.setText("123");
            assertFalse(field.isValidData(), "regexp rejects");
        });
    }

    @Test
    public void setValidIsKeptWithNeitherRegExpNorValidator() throws Exception
    {
        onEdt(() -> {
            // OptionTextArea's display field validates itself and calls setValid() directly;
            // typing must not reset that.
            DDTextField field = new DDTextField();
            field.setValid(false);
            field.setText("anything");
            assertFalse(field.isValidData());
        });
    }
}
