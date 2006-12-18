package org.apache.qpid.client;

import javax.jms.*;

/**
 * Provides support for covenience interface implemented by both AMQTemporaryTopic and AMQTemporaryQueue
 * so that operations related to their "temporary-ness" can be abstracted out.
 */
interface TemporaryDestination extends Destination
{

    public void delete() throws JMSException;
    public AMQSession getSession();
    public boolean isDeleted();

}
