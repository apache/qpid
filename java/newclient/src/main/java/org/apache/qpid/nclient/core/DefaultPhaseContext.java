package org.apache.qpid.nclient.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultPhaseContext implements PhaseContext
{
    public Map<String,Object> _props = new ConcurrentHashMap<String,Object>();

    public Object getProperty(String name)
    {
	return _props.get(name);
    }

    public void setProperty(String name, Object value)
    {
	_props.put(name, value);
    }

}
