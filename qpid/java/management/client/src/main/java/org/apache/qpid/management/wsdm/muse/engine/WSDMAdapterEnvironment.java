package org.apache.qpid.management.wsdm.muse.engine;

import java.io.File;
import java.net.URI;

import javax.servlet.ServletContext;

import org.apache.muse.core.AbstractEnvironment;
import org.apache.muse.util.FileUtils;
import org.apache.muse.ws.addressing.EndpointReference;
import org.apache.qpid.management.Messages;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.Protocol;
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
    private final ServletContext _servletContext;
	
    /**
     * Builds a new qman environment with the given application context.
     *  
     * @param servletContext the application context. 
     */
    public WSDMAdapterEnvironment(ServletContext servletContext)
    {
    	this._servletContext = servletContext;
    	String realDirectoryPath = servletContext.getRealPath(Names.WEB_APP_CLASSES_FOLDER);
        
        _realDirectory = (realDirectoryPath != null) 
        	? new File(realDirectoryPath) 
        	: FileUtils.CURRENT_DIR;
        	
        String defaultURI = getDefaultURIPrefix()+"adapter";
        setDefaultURI(defaultURI);
        
        LOGGER.info(Messages.QMAN_000029_DEFAULT_URI, defaultURI);
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
    
    public String getDefaultURIPrefix()
    {
        return new StringBuilder()
    		.append("http://")
    		.append(System.getProperty(
    				Names.ADAPTER_HOST_PROPERTY_NAME,
    				Protocol.DEFAULT_QMAN_HOSTNAME))
    		.append(":")
    		.append(System.getProperty(
    				Names.ADAPTER_PORT_PROPERTY_NAME,
    				String.valueOf(Protocol.DEFAULT_QMAN_PORT_NUMBER)))
    		.append(_servletContext.getContextPath())
    		.append("/services/")
    		.toString();    	
    }
}