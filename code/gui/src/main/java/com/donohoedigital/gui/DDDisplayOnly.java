package com.donohoedigital.gui;

/**
 * A component that can be switched between editable and display-only rendering.
 *
 * <p>Display-only is not the same as disabled: the component keeps its normal
 * colors (it isn't greyed out) but stops taking focus and input, so a read-only
 * form reads as text rather than as a row of dead controls.
 */
public interface DDDisplayOnly {
    void setDisplayOnly(boolean b);
}
