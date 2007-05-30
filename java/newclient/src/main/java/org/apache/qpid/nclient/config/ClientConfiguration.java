package org.apache.qpid.nclient.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import javax.security.sasl.SaslClientFactory;

import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.apache.qpid.nclient.core.AMQPConstants;
import org.apache.qpid.nclient.security.AMQPCallbackHandler;

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
		if (System.getProperty(AMQPConstants.CONFIG_FILE_PATH) != null)
		{
			try
			{
				return new FileInputStream((String)System.getProperty(AMQPConstants.CONFIG_FILE_PATH));
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
	    String key = AMQPConstants.AMQP_SECURITY + "." + 
	        AMQPConstants.AMQP_SECURITY_SASL_CLIENT_FACTORY_TYPES + "." +
	        AMQPConstants.AMQP_SECURITY_SASL_CLIENT_FACTORY;
	        
	    TreeMap<String, Class<? extends SaslClientFactory>> factoriesToRegister =
                new TreeMap<String, Class<? extends SaslClientFactory>>();
	    
	        int index = ClientConfiguration.get().getMaxIndex(key);                                       
	        	
	        for (int i=0; i<index+1;i++)
	        {
	            String mechanism = ClientConfiguration.get().getString(key + "(" + i + ")[@type]");
	            String className = ClientConfiguration.get().getString(key + "(" + i + ")" );
	            try
	            {
	                Class<?> clazz = Class.forName(className);
	                if (!(SaslClientFactory.class.isAssignableFrom(clazz)))
	                {
	                    _logger.error("Class " + clazz + " does not implement " + SaslClientFactory.class + " - skipping");
	                    continue;
	                }
	                factoriesToRegister.put(mechanism, (Class<? extends SaslClientFactory>) clazz);
	            }
	            catch (Exception ex)
	            {
	                _logger.error("Error instantiating SaslClientFactory calss " + className  + " - skipping");
	            }
	        }
	}
}
