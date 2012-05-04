package org.apache.qpid.systest.disttest.clientonly;

import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.naming.Context;

import junit.framework.Assert;

import org.apache.qpid.disttest.DistributedTestConstants;
import org.apache.qpid.disttest.jms.JmsMessageAdaptor;
import org.apache.qpid.disttest.message.Command;
import org.apache.qpid.disttest.message.CommandType;

/**
 * Helper for unit tests to simplify access to the Controller Queue.
 *
 * Implicitly creates the queue, so you must create a {@link ControllerQueue} object before
 * trying to use the underlying queue.
 */
public class ControllerQueue
{
    private MessageConsumer _controllerQueueMessageConsumer;
    private Session _controllerQueueSession;

    /**
     * Implicitly creates the queue, so you must create a {@link ControllerQueue} object before
     * trying to use the underlying queue.
     *
     * @param context used for looking up the controller queue {@link Destination}
     */
    public ControllerQueue(Connection connection, Context context) throws Exception
    {
        _controllerQueueSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination controllerQueue = (Destination) context.lookup(DistributedTestConstants.CONTROLLER_QUEUE_JNDI_NAME);
        _controllerQueueMessageConsumer = _controllerQueueSession.createConsumer(controllerQueue);
    }

    public <T extends Command> T getNext(long timeout) throws JMSException
    {
        final Message message = _controllerQueueMessageConsumer.receive(timeout);
        if(message == null)
        {
            return null;
        }

        return (T) JmsMessageAdaptor.messageToCommand(message);
    }

    public void addNextResponse(Map<CommandType, Command> responses) throws JMSException
    {
        Command nextResponse = getNext();
        responses.put(nextResponse.getType(), nextResponse);
    }

    @SuppressWarnings("unchecked")
    public <T extends Command> T getNext() throws JMSException
    {
        return (T)getNext(true);
    }

    public <T extends Command> T getNext(boolean assertMessageExists) throws JMSException
    {
        final Message message = _controllerQueueMessageConsumer.receive(1000);
        if(assertMessageExists)
        {
            Assert.assertNotNull("No message received from control queue", message);
        }

        if(message == null)
        {
            return null;
        }

        T command = (T) JmsMessageAdaptor.messageToCommand(message);

        return command;
    }

    public void close() throws Exception
    {
        _controllerQueueMessageConsumer.close();
        _controllerQueueSession.close();
    }
}
