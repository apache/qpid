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
package org.apache.qpid.tools.report;

import java.io.PrintStream;
import java.lang.reflect.Constructor;

import javax.jms.Message;

public class BasicReporter implements Reporter
{
    PrintStream out;
    int batchSize = 0;
    int batchCount = 0;
    boolean headerPrinted = false;
    protected Statistics overall;
    Statistics batch;
    Constructor<? extends Statistics> statCtor;

    public BasicReporter(Class<? extends Statistics> clazz, PrintStream out,
            int batchSize, boolean printHeader) throws Exception
    {
        this.out = out;
        this.batchSize = batchSize;
        this.headerPrinted = !printHeader;
        statCtor = clazz.getConstructor();
        overall = statCtor.newInstance();
        if (batchSize > 0)
        {
            batch = statCtor.newInstance();
        }        
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.report.Reporter#message(javax.jms.Message)
     */
    @Override
    public void message(Message msg)
    {
        overall.message(msg);
        if (batchSize > 0) 
        {
            batch.message(msg);            
            if (++batchCount == batchSize) 
            {
                if (!headerPrinted)
                {
                    header(); 
                }
                batch.report(out);
                batch.clear();
                batchCount = 0;
            }
        }
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.report.Reporter#report()
     */
    @Override
    public void report()
    {
        if (!headerPrinted)
        {
            header();
        }
        overall.report(out);
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.report.Reporter#header()
     */
    @Override
    public void header()
    {
        headerPrinted = true;
        overall.header(out);
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.tools.report.Reporter#log()
     */
    @Override
    public void log(String s)
    {
        // NOOP
    }

    @Override
    public void clear()
    {
        batch.clear();
        overall.clear();
    }
}
