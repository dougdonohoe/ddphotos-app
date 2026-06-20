package com.donohoedigital.gui;

/**
 * Implemented by DD input widgets that can validate their content and notify
 * listeners when their value changes.  Allows generic form validation without
 * knowing the concrete widget type.
 */
public interface DDValidatable {
    boolean isValidData();
    void addValidationListener(Runnable onChange);
}
