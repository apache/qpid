package org.apache.qpid.client;

import java.util.Enumeration;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

public class QpidConnectionMetaData implements ConnectionMetaData {

	private static QpidConnectionMetaData _instance = new QpidConnectionMetaData();
	
	private QpidConnectionMetaData(){		
	}
	
	public static QpidConnectionMetaData instance(){
		return _instance;
	}	
	
	public int getJMSMajorVersion() throws JMSException {
		return 1;
	}

	public int getJMSMinorVersion() throws JMSException {
		return 1;
	}

	public String getJMSProviderName() throws JMSException {
		return "Apache Qpid";
	}

	public String getJMSVersion() throws JMSException {
		return "1.1";
	}

	public Enumeration getJMSXPropertyNames() throws JMSException {
		return null;
	}

	public int getProviderMajorVersion() throws JMSException {
		return 0;
	}

	public int getProviderMinorVersion() throws JMSException {
		return 9;
	}

	public String getProviderVersion() throws JMSException {
		return "Incubating-M1";
	}
}
