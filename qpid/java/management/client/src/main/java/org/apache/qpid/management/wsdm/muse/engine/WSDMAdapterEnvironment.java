package org.apache.qpid.management.wsdm.muse.engine;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import javax.servlet.ServletContext;

import org.apache.muse.core.AbstractEnvironment;
import org.apache.muse.util.FileUtils;
import org.apache.muse.ws.addressing.EndpointReference;
import org.apache.qpid.management.Messages;
import org.apache.qpid.management.Names;
import org.apache.qpid.transport.util.Logger;

/**
 * QMan Adapter enviroment implementation.
 * 
 * @author Andrea Gazzarini
 */
public class WSDMAdapterEnvironment  extends AbstractEnvironment
{
	private final static Logger LOGGER = Logger.get(WSDMAdapterEnvironment.class);
	private final File _realDirectory;
    
    /**
     * Builds a new qman environment with the given application context.
     *  
     * @param servletContext the application context. 
     */
    public WSDMAdapterEnvironment(ServletContext servletContext)
    {
        String realDirectoryPath = servletContext.getRealPath(Names.WEB_APP_CLASSES_FOLDER);
        
        _realDirectory = (realDirectoryPath != null) 
        	? new File(realDirectoryPath) 
        	: FileUtils.CURRENT_DIR;

        String host = null;
   
        try {
			host = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			host = "localhost";
		}
        	
        String defaultURI = new StringBuilder()
        	.append("http://")
        	.append(host)
        	.append(":")
        	.append(System.getProperty(Names.ADAPTER_PORT))
        	.append(servletContext.getContextPath())
        	.append("/services/adapter")
        	.toString();
        
        LOGGER.info(Messages.QMAN_000029_DEFAULT_URI, defaultURI);
        
        setDefaultURI(defaultURI);
    }
    
    /**
     * Returns the endpoint created starting by this application default URI.
     * 
     * @return the endpoint created starting by this application default URI.
     */
    public EndpointReference getDeploymentEPR()
    {
        return new EndpointReference(URI.create(getDefaultURI()));
    }

    /**
     * Returns the application classes folder.
     * 
     * @return the application classes folder.
     */
    public File getRealDirectory()
    {
        return _realDirectory;
    }
}