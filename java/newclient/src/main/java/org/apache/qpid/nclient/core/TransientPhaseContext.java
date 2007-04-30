package org.apache.qpid.nclient.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TransientPhaseContext implements PhaseContext {

	private Map map = new ConcurrentHashMap();
	
	public Object getProperty(String name) {
		return map.get(name);
	}

	public void setProperty(String name, Object value) {
		map.put(name, value);
	}

}
