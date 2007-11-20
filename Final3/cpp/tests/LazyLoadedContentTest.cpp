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
#include <LazyLoadedContent.h>
#include <AMQP_HighestVersion.h>
#include <NullMessageStore.h>
#include <qpid_test_plugin.h>
#include <iostream>
#include <list>
#include <sstream>

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


class LazyLoadedContentTest : public CppUnit::TestCase  
{
        CPPUNIT_TEST_SUITE(LazyLoadedContentTest);
        CPPUNIT_TEST(testFragmented);
        CPPUNIT_TEST(testWhole);
        CPPUNIT_TEST(testHalved);
        CPPUNIT_TEST_SUITE_END();

    class TestMessageStore : public NullMessageStore
    {
        const string content;
        
    public:
        TestMessageStore(const string& _content) : content(_content) {}

        void loadContent(Message* const, string& data, u_int64_t offset, u_int32_t length)
        {
            if (offset + length <= content.size()) {
                data = content.substr(offset, length);
            } else{
                std::stringstream error;
                error << "Invalid segment: offset=" << offset << ", length=" << length << ", content_length=" << content.size();
                throw qpid::Exception(error.str());
            }
        }
    };


public:
    void testFragmented()
    {
        string data = "abcdefghijklmnopqrstuvwxyz";
        u_int32_t framesize = 5;
        string out[] = {"abcde", "fghij", "klmno", "pqrst", "uvwxy", "z"};
        load(data, 6, out, framesize);
    }

    void testWhole()
    {
        string data = "abcdefghijklmnopqrstuvwxyz";
        u_int32_t framesize = 50;
        string out[] = {data};
        load(data, 1, out, framesize);
    }

    void testHalved()
    {
        string data = "abcdefghijklmnopqrstuvwxyz";
        u_int32_t framesize = 13;
        string out[] = {"abcdefghijklm", "nopqrstuvwxyz"};
        load(data, 2, out, framesize);
    }

    void load(string& in, size_t outCount, string* out, u_int32_t framesize)
    {
        TestMessageStore store(in);
        LazyLoadedContent content(&store, 0, in.size());
        DummyHandler handler;
        u_int16_t channel = 3;
        content.send(highestProtocolVersion, &handler, channel, framesize);         
        check(handler, channel, outCount, out);
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
CPPUNIT_TEST_SUITE_REGISTRATION(LazyLoadedContentTest);

