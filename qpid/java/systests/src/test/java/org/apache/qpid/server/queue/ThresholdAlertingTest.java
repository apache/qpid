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
package org.apache.qpid.server.queue;

import org.apache.qpid.testutil.QpidClientConnection;
import org.apache.qpid.client.transport.TransportConnection;
import junit.framework.TestCase;

/**
 * User donated test with user ref 402 - checking alert on queue depth (measured in *bytes*)
 */
public class ThresholdAlertingTest extends TestCase {
  protected final String BROKER = "vm://:1";
  protected QpidClientConnection conn;
  protected final String queue = "direct://amq.direct//queue";

  protected Integer byteLimit = 4235264;  // 4MB
  protected Integer chunkSizeBytes = 100 * 1024; // 100kB
  protected String payload;

  protected void log(String msg)
  {
    System.out.println(msg);
  }

  protected void generatePayloadOfSize(Integer numBytes)
  {
    payload = new String(new byte[numBytes]);
  }

  protected void setUp() throws Exception
  {
    super.setUp();
    TransportConnection.createVMBroker(1);
    conn = new QpidClientConnection(BROKER);

    conn.connect();
    // clear queue
    log("setup: clearing test queue");
    conn.consume(queue, 2000);

    generatePayloadOfSize(chunkSizeBytes);
  }

  @Override
  protected void tearDown() throws Exception
  {
    super.tearDown();
    conn.disconnect();
  }

  /** put a message */
  public void testSingleChunk() throws Exception
  {
    assertEquals("payload not correct size", chunkSizeBytes.intValue(), payload.getBytes().length);
    int copies = 1;
    conn.put(queue, payload, copies);
    log("put " + copies + " copies for total bytes: " + copies * chunkSizeBytes);
  }

  /** fill queue to be near but below threshold */
  public void testNearThreshold() throws Exception
  {
    assertEquals("payload not correct size", chunkSizeBytes.intValue(), payload.getBytes().length);
    int copies = 40;
    conn.put(queue, payload, copies);

    //now check what the queue depth is ?

    log("put " + copies + " copies for total bytes: " + copies * chunkSizeBytes);
  }
}

