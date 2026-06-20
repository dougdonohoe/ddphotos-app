/*
 * DataElement.java
 *
 * Created on November 11, 2002, 7:57 PM
 */

package com.donohoedigital.config;

import com.donohoedigital.base.NamedObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author Doug Donohoe
 */
public class DataElement<E>
{
    static Logger logger = LogManager.getLogger(DataElement.class);

    List<E> values_;
    List<String> displayValues_;
    String sName_;

    /**
     * Creates a new instance of DataElement
     */
    public DataElement(String sName, List<E> values, List<String> displayValues)
    {
        sName_ = sName;
        values_ = values;
        displayValues_ = displayValues;
    }

    /**
     * Get name of this data element
     */
    public String getName()
    {
        return sName_;
    }

    /**
     * Get list values.  Do not modify the returned object.
     */
    public List<E> getListValues()
    {
        return values_;
    }

    /**
     * Get display value for given value
     */
    public String getDisplayValue(Object oValue)
    {
        // oValue arrives untyped from Swing (getSelectedItem / renderer), so look it
        // up through a List<?> view where indexOf(Object) is the natural contract.
        @SuppressWarnings("RedundantCast") int index = ((List<?>) values_).indexOf(oValue);
        String sDisplayValue = null;

        if ((displayValues_ != null) && (displayValues_.size() > index))
        {
            sDisplayValue = displayValues_.get(index);
        }

        if ((sDisplayValue == null) && (oValue instanceof NamedObject))
        {
            sDisplayValue = ((NamedObject) oValue).getName();
        }

        if (sDisplayValue == null)
        {
            logger.warn("WARNING: No display value for {}.{}", sName_, oValue);
            return oValue.toString();
        }
        else
        {
            return sDisplayValue;
        }
    }
}
