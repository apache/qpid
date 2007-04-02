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
#include "../broker/LazyLoadedContent.h"
#include "AMQP_HighestVersion.h"
#include "../broker/NullMessageStore.h"
#include "qpid_test_plugin.h"
#include <iostream>
#include <list>
#include <sstream>
#include "../framing/AMQFrame.h"
#include "MockChannel.h"
using std::list;
using std::string;
using boost::dynamic_pointer_cast;
using namespace qpid::broker;
using namespace qpid::framing;



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

        void loadContent(PersistableMessage&, string& data, uint64_t offset, uint32_t length)
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
        uint32_t framesize = 5;
        string out[] = {"abcde", "fghij", "klmno", "pqrst", "uvwxy", "z"};
        load(data, 6, out, framesize);
    }

    void testWhole()
    {
        string data = "abcdefghijklmnopqrstuvwxyz";
        uint32_t framesize = 50;
        string out[] = {data};
        load(data, 1, out, framesize);
    }

    void testHalved()
    {
        string data = "abcdefghijklmnopqrstuvwxyz";
        uint32_t framesize = 13;
        string out[] = {"abcdefghijklm", "nopqrstuvwxyz"};
        load(data, 2, out, framesize);
    }

    void load(string& in, size_t outCount, string* out, uint32_t framesize)
    {
        TestMessageStore store(in);
        LazyLoadedContent content(&store, 0, in.size());
        MockChannel channel(3);
        content.send(channel, framesize);         
        CPPUNIT_ASSERT_EQUAL(outCount, channel.out.frames.size());

        for (unsigned int i = 0; i < outCount; i++) {
            AMQContentBody::shared_ptr chunk(dynamic_pointer_cast<AMQContentBody, AMQBody>(channel.out.frames[i].getBody()));
            CPPUNIT_ASSERT(chunk);
            CPPUNIT_ASSERT_EQUAL(out[i], chunk->getData());
            CPPUNIT_ASSERT_EQUAL(
                ChannelId(3), channel.out.frames[i].getChannel());
        }
    }
};

// Make this test suite a plugin.
CPPUNIT_PLUGIN_IMPLEMENT();
CPPUNIT_TEST_SUITE_REGISTRATION(LazyLoadedContentTest);

