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

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.QueueEntryVisitor;
import org.apache.qpid.test.utils.QpidTestCase;

public class ReportRunnerTest extends QpidTestCase
{
    public void testTextReportCountsMessages()
    {
        ReportRunner<String> runner = (ReportRunner<String>) ReportRunner.createRunner(TestTextReport.NAME,
                                                                                       Collections.<String, String[]>emptyMap());
        Queue queue = createMockQueue();
        assertEquals("There are 0 messages on the queue.", runner.runReport(queue));

        runner = (ReportRunner<String>) ReportRunner.createRunner(TestTextReport.NAME,
                                                                  Collections.<String, String[]>emptyMap());
        Queue queue1 = createMockQueue(mock(ServerMessage.class));
        assertEquals("There are 1 messages on the queue.", runner.runReport(queue1));

        runner = (ReportRunner<String>) ReportRunner.createRunner(TestTextReport.NAME,
                                                                  Collections.<String, String[]>emptyMap());
        Queue queue2 = createMockQueue(mock(ServerMessage.class), mock(ServerMessage.class));
        assertEquals("There are 2 messages on the queue.", runner.runReport(queue2));
    }

    public void testTextReportSingleStringParam()
    {
        Queue queue2 = createMockQueue(mock(ServerMessage.class), mock(ServerMessage.class));

        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("stringParam", new String[]{"hello world"});
        ReportRunner<String> runner =
                (ReportRunner<String>) ReportRunner.createRunner(TestTextReport.NAME, parameterMap);
        assertEquals("There are 2 messages on the queue. stringParam = hello world.", runner.runReport(queue2));
    }

    public void testTextReportSingleStringArrayParam()
    {
        Queue queue = createMockQueue();

        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("stringArrayParam", new String[] { "hello world", "goodbye"});
        ReportRunner<String> runner = (ReportRunner<String>) ReportRunner.createRunner(TestTextReport.NAME, parameterMap);
        assertEquals("There are 0 messages on the queue. stringArrayParam = [hello world, goodbye].", runner.runReport(queue));

    }


    public void testTextReportBothParams()
    {
        Queue queue = createMockQueue();

        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("stringParam", new String[]{"hello world"});
        parameterMap.put("stringArrayParam", new String[] { "hello world", "goodbye"});
        ReportRunner<String> runner = (ReportRunner<String>) ReportRunner.createRunner(TestTextReport.NAME, parameterMap);
        assertEquals("There are 0 messages on the queue. stringParam = hello world. stringArrayParam = [hello world, goodbye].", runner.runReport(queue));

    }

    public void testInvalidReportName()
    {
        try
        {
            ReportRunner.createRunner("unknown", Collections.<String, String[]>emptyMap());
            fail("Unknown report name should throw exception");
        }
        catch(IllegalArgumentException e)
        {
            assertEquals("Unknown report: unknown", e.getMessage());
        }
    }

    public void testBinaryReportWithLimit() throws Exception
    {
        Queue queue = createMockQueue(createMessageWithAppProperties(Collections.<String,Object>singletonMap("key",1)),
                                      createMessageWithAppProperties(Collections.<String,Object>singletonMap("key",2)),
                                      createMessageWithAppProperties(Collections.<String, Object>singletonMap("key", 3)),
                                      createMessageWithAppProperties(Collections.<String, Object>singletonMap("key", 4)));
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("propertyName", new String[]{"key"});
        parameterMap.put("limit", new String[] { "3" });

        ReportRunner<byte[]> runner = (ReportRunner<byte[]>) ReportRunner.createRunner(TestBinaryReport.NAME, parameterMap);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream objects = new ObjectOutputStream(bytes);
        objects.writeObject(Integer.valueOf(1));
        objects.writeObject(Integer.valueOf(2));
        objects.writeObject(Integer.valueOf(3));
        objects.flush();
        byte[] expected = bytes.toByteArray();
        byte[] actual = runner.runReport(queue);
        assertTrue("Output not as expected", Arrays.equals(expected, actual));
    }

    private ServerMessage<?> createMessageWithAppProperties(final Map<String,Object> props)
    {
        ServerMessage<?> message = mock(ServerMessage.class);
        final AMQMessageHeader header = mock(AMQMessageHeader.class);
        when(message.getMessageHeader()).thenReturn(header);
        final ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        when(header.getHeader(headerNameCaptor.capture())).thenAnswer(new Answer<Object>()
        {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                String header = headerNameCaptor.getValue();
                return props.get(header);
            }
        });
        when(header.getHeaderNames()).thenReturn(props.keySet());
        return message;
    }

    private Queue createMockQueue(final ServerMessage<?>... messages)
    {
        final AMQQueue queue = mock(AMQQueue.class);
        final ArgumentCaptor<QueueEntryVisitor> captor = ArgumentCaptor.forClass(QueueEntryVisitor.class);
        doAnswer(new Answer()
        {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable
            {
                QueueEntryVisitor visitor = captor.getValue();
                for(ServerMessage<?> message : messages)
                {
                    if(visitor.visit(makeEntry(queue, message)))
                    {
                        break;
                    }
                }
                return null;
            }
        }).when(queue).visit(captor.capture());
        return queue;
    }

    private QueueEntry makeEntry(final AMQQueue queue, final ServerMessage<?> message)
    {
        QueueEntry entry = mock(QueueEntry.class);
        when(entry.getQueue()).thenReturn(queue);
        when(entry.getMessage()).thenReturn(message);
        return entry;
    }
}
