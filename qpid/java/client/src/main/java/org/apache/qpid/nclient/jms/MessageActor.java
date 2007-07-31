/* Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.qpid.nclient.jms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpid.nclient.api.Resource;
import org.apache.qpidity.QpidException;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;

/**
 * MessageActor is the superclass for MessageProducerImpl and MessageProducerImpl.
 */
public abstract class MessageActor
{
    /**
     * Used for debugging.
     */
    private static final Logger _logger = LoggerFactory.getLogger(MessageActor.class);

    /**
     * Indicates whether this MessageActor is closed.
     */
    boolean _isClosed = false;

    /**
     * This messageActor's session
     */
    SessionImpl _session;

    /**
     * The underlying Qpid Resource
     */
    Resource _qpidResource;

    /**
     * The JMS destination this actor is set for.
     */
    DestinationImpl _destination;

    //-- Constructor

    //TODO define the parameters

     protected MessageActor()
    {
        
    }

    protected MessageActor(SessionImpl session, DestinationImpl destination)
    {
        // TODO create the qpidResource _qpidResource =
        _session = session;
        _destination = destination;
    }

    //--- public methods (part of the jms public API)
    /**
     * Closes the MessageActor and deregister it from its session.
     *
     * @throws JMSException if the MessaeActor cannot be closed due to some internal error.
     */
    public void close() throws JMSException
    {
        if (!_isClosed)
        {
            closeMessageActor();
            // notify the session that this message actor is closing
            _session.closeMessageActor(this);
        }
    }

    //-- protected methods
     /**
     * Check if this MessageActor is not closed.
     * <p> If the MessageActor is closed, throw a javax.jms.IllegalStateException.
     * <p> The method is not synchronized, since MessageProducers can only be used by a single thread.
     *
     * @throws IllegalStateException if the MessageActor is closed
     */
    protected void checkNotClosed() throws IllegalStateException
    {
        if (_isClosed || _session == null)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Actor " + this + " is already closed");
            }
            throw new IllegalStateException("Actor " + this + " is already closed");
        }
        _session.checkNotClosed();
    }

    /**
     * Closes a MessageActor.
     * <p> This method is invoked when the session is closing or when this
     * messageActor is closing.
     *
     * @throws JMSException If the MessaeActor cannot be closed due to some internal error.
     */
    protected void closeMessageActor() throws JMSException
    {
        if (!_isClosed)
        {
            // close the underlying qpid resource
            try
            {
                _qpidResource.close();
            }
            catch (QpidException e)
            {
                throw ExceptionHelper.convertQpidExceptionToJMSException(e);
            }
            _isClosed = true;
        }
    }

    /**
     * Get the associated session object. 
     *
     * @return This Actor's Session.
     */
    protected  SessionImpl getSession()
    {
        return _session;
    }

}
