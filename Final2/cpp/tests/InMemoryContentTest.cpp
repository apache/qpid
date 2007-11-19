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
#include <InMemoryContent.h>
#include <qpid_test_plugin.h>
#include <AMQP_HighestVersion.h>
#include <iostream>
#include <list>

using std::list;
using std::string;
using boost::dynamic_pointer_cast;
using namespace qpid::broker;
using namespace qpid::framing;

struct DummyHandler : OutputHandler{
    std::vector<AMQFrame*> frames; 

    virtual void send(AMQFrame* frame){
        frames.push_back(frame);
    }
};

class InMemoryContentTest : public CppUnit::TestCase  
{
        CPPUNIT_TEST_SUITE(InMemoryContentTest);
        CPPUNIT_TEST(testRefragmentation);
        CPPUNIT_TEST_SUITE_END();

public:
    void testRefragmentation()
    {
        {//no remainder
            string out[] = {"abcde", "fghij", "klmno", "pqrst"};
            string in[] = {out[0] + out[1], out[2] + out[3]};        
            refragment(2, in, 4, out);
        }
        {//remainder for last frame
            string out[] = {"abcde", "fghij", "klmno", "pqrst", "uvw"};
            string in[] = {out[0] + out[1], out[2] + out[3] + out[4]};        
            refragment(2, in, 5, out);
        }
    }


    void refragment(size_t inCount, string* in, size_t outCount, string* out, u_int32_t framesize = 5)
    {
        InMemoryContent content;
        DummyHandler handler;
        u_int16_t channel = 3;

        addframes(content, inCount, in);
        content.send(highestProtocolVersion, &handler, channel, framesize);         
        check(handler, channel, outCount, out);
    }

    void addframes(InMemoryContent& content, size_t frameCount, string* frameData)
    {
        for (unsigned int i = 0; i < frameCount; i++) {
            AMQContentBody::shared_ptr frame(new AMQContentBody(frameData[i]));
            content.add(frame);
        }
    }

    void check(DummyHandler& handler, u_int16_t channel, size_t expectedChunkCount, string* expectedChunks)
    {
        CPPUNIT_ASSERT_EQUAL(expectedChunkCount, handler.frames.size());

        for (unsigned int i = 0; i < expectedChunkCount; i++) {
            AMQContentBody::shared_ptr chunk(dynamic_pointer_cast<AMQContentBody, AMQBody>(handler.frames[i]->getBody()));
            CPPUNIT_ASSERT(chunk);
            CPPUNIT_ASSERT_EQUAL(expectedChunks[i], chunk->getData());
            CPPUNIT_ASSERT_EQUAL(channel, handler.frames[i]->getChannel());
        }
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(InMemoryContentTest);

