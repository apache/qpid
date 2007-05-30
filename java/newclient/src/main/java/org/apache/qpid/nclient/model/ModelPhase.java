package org.apache.qpid.nclient.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.nclient.amqp.event.AMQPEventManager;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.AbstractPhase;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.PhaseContext;
import org.apache.qpid.nclient.core.AMQPConstants;

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
	    AMQPEventManager eventManager = (AMQPEventManager)_ctx.getProperty(AMQPConstants.EVENT_MANAGER);
	    eventManager.notifyEvent(event);	    
	}
	
	/**
	 * ------------------------------------------------
	 *  Configuration 
	 * ------------------------------------------------
	 */	
}
