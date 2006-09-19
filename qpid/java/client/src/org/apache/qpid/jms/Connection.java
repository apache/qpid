/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.jms;

import javax.jms.*;


public interface Connection extends javax.jms.Connection
{
    /**
     * @return the maximum number of sessions supported by this Connection
     */
    long getMaximumChannelCount();

    void setConnectionListener(ConnectionListener listener);

    /**
     * Get the connection listener that has been registered with this connection, if any
     * @return the listener or null if none has been set
     */
    ConnectionListener getConnectionListener();

    /**
     * Create a session specifying the prefetch limit of messages.
     * @param transacted
     * @param acknowledgeMode
     * @param prefetch the maximum number of messages to buffer in the client. This
     * applies as a total across all consumers
     * @return
     * @throws JMSException
     */
    org.apache.qpid.jms.Session createSession(boolean transacted, int acknowledgeMode,
                                          int prefetch) throws JMSException;
}
