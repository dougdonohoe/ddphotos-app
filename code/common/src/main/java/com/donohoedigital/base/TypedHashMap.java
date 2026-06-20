package com.donohoedigital.base;

import java.util.*;

/**
 * This class is used to make hashtable access easier (less casting by
 * caller).  You will get class cast exceptions if object you are retrieving
 * doesn't match what is expected.
 */
@SuppressWarnings("unused")
public class TypedHashMap extends TreeMap<String, Object>
{
    /**
     * return a string param, sDefault if not found
     */
    public String getString(String sName, String sDefault)
    {
        String s = (String) get(sName);
        if (s == null) s = sDefault;

        return s;
    }
    
    /**
     * return a string param, null if not found
     */    
    public String getString(String sName)
    {
        return getString(sName, null);
    }
    
    /**
     * insert a string param
     */
    public void setString(String sName, String sValue)
    {
        put(sName, sValue);
    }

    /**
     * remove a string param
     */
    public String removeString(String sName)
    {
        return (String) remove(sName);
    }
    
    /**
     * return an integer param, nDefault if not found
     */
    public int getInteger(String sName, int nDefault)
    {
        Integer n = (Integer) get(sName);
        if (n == null) return nDefault;
        return n;
    }
    
    /**
     * return an integer param, nDefault if not found,
     * bounds are checked (min/max)
     */
    public int getInteger(String sName, int nDefault, int nMin, int nMax)
    {
        Integer n = (Integer) get(sName);
        if (n == null) return nDefault;
        int nValue = n;
        if (nValue < nMin) nValue = nMin;
        if (nValue > nMax) nValue = nMax;
        return nValue;
    }
    
    /**
     * return an integer param, null if not found
     */
    public Integer getInteger(String sName)
    {
        return (Integer) get(sName);
    }

    /**
     * insert an integer param
     */
    public void setInteger(String sName, Integer iValue)
    {
        put(sName, iValue);
    }
    
    /**
     * remove an integer param
     */
    public Integer removeInteger(String sName)
    {
        return (Integer) remove(sName);
    }
    
    /**
     * return a long param, nDefault if not found
     */
    public long getLong(String sName, long nDefault)
    {
        Long n = (Long) get(sName);
        if (n == null) return nDefault;
        return n;
    }
    
    /**
     * return a long param, null if not found
     */
    public Long getLong(String sName)
    {
        return (Long) get(sName);
    }

    /**
     * Return a long as a date, null if not found
     */
    public Date getLongAsDate(String sName)
    {
        Long value = getLong(sName);
        if (value == null) return null;
        return new Date(value);
    }

    /**
     * insert a long param
     */
    public void setLong(String sName, Long iValue)
    {
        put(sName, iValue);
    }

    /**
     * insert a date as a long
     */
    public void setLongFromDate(String sName, Date date)
    {
        if (date == null)
        {
            remove(sName);
            return;
        }
        setLong(sName, date.getTime());
    }
    
    /**
     * remove a long param
     */
    public Long removeLong(String sName)
    {
        return (Long) remove(sName);
    }
    
    /**
     * return a double param, dDefault if not found
     */
    public double getDouble(String sName, double dDefault)
    {
        Double n = (Double) get(sName);
        if (n == null) return dDefault;
        return n;
    }
    
    /**
     * return a double param, null if not found
     */
    public Double getDouble(String sName)
    {
        return (Double) get(sName);
    }

    /**
     * insert a double param
     */
    public void setDouble(String sName, Double dValue)
    {
        put(sName, dValue);
    }
    
    /**
     * remove a double param
     */
    public Double removeDouble(String sName)
    {
        return (Double) remove(sName);
    }
    
    /**
     * return a boolean param, bDefault if not found
     */
    public boolean getBoolean(String sName, boolean bDefault)
    {
        Boolean b = (Boolean) get(sName);
        if (b == null) return bDefault;
        return b;
    }
    
    /**
     * return a boolean param, null if not found
     */
    public Boolean getBoolean(String sName)
    {
        return (Boolean) get(sName);
    }
    
    /**
     * insert a boolean param
     */
    public void setBoolean(String sName, Boolean bValue)
    {
        put(sName, bValue);
    }
        
    /**
     * remove a boolean param
     */
    public Boolean removeBoolean(String sName)
    {
        return (Boolean) remove(sName);
    }
    
    /**
     * return an ArrayList param
     */
    public List<?> getList(String sName)
    {
        return (List<?>) get(sName);
    }
    
    /**
     * insert a list param
     */
    public void setList(String sName, List<?> aValue)
    {
        put(sName, aValue);
    }
    
    /**
     * remove a list param
     */
    public List<?> removeList(String sName)
    {
        return (List<?>) remove(sName);
    }
    
    /**
     * Return a generic object
     */
    public Object getObject(String sName)
    {
        return get(sName);
    }
    
    /**
     * insert an object param
     */
    public void setObject(String sName, Object oValue)
    {
        put(sName, oValue);
    }
    
    /**
     * remove a object param
     */
    public Object removeObject(String sName)
    {
        return remove(sName);
    }
    
    /**
     * String representation of contents of this map
     */
    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = keySet().iterator();
        String sName;
        List<?> list;
        Object oValue;
        while (iter.hasNext())
        {
            sName = iter.next();
            oValue = get(sName);
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(sName);
            sb.append('=');
            if (oValue instanceof List)
            {
                list = (List<?>) oValue;
                sb.append('[');
                for (int i = 0; i < list.size(); i++)
                {
                    if (i > 0) sb.append(", ");
                    
                    sb.append(list.get(i));
                }
                sb.append(']');
            }
            else
            {
                sb.append(oValue);
            }
        }
        
        return sb.toString();
    }
}
