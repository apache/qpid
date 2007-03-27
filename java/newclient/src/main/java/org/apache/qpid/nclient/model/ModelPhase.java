package org.apache.qpid.nclient.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.nclient.amqp.state.AMQPStateManager;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.AbstractPhase;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.PhaseContext;
import org.apache.qpid.nclient.core.QpidConstants;

/**
 * This Phase handles Layer 3 functionality of the AMQP spec.
 * This class acts as the interface between the API and the pipeline
 */
public class ModelPhase extends AbstractPhase {

	private static final Logger _logger = Logger.getLogger(ModelPhase.class);
	
	private Map <Class,List> _methodListners = new HashMap<Class,List>();
	
	/**
	 * ------------------------------------------------
	 * Phase - methods introduced by Phase
	 * ------------------------------------------------
	 */    
	public void init(PhaseContext ctx, Phase nextInFlowPhase, Phase nextOutFlowPhase) 
	{
		super.init(ctx, nextInFlowPhase, nextOutFlowPhase);
		try
		{
			loadMethodListeners();
		}
		catch(Exception e)
		{
			_logger.fatal("Error loading method listeners", e);
		}
	}

	public void messageReceived(Object msg) throws AMQPException 
	{
		notifyMethodListerners((AMQPMethodEvent)msg);
		
		// not doing super.methodReceived here, as this is the end of
		// the pipeline
		//super.messageReceived(msg);
	}

	/**
	 * This method should only except and pass messages
	 * of Type @see AMQPMethodEvent
	 */
	public void messageSent(Object msg) throws AMQPException 
	{		
		super.messageSent(msg);
	}
	
	/**
	 * ------------------------------------------------
	 *  Event Handling 
	 * ------------------------------------------------
	 */
	
	public void notifyMethodListerners(AMQPMethodEvent event) throws AMQPException
	{
		if (_methodListners.containsKey(event.getMethod().getClass()))
		{
			List<AMQPMethodListener> listeners = _methodListners.get(event.getMethod().getClass()); 
		
			if(listeners.size()>0)
			{
				throw new AMQPException("There are no registered listeners for this method");
			}
			
			for(AMQPMethodListener l : listeners)
			{
				try 
				{
					l.methodReceived(event);
				} 
				catch (Exception e) 
				{
					_logger.error("Error handling method event " +  event, e);
				}
			}
		}
	}
	
	/**
	 * ------------------------------------------------
	 *  Configuration 
	 * ------------------------------------------------
	 */    

	/**
	 * This method loads method listeners from the client.xml file
	 * For each method class there is a list of listeners
	 */
	private void loadMethodListeners() throws Exception
	{
		int count = ClientConfiguration.get().getMaxIndex(QpidConstants.METHOD_LISTENERS + "." + QpidConstants.METHOD_LISTENER);
		System.out.println(count);
				
		for(int i=0 ;i<count;i++)
		{
			String methodListener = QpidConstants.METHOD_LISTENERS + "." + QpidConstants.METHOD_LISTENER + "(" + i + ")";
			String className =  ClientConfiguration.get().getString(methodListener + "." + QpidConstants.CLASS);
			Class listenerClass = Class.forName(className);
			List<String> list = ClientConfiguration.get().getList(methodListener + "." + QpidConstants.METHOD_CLASS);
			for(String s:list)
			{
				List listeners;
				Class methodClass = Class.forName(s);
				if (_methodListners.containsKey(methodClass))
				{
					listeners = _methodListners.get(methodClass); 
				}
				else
				{
					listeners = new ArrayList();
					_methodListners.put(methodClass,listeners);
				}
				listeners.add(listenerClass);
			}
		}		
	}
	
}
