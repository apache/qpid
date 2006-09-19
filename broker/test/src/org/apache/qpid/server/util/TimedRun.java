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
package org.apache.qpid.server.util;

import java.util.concurrent.Callable;

public abstract class TimedRun implements Callable<Long>
{
    private final String description;

    public TimedRun(String description)
    {
        this.description = description;
    }

    public Long call() throws Exception
    {
        setup();
        long start = System.currentTimeMillis();
        run();
        long stop = System.currentTimeMillis();
        teardown();
        return stop - start;
    }

    public String toString()
    {
        return description;
    }

    protected void setup() throws Exception{}
    protected void teardown() throws Exception{}
    protected abstract void run() throws Exception;
}
