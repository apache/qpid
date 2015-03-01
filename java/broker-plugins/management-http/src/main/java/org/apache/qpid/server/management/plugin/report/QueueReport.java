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
package org.apache.qpid.server.management.plugin.report;

import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;

/**
 * <p>
 *    The QueueReport class provides an extension point for installations to provide custom management reporting on
 *    queues through the REST API.
 * </p>
 *
 * <p>
 *     A custom QueueReport must extend either {@link org.apache.qpid.server.management.plugin.report.QueueTextReport}
 *     or {@link org.apache.qpid.server.management.plugin.report.QueueBinaryReport}. The report implementation must
 *     define a {@link #getName() name} which is unique amongst all installed reports.  The report class must be present
 *     in the classpath of the broker, and a provider-configuration file named
 *     org.apache.qpid.server.management.plugin.report.QueueReport must be added in the resource directory
 *     META-INF/services directory with the binary name of the implementation (as described in
 *     {@link java.util.ServiceLoader ServiceLoader}).
 * </p>
 *
 * <h3>Running reports</h3>
 * <p>
 *     The report can be run using the URL:
 *     {@code http://<broker>/service/queuereport/<virtualhost name>/<queue name>/<report name>[?param1=x&param2=y...]}
 * </p>
 *
 *  <h4>Report Parameters</h4>
 *
 * <p>
 *     Reports can take parameters from the query string of the HTTP request.  For every parameter in the query string
 *     the system will look for a setter on the report object with either a String or String[] parameter.  Thus if
 *     the query string contains {@code foo=bar}, then the system will look for a setter {@code setFoo(String value)} or
 *     {@code setFoo(String[] value)}.  If the same parameter occurs multiple times in the query string then only the
 *     array variant of the setter will be called.
 * </p>
 * <p>
 *     Setters for the parameters are guaranteed to be called before the first message is added to the report.
 * </p>
 *
 * <p>
 *     NOTE: In order to comply with the requirements of the {@link java.util.ServiceLoader ServiceLoader} api, all
 *     implementations of QueueReport MUST provide a public no-args constructor.
 * </p>
 * @param <T>
 */
public abstract class QueueReport<T>
{
    private Queue<?> _queue;

    QueueReport()
    {

    }

    /**
     * Gets the name of the report.
     * <p>
     *     The name of the report must be unique amongst all installed implementations. The name of the report
     *     is examined by the Qpid immediately upon construction.  The name should not change during
     *     the lifetime of the object (the value is only meaningful to the system at the time of initial construction)
     *     and all instances of the same concrete implementation should have the same name.
     * </p>
     * @return the name of the report
     */
    public abstract String getName();

    /**
     * Get the name of the queue against which the report is being run.
     *
     * @return the name of the queue
     */
    public final String getQueueName()
    {
        return _queue.getName();
    }

    final void setQueue(final Queue<?> queue)
    {
        _queue = queue;
    }

    /**
     * Get the name of the virtual host against which the report is being run.
     *
     * @return the name of the virtual host
     */
    public final String getVirtualHostName()
    {
        return _queue.getParent(VirtualHost.class).getName();
    }

    /**
     *
     * The value returned by getContentType() will be used to set the Content-Type HTTP header field
     *
     * @return the value to use for the content-type HTTP header field
     */
    public abstract String getContentType();

    /**
     * Called by the system to add a message to the report.
     *
     * <p>
     *   The method is called by the system for every message on the queue, or until {@link #isComplete()} returns true.
     * </p>
     * @param reportableMessage the message to add to the report
     */
    public abstract void addMessage(final ReportableMessage reportableMessage);

    /**
     * Informs the system if the report is complete (i.e. does not need to report on any more messages).
     *
     * <p>
     *     This method will be called by the system after each message is {@link #addMessage(ReportableMessage) added}
     *     to the report.  If a report is only interested in some messages, and can determine that the addition of more
     *     messages will not vary the content of the report, then it can return true.
     * </p>
     * <p>
     *     If this method always returns false, then all messages from the queue will be added to the report.
     * </p>
     * <p>
     *     NOTE: Retrieving content or properties of the message may require it to be reloaded from disk, and so care
     *     should be taken by reports to only access properties/content of the message if it is going to be required
     *     for the report production.
     * </p>
     *
     * @return true if the report does not want to report on any more messages in the queue
     */
    public abstract boolean isComplete();

    /**
     * Called by the system to get the content of the report to retrun to the user.
     * <p>
     *     The system guarantees to only call this method once
     * </p>
     * @return the report content.
     */
    public abstract T getReport();

}
