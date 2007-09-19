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
package org.apache.qpidity.njms.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpidity.QpidException;

import javax.jms.ObjectMessage;
import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Implemetns javax.njms.ObjectMessage
 */
public class ObjectMessageImpl extends MessageImpl implements ObjectMessage
{

    /**
     * this ObjectMessageImpl's logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(ObjectMessageImpl.class);

    /**
     * The ObjectMessage's payload.
     */
    private Serializable _object = null;

    //--- Constructor
    /**
     * Constructor used by SessionImpl.
     */
    public ObjectMessageImpl()
    {
        super();
        setMessageType(String.valueOf(MessageFactory.JAVAX_JMS_OBJECTMESSAGE));
    }

    /**
     * Constructor used by MessageFactory
     *
     * @param message The new qpid message.
     * @throws QpidException In case of IO problem when reading the received message.
     */
    protected ObjectMessageImpl(org.apache.qpidity.api.Message message) throws QpidException
    {
        super(message);
    }

    //--- Interface ObjctMessage
    /**
     * Sets the serializable object containing this message's data.
     * <p> JE JMS spec says:
     * <p> It is important to note that an <CODE>ObjectMessage</CODE>
     * contains a snapshot of the object at the time <CODE>setObject()</CODE>
     * is called; subsequent modifications of the object will have no
     * effect on the <CODE>ObjectMessage</CODE> body.
     *
     * @param object The message's data
     * @throws JMSException If setting the object fails due to some error.
     * @throws javax.jms.MessageFormatException
     *                      If object serialization fails.
     * @throws javax.jms.MessageNotWriteableException
     *                      If the message is in read-only mode.
     */
    public void setObject(Serializable object) throws JMSException
    {
        isWriteable();
        try
        {
            // Serialize the passed in object, then de-serialize it
            // so that changes to it do not affect m_data  (JAVA's way to perform a deep clone)
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            ObjectOutputStream objOut = new ObjectOutputStream(bOut);
            objOut.writeObject(object);
            byte[] bArray = bOut.toByteArray();
            ByteArrayInputStream bIn = new ByteArrayInputStream(bArray);
            ObjectInputStream objIn = new ObjectInputStream(bIn);
            _object = (Serializable) objIn.readObject();
            objOut.close();
            objIn.close();
        }
        catch (Exception e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Unexpected exeption when performing object deep clone", e);
            }
            throw new MessageNotWriteableException("Unexpected exeption when performing object deep clone",
                                                   e.getMessage());
        }
    }

    /**
     * Gets the serializable object containing this message's data. The
     * default value is null.
     *
     * @return The serializable object containing this message's data
     * @throws JMSException If getting the object fails due to some internal error.
     */
    public Serializable getObject() throws JMSException
    {
        return _object;
    }

    //--- Overwritten methods
    /**
     * This method is invoked before a message dispatch operation.
     *
     * @throws org.apache.qpidity.QpidException
     *          If the destination is not set
     */
    public void beforeMessageDispatch() throws QpidException
    {
        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(_object);
            byte[] bytes = baos.toByteArray();
            setMessageData(ByteBuffer.wrap(bytes));
        }
        catch (IOException e)
        {
            throw new QpidException("Problem when setting object of object message", null, e);
        }
        super.beforeMessageDispatch();
    }

    /**
     * This method is invoked after this message is received.
     *
     * @throws QpidException
     */
    @Override
    public void afterMessageReceive() throws QpidException
    {
        super.afterMessageReceive();
        ByteBuffer messageData = getMessageData();
        if (messageData != null)
        {
            try
            {
                ObjectInputStream ois = new ObjectInputStream(asInputStream());
                _object = (Serializable) ois.readObject();
            }
            catch (IOException ioe)
            {
                throw new QpidException(
                        "Unexpected error during rebuild of message in afterReceive() - " + "The Object stored in the message was not a Serializable object.",
                        null, ioe);
            }
            catch (ClassNotFoundException clnfe)
            {
                throw new QpidException(
                        "Unexpected error during rebuild of message in afterReceive() - " + "Could not find the required class in classpath.",
                        null, clnfe);
            }
        }
    }
}
