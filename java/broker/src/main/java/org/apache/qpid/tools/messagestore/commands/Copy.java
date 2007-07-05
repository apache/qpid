/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.tools.messagestore.commands;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.tools.messagestore.MessageStoreTool;

public class Copy extends AbstractCommand
{

    /**
     * Since the Coopy command is not associated with a real channel we can safely create our own store context
     * for use in the few methods that require one.
     */
    private StoreContext _storeContext = new StoreContext();

    public Copy(MessageStoreTool tool)
    {
        super(tool);
    }

    public String help()
    {
        return "Copy messages between queues.\n" +
               "The currently selected message set will be copied to the specifed queue.\n" +
               "Alternatively the values can be provided on the command line.";
    }

    public String usage()
    {
        return "copy to=<queue> [from=<queue>] [msgids=<msgids eg, 1,2,4-10>]";
    }

    public String getCommand()
    {
        return "copy";
    }

    public void execute(String... args)
    {
        AMQQueue toQueue = null;
        AMQQueue fromQueue = _tool.getState().getQueue();
        java.util.List<Long> msgids = _tool.getState().getMessages();

        if (args.length >= 2)
        {
            for (String arg : args)
            {
                if (arg.startsWith("to="))
                {
                    String queueName = arg.substring(arg.indexOf("=") + 1);
                    toQueue = _tool.getState().getVhost().getQueueRegistry().getQueue(new AMQShortString(queueName));
                }

                if (arg.startsWith("from="))
                {
                    String queueName = arg.substring(arg.indexOf("=") + 1);
                    fromQueue = _tool.getState().getVhost().getQueueRegistry().getQueue(new AMQShortString(queueName));
                }

                if (arg.startsWith("msgids="))
                {
                    String msgidStr = arg.substring(arg.indexOf("=") + 1);

                    // Record the current message selection
                    java.util.List<Long> currentIDs = _tool.getState().getMessages();

                    // Use the ToolState class to perform the messasge parsing
                    _tool.getState().setMessages(msgidStr);
                    msgids = _tool.getState().getMessages();

                    // Reset the original selection of messages
                    _tool.getState().setMessages(currentIDs);
                }
            }
        }

        if (toQueue == null)
        {
            _console.println("Queue to copy to not specifed.");
            _console.println(usage());
            return;
        }

        if (fromQueue == null)
        {
            _console.println("Queue to copy from not specifed.");
            _console.println(usage());
            return;
        }

        performCopy(fromQueue, toQueue, msgids);
    }

    protected void performCopy(AMQQueue fromQueue, AMQQueue toQueue, java.util.List<Long> msgids)
    {
        Long previous = null;
        Long start = null;

        for (long id : msgids)
        {
            if (previous != null)
            {
                if (id == previous + 1)
                {
                    if (start == null)
                    {
                        start = previous;
                    }
                }
                else
                {
                    if (start != null)
                    {
                        //move a range of ids
                        fromQueue.moveMessagesToAnotherQueue(start, id, toQueue.getName().toString(), _storeContext);
                    }
                    else
                    {
                        //move a single id
                        fromQueue.moveMessagesToAnotherQueue(id, id, toQueue.getName().toString(), _storeContext);
                    }
                }
            }

            previous = id;
        }
    }
}
