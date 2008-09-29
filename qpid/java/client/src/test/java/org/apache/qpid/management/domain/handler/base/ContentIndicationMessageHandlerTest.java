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
package org.apache.qpid.management.domain.handler.base;

import java.nio.ByteBuffer;

import org.apache.qpid.management.TestConstants;
import org.apache.qpid.management.domain.model.type.Binary;
import org.apache.qpid.transport.codec.ManagementDecoder;
import org.apache.qpid.transport.codec.ManagementEncoder;

import junit.framework.TestCase;

/**
 * Test case for Content indication message handler (base class).
 * 
 * @author Andrea Gazzarini
 */
public class ContentIndicationMessageHandlerTest extends TestCase
{      
    /**
     * Tests the execution of the process method when the message is processed correctly.
     */
    public void testProcessOk() {
        final String expectedPackageName = "org.apache.qpid.broker";
        final String expectedClassName ="connection";
        final long expectedMessageTimestamp = System.currentTimeMillis();
        final long expectedCreationTime = expectedMessageTimestamp - 1000;
        final long expectedDeletionTime = 0;   
        final Binary expectedClassHash = new Binary(new byte[]{9,9,9,9,8,8,8,8,7,7,7,7,6,6,6,6});
        final Binary expectedObjectId = new Binary(new byte[]{1,2,3,4,5,6,7,8,9,0,11,12,13,14,15,16});
        final Binary expectedBody = new Binary(new byte[]{1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4});
        
        ContentIndicationMessageHandler mockHandler = new ContentIndicationMessageHandler()
        {
            @Override
            protected void updateDomainModel (String packageName, String className, Binary classHash, Binary objectId,
                    long timeStampOfCurrentSample, long timeObjectWasCreated, long timeObjectWasDeleted, byte[] contentData)
            {
                assertEquals(expectedPackageName,packageName);
                assertEquals(expectedClassName,className);
                assertEquals(expectedClassHash,classHash);
                assertEquals(expectedMessageTimestamp,timeStampOfCurrentSample);
                assertEquals(expectedCreationTime,timeObjectWasCreated);
                assertEquals(expectedDeletionTime,timeObjectWasDeleted);
                assertEquals(expectedObjectId,objectId);                
                assertEquals(expectedBody,new Binary(contentData));
            }
            
            @Override
            void removeObjectInstance (String packageName, String className, Binary classHash, Binary objectId)
            {
                fail("The object shouldn't be deleted because deletion time was set to 0!");
            }
        };
        mockHandler.setDomainModel(TestConstants.DOMAIN_MODEL);
        
        ByteBuffer buffer = ByteBuffer.allocate(1000);
        ManagementEncoder encoder = new ManagementEncoder(buffer);
        
        encoder.writeStr8(expectedPackageName);
        encoder.writeStr8(expectedClassName);
        expectedClassHash.encode(encoder);
        encoder.writeDatetime(expectedMessageTimestamp);
        encoder.writeDatetime(expectedCreationTime);
        encoder.writeDatetime(expectedDeletionTime);
        expectedObjectId.encode(encoder);
        expectedBody.encode(encoder);
        
        buffer.flip();
        ManagementDecoder decoder = new ManagementDecoder();
        decoder.init(buffer);
        
        mockHandler.process(decoder, 1);
    }
    
    /**
     * Tests the behaviour of the objectHasBeenRemoved method().
     */
    public void testObjectHasBeenRemoved() 
    {
        ContentIndicationMessageHandler mockHandler = new ContentIndicationMessageHandler()
        {
            @Override
            protected void updateDomainModel (String packageName, String className, Binary classHash, Binary objectId,
                    long timeStampOfCurrentSample, long timeObjectWasCreated, long timeObjectWasDeleted, byte[] contentData)
            {
            }
        };
        
        long deletionTimestamp = 0;
        long now = System.currentTimeMillis();
        
        assertFalse(mockHandler.objectHasBeenRemoved(deletionTimestamp, now));
        
        deletionTimestamp = now + 1000;
        assertFalse(mockHandler.objectHasBeenRemoved(deletionTimestamp, now));
        
        deletionTimestamp = now - 1000;
        assertTrue(mockHandler.objectHasBeenRemoved(deletionTimestamp, now));
    }
}