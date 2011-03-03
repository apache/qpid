package org.apache.qpid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class BrokerOptions extends HashMap<String, List<String>>
{
    /** serialVersionUID */
    private static final long serialVersionUID = 8051825964945442234L;
    
    public static final String DEFAULT_CONFIG_FILE = "etc/config.xml";
    public static final String DEFAULT_LOG_CONFIG_FILENAME = "log4j.xml";
    public static final String QPID_HOME = "QPID_HOME";
    
    public static final String PORTS = "p";
    public static final String EXCLUDE_0_10 = "exclude-0-10";
    public static final String EXCLUDE_0_9_1 = "exclude-0-9-1";
    public static final String EXCLUDE_0_9 = "exclude-0-9";
    public static final String EXCLUDE_0_8 = "exclude-0-8";
    public static final String BIND = "b";
    public static final String MANAGEMENT = "m";
    public static final String LOG4J = "l";
    public static final String WATCH = "w";
    public static final String CONFIG = "c";
    public static final String PROTOCOL = "protocol";
    
    public static final String[] COMMAND_LINE_OPTIONS = new String[] {
        PORTS, EXCLUDE_0_10, EXCLUDE_0_9_1, EXCLUDE_0_9, EXCLUDE_0_8,
        BIND, MANAGEMENT, LOG4J, WATCH, CONFIG,
    };
    
    public void setPorts(Integer...ports)
    {
        put(PORTS, ports);
    }
    
    public List<Integer> getPorts()
    {
        return getList(PORTS);
    }
    
    public void setExclude_0_10Ports(Integer...ports)
    {
        put(EXCLUDE_0_10, ports);
    }
    
    public List<Integer> getExclude_0_10Ports()
    {
        return getList(EXCLUDE_0_10);
    }
    
    public void setExclude_0_9_1Ports(Integer...ports)
    {
        put(EXCLUDE_0_9_1, ports);
    }
    
    public List<Integer> getExclude_0_9_1Ports()
    {
        return getList(EXCLUDE_0_9_1);
    }
    
    public void setExclude_0_9Ports(Integer...ports)
    {
        put(EXCLUDE_0_9, ports);
    }
    
    public List<Integer> getExclude_0_9Ports()
    {
        return getList(EXCLUDE_0_9);
    }
    
    public void setExclude_0_8Ports(Integer...ports)
    {
        put(EXCLUDE_0_8, ports);
    }
    
    public List<Integer> getExclude_0_8Ports()
    {
        return getList(EXCLUDE_0_8);
    }
    
    public void setManagementPort(Integer management)
    {
        put(MANAGEMENT, Integer.toString(management));
    }
    
    public Integer getManagementPort()
    {
        return getInteger(MANAGEMENT);
    }
    
    public void setBind(String bind)
    {
        put(BIND, bind);
    }
    
    public String getBind()
    {
        return getValue(BIND);
    }
    
    public void setLog4JFile(String log4j)
    {
        put(LOG4J, log4j);
    }
    
    public String getLog4JFile()
    {
        return getValue(LOG4J);
    }
    
    public void setLog4JWatch(Integer watch)
    {
        put(WATCH, Integer.toString(watch));
    }
    
    public Integer getLog4JWatch()
    {
        return getInteger(WATCH);
    }
    
    public void setConfigFile(String config)
    {
        put(CONFIG, config);
    }
    
    public String getConfigFile()
    {
        return getValue(CONFIG);
    }
    
    public void setProtocol(String protocol)
    {
        put(PROTOCOL, protocol);
    }
    
    public String getProtocol()
    {
        return getValue(PROTOCOL);
    }
    
    public void put(String key, String value)
    {
        if (value != null)
        {
	        put(key, Collections.singletonList(value));
        }
    }
    
    public void put(String key, String...values)
    {
        if (values != null)
        {
            put(key, Arrays.asList(values));
        }
    }
    
    public void put(String key, Integer...values)
    {
        List<String> list = new ArrayList<String>();
        for (Integer i : values)
        {
            list.add(Integer.toString(i));
        }
        put(key, list);
    }
    
    public Integer getInteger(Object key)
    {
        return getInteger(key, null);
    }
    
    public Integer getInteger(Object key, Integer defaultValue)
    {
        if (!containsKey(key))
        {
            return defaultValue;
        }
        List<String> values = get(key);
        return Integer.valueOf(values.get(0));
    }
    
    public List<Integer> getList(Object key)
    {
        return getList(key, null);
    }
    
    public List<Integer> getList(Object key, List<Integer> defaultValues)
    {
        if (!containsKey(key))
        {
            return defaultValues;
        }
        List<String> list = get(key);
        List<Integer> values = new ArrayList<Integer>();
        for (String s : list)
        {
            values.add(Integer.valueOf(s));
        }
        return values;
    }
    
    public String getValue(Object key)
    {
        return getValue(key, null);
    }
    
    public String getValue(Object key, String defaultValue)
    {
        if (!containsKey(key))
        {
            return defaultValue;
        }
        List<String> values = get(key);
        return values.get(0);
    }
    
    public List<String> get(Object key, List<String> defaultValues)
    {
        if (!containsKey(key))
        {
            return defaultValues;
        }
        return get(key);
    }
}
