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
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.test.utils.QpidTestCase;

public class SuppressingInheritedAccessControlContextThreadFactoryTest extends QpidTestCase
{
    public void testAccessControlContextIsNotInheritedByThread() throws Exception
    {
        final String principalName = getName();
        final CountDownLatch latch = new CountDownLatch(1);

        final AtomicReference<AccessControlContext> threadAccessControlContextCapturer = new AtomicReference<>();
        final AtomicReference<AccessControlContext>  callerAccessControlContextCapturer = new AtomicReference<>();

        final Set<Principal> principals = Collections.<Principal>singleton(new Principal()
        {
            @Override
            public String getName()
            {
                return principalName;
            }

            @Override
            public String toString()
            {
                return "Principal{" + getName() + "}";
            }
        });

        Subject subject = new Subject(false, principals, Collections.EMPTY_SET, Collections.EMPTY_SET);

        Subject.doAs(subject, new PrivilegedAction<Void>()
        {
            @Override
            public Void run()
            {
                callerAccessControlContextCapturer.set(AccessController.getContext());
                SuppressingInheritedAccessControlContextThreadFactory factory = new SuppressingInheritedAccessControlContextThreadFactory();
                factory.newThread(new Runnable()
                {

                    public void run()
                    {
                        threadAccessControlContextCapturer.set(AccessController.getContext());
                        latch.countDown();
                    }

                }).start();
                return null;
            }
        });

        latch.await(3, TimeUnit.SECONDS);

        Subject callerSubject = Subject.getSubject(callerAccessControlContextCapturer.get());
        Subject threadSubject = Subject.getSubject(threadAccessControlContextCapturer.get());

        assertEquals("Unexpected subject in main thread", callerSubject, subject);
        assertNull("Unexpected subject in executor thread", threadSubject);

    }

}
