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
package org.apache.qpid.server.management.plugin;

import java.net.SocketAddress;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedContextDefault;
import org.apache.qpid.server.model.Plugin;

public interface HttpManagementConfiguration<X extends HttpManagementConfiguration<X>> extends Plugin<X>
{

    @ManagedAttribute( defaultValue = "true" )
    boolean isHttpsSaslAuthenticationEnabled();

    @ManagedAttribute( defaultValue = "true" )
    boolean isHttpSaslAuthenticationEnabled();

    @ManagedAttribute( defaultValue = "true" )
    boolean isHttpsBasicAuthenticationEnabled();

    @ManagedAttribute( defaultValue = "false" )
    boolean isHttpBasicAuthenticationEnabled();

    @ManagedAttribute( defaultValue = "600" )
    public int getSessionTimeout();

    String HTTP_MANAGEMENT_COMPRESS_RESPONSES = "httpManagement.compressResponses";
    @ManagedContextDefault(name = HTTP_MANAGEMENT_COMPRESS_RESPONSES)
    boolean DEFAULT_COMPRESS_RESPONSES = false;

    @ManagedAttribute( defaultValue = "${"+HTTP_MANAGEMENT_COMPRESS_RESPONSES+"}" )
    public boolean isCompressResponses();

    String MAX_HTTP_FILE_UPLOAD_SIZE_CONTEXT_NAME = "maxHttpFileUploadSize";
    @ManagedContextDefault( name = MAX_HTTP_FILE_UPLOAD_SIZE_CONTEXT_NAME)
    static final long DEFAULT_MAX_UPLOAD_SIZE = 100 * 1024;


    String JETTY_THREAD_POOL_MAX_QUEUED = "jetty.threadPool.maxQueued";
    @ManagedContextDefault( name = JETTY_THREAD_POOL_MAX_QUEUED)
    static final long DEFAULT_JETTY_THREAD_POOL_MAX_QUEUED = 1000;

    String JETTY_THREAD_POOL_MAX_THREADS = "jetty.threadPool.maxThreads";
    @ManagedContextDefault( name = JETTY_THREAD_POOL_MAX_THREADS)
    static final long DEFAULT_JETTY_THREAD_POOL_MAX_THREADS = 50;


    String JETTY_THREAD_POOL_MIN_THREADS = "jetty.threadPool.minThreads";
    @ManagedContextDefault( name = JETTY_THREAD_POOL_MIN_THREADS)
    static final long DEFAULT_JETTY_THREAD_POOL_MIN_THREADS = 5;



    AuthenticationProvider getAuthenticationProvider(SocketAddress localAddress);
}
