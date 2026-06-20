/*
 * XYConstraints.java
 *
 * Created on October 24, 2002, 8:20 PM
 */

package com.donohoedigital.gui;

public class XYConstraints {
    public int x;
    public int y;
    public int width;
    public int height;

    public XYConstraints() {
        this(0, 0, 0, 0);
    }

    public XYConstraints(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int hashCode() {
        return x ^ y * 37 ^ width * 43 ^ height * 47;
    }

    public boolean equals(Object that) {
        if (that instanceof XYConstraints other) {
            return other.x == x && other.y == y && other.width == width && other.height == height;
        } else {
            return false;
        }
    }

    public String toString() {
        return "XYConstraints[" + x + "," + y + "," + width + "," + height + "]";
    }
}
