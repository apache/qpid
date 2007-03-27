package org.apache.qpid.nclient.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.nclient.core.QpidConstants;

/**
 * Loads a properties file from classpath.
 * These values can be overwritten using system properties 
 */
public class ClientConfiguration extends CombinedConfiguration {
	
	private static final Logger _logger = Logger.getLogger(ClientConfiguration.class);
	private static ClientConfiguration _instance = new ClientConfiguration();
		
	ClientConfiguration()
	{
		super();
		addConfiguration(new SystemConfiguration());
		try 
		{
			XMLConfiguration config = new XMLConfiguration();
			config.load(getInputStream());
			addConfiguration(config);			
		} 
		catch (ConfigurationException e) 
		{
			_logger.warn("Client Properties missing, using defaults",e);
		}		
	}	

	public static ClientConfiguration get()
	{
		return _instance;
	}
	
	private InputStream getInputStream()
	{
		if (System.getProperty(QpidConstants.CONFIG_FILE_PATH) != null)
		{
			try
			{
				return new FileInputStream((String)System.getProperty(QpidConstants.CONFIG_FILE_PATH));
			}
			catch(Exception e)
			{
				return this.getClass().getResourceAsStream("client.xml");
			}
		}
		else
		{
			return this.getClass().getResourceAsStream("client.xml");
		}
		
	}
	
	public static void main(String[] args)
	{
		System.out.println(ClientConfiguration.get().getString(QpidConstants.USE_SHARED_READ_WRITE_POOL));
			
		//System.out.println(ClientConfiguration.get().getString("methodListeners.methodListener(1).[@class]"));
		int count = ClientConfiguration.get().getMaxIndex(QpidConstants.METHOD_LISTENERS + "." + QpidConstants.METHOD_LISTENER);
		System.out.println(count);
				
		for(int i=0 ;i<count;i++)
		{
			String methodListener = QpidConstants.METHOD_LISTENERS + "." + QpidConstants.METHOD_LISTENER + "(" + i + ")";
			System.out.println("\n\n"+ClientConfiguration.get().getString(methodListener + QpidConstants.CLASS));
			List<String> list = ClientConfiguration.get().getList(methodListener + "." + QpidConstants.METHOD_CLASS);
			for(String s:list)
			{
				System.out.println(s);
			}
		}
	}
}
