/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
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
 *
 */
package org.apache.qpid.server.cluster;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;

/**
 * AMQUnexpectedBodyTypeException represents a failure where a message body does not match its expected type. For example,
 * and AMQP method should have a method body.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents a failure where a message body does not match its expected type.
 * </table>
 *
 * @todo Not an AMQP exception as no status code.
 *
 * @todo Seems like this exception was created to handle an unsafe type cast that will never happen in practice. Would
 *       be better just to leave that as a ClassCastException. Check that the framing layer will pick up the error first.
 */
public class AMQUnexpectedBodyTypeException extends AMQException
{
    public AMQUnexpectedBodyTypeException(Class<? extends AMQBody> expectedClass, AMQBody body)
    {
        super("Unexpected body type.  Expected: " + expectedClass.getName() + "; got: " + body.getClass().getName());
    }
}
