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
package org.apache.qpid.pool;


import javax.security.auth.Subject;
import java.security.PrivilegedAction;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * <code>ThreadFactory</code> to create threads with empty inherited  <code>java.security.AccessControlContext</code>
 * <p></p>
 * It delegates thread creation to <code>Executors</code> default thread factory.
 */
public class SuppressingInheritedAccessControlContextThreadFactory implements ThreadFactory
{
    private final ThreadFactory _defaultThreadFactory = Executors.defaultThreadFactory();

    @Override
    public Thread newThread(final Runnable runnable)
    {
        return Subject.doAsPrivileged(null, new PrivilegedAction<Thread>()
                                            {
                                                @Override
                                                public Thread run()
                                                {
                                                    return _defaultThreadFactory.newThread(runnable);
                                                }
                                            }, null);
    }
}
