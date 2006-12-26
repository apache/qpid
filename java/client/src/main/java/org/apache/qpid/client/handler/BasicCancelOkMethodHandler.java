package org.apache.qpid.client.handler;

import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.BasicMessageConsumer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ExchangeBoundOkBody;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.log4j.Logger;

/**
 * @author Apache Software Foundation
 */
public class BasicCancelOkMethodHandler implements StateAwareMethodListener
{
     private static final Logger _logger = Logger.getLogger(BasicCancelOkMethodHandler.class);
     private static final BasicCancelOkMethodHandler _instance = new BasicCancelOkMethodHandler();

     public static BasicCancelOkMethodHandler getInstance()
     {
         return _instance;
     }

     private BasicCancelOkMethodHandler()
     {
     }

     public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
     {
         _logger.debug("New BasicCancelOk method received");
         BasicCancelOkBody body = (BasicCancelOkBody) evt.getMethod();
         evt.getProtocolSession().confirmConsumerCancelled(evt.getChannelId(), body.consumerTag);                  
     }
}
