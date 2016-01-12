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

#include <qpid/messaging/Address.h>
#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Message_io.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>
#include <qpid/types/Variant.h>

#include <iostream>
#include <sstream>
#include <vector>
#include <ctime>

#include "OptionParser.h"

using namespace qpid::messaging;
using namespace qpid::types;

typedef std::vector<std::string> string_vector;

struct Options : OptionParser
{
    std::string url;
    std::string address;
    int timeout;
    bool durable;
    int count;
    std::string id;
    std::string replyto;
    string_vector properties;
    string_vector entries;
    std::string content;
    std::string connectionOptions;
    bool print;

    Options()
        : OptionParser("Usage: spout [OPTIONS] ADDRESS", "Send messages to the specified address"),
          url("127.0.0.1"),
          timeout(0),
          count(1),
          durable(false),
          print(false)
    {
        add("broker,b", url, "url of broker to connect to");
        add("timeout,t", timeout, "exit after the specified time");
        add("durable,d", durable, "make the message durable (def. transient)");
        add("count,c", count, "stop after count messages have been sent, zero disables");
        add("id,i", id, "use the supplied id instead of generating one");
        add("reply-to", replyto, "specify reply-to address");
        add("property,P", properties, "specify message property");
        add("map,M", entries, "specify entry for map content");
        add("content", content, "specify textual content");
        add("connection-options", connectionOptions, "connection options string in the form {name1:value1, name2:value2}");
        add("print", print, "print each message sent");
    }

    static bool nameval(const std::string& in, std::string& name, std::string& value)
    {
        std::string::size_type i = in.find("=");
        if (i == std::string::npos) {
            name = in;
            return false;
        } else {
            name = in.substr(0, i);
            if (i+1 < in.size()) {
                value = in.substr(i+1);
                return true;
            } else {
                return false;
            }
        }
    }

    static void setProperty(Message& message, const std::string& property)
    {
        std::string name;
        std::string value;
        if (nameval(property, name, value)) {
            message.getProperties()[name] = value;
            message.getProperties()[name].setEncoding("utf8");
        } else {
            message.getProperties()[name] = Variant();
        }
    }

    void setProperties(Message& message) const
    {
        for (string_vector::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            setProperty(message, *i);
        }
    }

    void setEntries(Variant::Map& content) const
    {
        for (string_vector::const_iterator i = entries.begin(); i != entries.end(); ++i) {
            std::string name;
            std::string value;
            if (nameval(*i, name, value)) {
                content[name] = value;
            } else {
                content[name] = Variant();
            }
        }
    }

    bool checkAddress()
    {
        if (getArguments().empty()) {
            error("Address is required");
            return false;
        } else {
            address = getArguments()[0];
            return true;
        }
    }

    bool isDurable() const
    {
      return durable;
    }
};

int main(int argc, char** argv)
{
    Options options;
    if (options.parse(argc, argv) && options.checkAddress()) {
        Connection connection(options.url, options.connectionOptions);
        try {
            connection.open();
            Session session = connection.createSession();
            Sender sender = session.createSender(options.address);

            Message message;
            message.setDurable(options.isDurable());
            options.setProperties(message);
            Variant& obj = message.getContentObject();
            if (options.entries.size()) {
                Variant::Map content;
                options.setEntries(content);
                obj = content;
            } else if (options.content.size()) {
                obj = options.content;
                obj.setEncoding("utf8");
            }
            std::time_t start = std::time(0);
            for (int count = 0; 
                (count < options.count || options.count == 0) && 
                (options.timeout == 0 || std::difftime(std::time(0), start) < options.timeout); 
                count++) {
                if (!options.replyto.empty()) message.setReplyTo(Address(options.replyto));
                std::string id = options.id.empty() ? Uuid(true).str() : options.id;
                std::stringstream spoutid;
                spoutid << id << ":" << count;
                message.getProperties()["spout-id"] = spoutid.str();
                if (options.print) std::cout << message << std::endl;
                sender.send(message);
            }
            session.sync();
            connection.close();
            return 0;
        } catch(const std::exception& error) {
            std::cout << error.what() << std::endl;
            connection.close();
        }
    }
    return 1;
}


