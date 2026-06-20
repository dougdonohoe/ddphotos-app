package com.donohoedigital.base;

/**
 * Interface for objects that have a name.  Used for default behavior in places like combo boxes.
 */
public interface NamedObject
{
    void setName(String sName);

    String getName();
}
