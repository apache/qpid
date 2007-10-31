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

import org.apache.qpid.framing.AMQMethodBody;

public class BlockingHandler implements ResponseHandler
{
    private final Class _expected;
    private boolean _completed;
    private AMQMethodBody _response;


    public BlockingHandler()
    {
        this(AMQMethodBody.class);
    }

    public BlockingHandler(Class<? extends AMQMethodBody> expected)
    {
        _expected = expected;
    }

    public void responded(AMQMethodBody response)
    {
        if (_expected.isInstance(response))
        {
            _response = response;
            completed();
        }
    }

    public void removed()
    {
        completed();
    }

    private synchronized void completed()
    {
        _completed = true;
        notifyAll();
    }

    synchronized void waitForCompletion()
    {
        while (!_completed)
        {
            try
            {
                wait();
            }
            catch (InterruptedException ignore)
            {

            }
        }
    }

    AMQMethodBody getResponse()
    {
        return _response;
    }

    boolean failed()
    {
        return _response == null;
    }

    boolean isCompleted()
    {
        return _completed;
    }
}
