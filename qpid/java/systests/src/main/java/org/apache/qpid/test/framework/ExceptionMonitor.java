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
package org.apache.qpid.test.framework;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * An exception monitor, listens for JMS exception on a connection or consumer. It record all exceptions that it receives
 * and provides methods to test the number and type of exceptions received.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Record all exceptions received. <td> {@link ExceptionListener}
 * </table>
 */
public class ExceptionMonitor implements ExceptionListener
{
    /** Holds the received exceptions. */
    List<JMSException> exceptions = new ArrayList<JMSException>();

    /**
     * Receives incoming exceptions.
     *
     * @param e The exception to record.
     */
    public void onException(JMSException e)
    {
        exceptions.add(e);
    }

    /**
     * Checks that no exceptions have been received.
     *
     * @return <tt>true</tt> if no exceptions have been received, <tt>false</tt> otherwise.
     */
    public boolean assertNoExceptions()
    {
        return exceptions.isEmpty();
    }

    /**
     * Checks that exactly one exception has been received.
     *
     * @return <tt>true</tt> if exactly one exception been received, <tt>false</tt> otherwise.
     */
    public boolean assertOneJMSException()
    {
        return exceptions.size() == 1;
    }

    /**
     * Checks that exactly one exception, with a linked cause of the specified type, has been received.
     *
     * @return <tt>true</tt> if exactly one exception, with a linked cause of the specified type, been received,
     *         <tt>false</tt> otherwise.
     */
    public boolean assertOneJMSExceptionWithLinkedCause(Class aClass)
    {
        if (exceptions.size() == 1)
        {
            JMSException e = exceptions.get(0);

            Exception linkedCause = e.getLinkedException();

            if ((linkedCause != null) && aClass.isInstance(linkedCause))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Reports the number of exceptions held by this monitor.
     *
     * @return The number of exceptions held by this monitor.
     */
    public int size()
    {
        return exceptions.size();
    }

    /**
     * Clears the record of received exceptions.
     */
    public void reset()
    {
        exceptions = new ArrayList();
    }

    /**
     * Provides a dump of the stack traces of all exceptions that this exception monitor was notified of. Mainly
     * use for debugging/test failure reporting purposes.
     *
     * @return A string containing a dump of the stack traces of all exceptions.
     */
    public String toString()
    {
        String result = "ExceptionMonitor: holds " + exceptions.size() + " exceptions.\n\n";

        for (JMSException ex : exceptions)
        {
            result += getStackTrace(ex) + "\n";
        }

        return result;
    }

    /**
     * Prints an exception stack trace into a string.
     *
     * @param t The throwable to get the stack trace from.
     *
     * @return A string containing the throwables stack trace.
     */
    public static String getStackTrace(Throwable t)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);
        pw.flush();
        sw.flush();

        return sw.toString();
    }
}
