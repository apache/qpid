package org.apache.qpid.nclient.execution;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQRequestBody;
import org.apache.qpid.framing.AMQResponseBody;
import org.apache.qpid.framing.RequestResponseMappingException;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.AbstractPhase;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.nclient.model.AMQPMethodEvent;
import org.apache.qpid.protocol.AMQMethodEvent;

/**
 * Corressponds to the Layer 2 in AMQP.
 * This phase handles the correlation of amqp messages
 * This class implements the 0.9 spec (request/response) 
 */
public class ExecutionPhase extends AbstractPhase{

	protected static final Logger _logger = Logger.getLogger(ExecutionPhase.class);
    protected ConcurrentMap _channelId2RequestMgrMap = new ConcurrentHashMap();
    protected ConcurrentMap _channelId2ResponseMgrMap = new ConcurrentHashMap();

    
	/**
	 * --------------------------------------------------
	 * Phase related methods
	 * --------------------------------------------------
	 */	
    
    // should add these in the init method
    //_channelId2RequestMgrMap.put(0, new RequestManager(_ConnectionId, 0, this, false));
    //_channelId2ResponseMgrMap.put(0, new ResponseManager(_ConnectionId, 0, _stateManager, this, false));
    
	public void messageReceived(Object msg) throws AMQPException 
	{		
		AMQFrame frame = (AMQFrame) msg;
        final AMQBody bodyFrame = frame.getBodyFrame();

        if (bodyFrame instanceof AMQRequestBody)
        {   
        	AMQPMethodEvent event;
			try 
			{
				event = messageRequestBodyReceived(frame.getChannel(), (AMQRequestBody)bodyFrame);
				super.messageReceived(event);
			} 
			catch (Exception e) 
			{
				_logger.error("Error handling request",e);
			}
            
        }
        else if (bodyFrame instanceof AMQResponseBody)
        {
           List<AMQPMethodEvent> events;
		   try 
		   {
			   events = messageResponseBodyReceived(frame.getChannel(), (AMQResponseBody)bodyFrame);
			   for (AMQPMethodEvent event: events)
			   {
		        	  super.messageReceived(event);  
		       }
		   }
		   catch (Exception e) 
		   {
			   _logger.error("Error handling response",e);
		   }           
        }
	}

	/**
	 * Need to figure out if the message is a request or a response 
	 * that needs to be sent and then delegate it to the Request or response manager 
	 * to prepare it.
	 */
	public void messageSent(Object msg) throws AMQPException 
	{
		AMQPMethodEvent evt = (AMQPMethodEvent)msg;		
		if(evt.getCorrelationId() == QpidConstants.EMPTY_CORRELATION_ID)
		{
			// This is a request
			AMQFrame frame = handleRequest(evt);
			super.messageSent(frame);
		}
		else
		{
//			 This is a response
			List<AMQFrame> frames = handleResponse(evt);
			for(AMQFrame frame: frames)
			{
				super.messageSent(frame);
			}
		}		
	}

	/**
	 * ------------------------------------------------
	 * Methods to handle request response
	 * -----------------------------------------------
	 */
    private AMQPMethodEvent messageRequestBodyReceived(int channelId, AMQRequestBody requestBody) throws Exception
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Request frame received: " + requestBody);
        }
        ResponseManager responseManager = (ResponseManager)_channelId2ResponseMgrMap.get(channelId);
        if (responseManager == null)
            throw new AMQException("Unable to find ResponseManager for channel " + channelId);
        return responseManager.requestReceived(requestBody);
    }
    
    private List<AMQPMethodEvent> messageResponseBodyReceived(int channelId, AMQResponseBody responseBody) throws Exception
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Response frame received: " + responseBody);
        }
        RequestManager requestManager = (RequestManager)_channelId2RequestMgrMap.get(channelId);
        if (requestManager == null)
            throw new AMQException("Unable to find RequestManager for channel " + channelId);
        return requestManager.responseReceived(responseBody);
    }
    
    private AMQFrame handleRequest(AMQPMethodEvent evt)
    {
    	RequestManager requestManager = (RequestManager)_channelId2RequestMgrMap.get(evt.getChannelId());
    	return requestManager.sendRequest(evt);
    }
    
    private List<AMQFrame> handleResponse(AMQPMethodEvent evt) throws AMQPException
    {
    	ResponseManager responseManager = (ResponseManager)_channelId2ResponseMgrMap.get(evt.getChannelId());
    	try
    	{
    		return responseManager.sendResponse(evt);
    	}
    	catch(Exception e)
    	{
    		throw new AMQPException("Error handling response",e);
    	}
    }
}
