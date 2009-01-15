package org.apache.qpid.management.wsdm.muse.engine;

import javax.servlet.ServletContext;

import org.apache.muse.core.Environment;
import org.apache.muse.core.platform.mini.MiniIsolationLayer;

/**
 * QMan specific implementation of the Apache Muse isolation layer.
 * If you are a Muse expert you were wondering why don't we use the muse default implementation...
 * well, 
 *  
 * @author Andrea Gazzarini
 */
public class WSDMAdapterIsolationLayer extends MiniIsolationLayer 
{
	/**
	 * Builds a new isolation layer with the given application context.
	 * 
	 * @param initialContext the application context.
	 */
	public WSDMAdapterIsolationLayer(ServletContext initialContext) 
	{
		super(null, initialContext);
	}

	/**
	 * WSDMAdapterEnvironment factory method.
	 * 
	 *  @return the environment.
	 */
	protected Environment createEnvironment() 
	{
		return new WSDMAdapterEnvironment(getInitialContext());
	}
}