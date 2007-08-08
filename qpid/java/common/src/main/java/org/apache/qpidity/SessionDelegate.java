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
package org.apache.qpidity;


/**
 * SessionDelegate
 *
 * @author Rafael H. Schloming
 */

public abstract class SessionDelegate extends Delegate<Session>
{

    public abstract void headers(Session ssn, Struct ... headers);

    public abstract void data(Session ssn, Frame frame);

    @Override public void executionComplete(Session ssn, ExecutionComplete excmp)
    {
        RangeSet ranges = excmp.getRangedExecutionSet();
        if (ranges != null)
        {
            for (Range range : ranges)
            {
                ssn.complete(range.getLower(), range.getUpper());
            }
        }
        ssn.complete(excmp.getCumulativeExecutionMark());
        System.out.println("outstanding commands: " + ssn.getOutstandingCommands());
    }

    @Override public void executionFlush(Session ssn, ExecutionFlush flush)
    {
        ssn.flushProcessed();
    }

    @Override public void executionSync(Session ssn, ExecutionSync sync)
    {
        ssn.syncPoint();
    }

}
