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
#include "AddressParser.h"
#include "AddressImpl.h"
#include "qpid/framing/Uuid.h"
#include <boost/format.hpp>

namespace qpid {
namespace messaging {

using namespace qpid::types;

AddressParser::AddressParser(const std::string& s) : input(s), current(0) {}

bool AddressParser::error(const std::string& message)
{
    throw MalformedAddress((boost::format("%1%, character %2% of %3%") % message % current % input).str());
}

bool AddressParser::parse(Address& address)
{
    std::string name;
    if (readName(name)) {
        if (name.find('#') == 0) {
            name = qpid::framing::Uuid(true).str() + name;
            AddressImpl::setTemporary(address, true);
        }
        address.setName(name);
        if (readChar('/')) {
            std::string subject;
            readSubject(subject);
            address.setSubject(subject);
        }
        if (readChar(';')) {
            Variant options = Variant::Map();
            if (readMap(options)) {
                address.setOptions(options.asMap());
            }
        }
        //skip trailing whitespace
        while (!eos() && iswhitespace()) ++current;
        return eos() || error("Unexpected chars in address: " + input.substr(current));
    } else {
        return input.empty() || error("Expected name");
    }
}

bool AddressParser::parseMap(Variant::Map& map)
{
    if (readChar('{')) { 
        readMapEntries(map);
        return readChar('}') || error("Unmatched '{'!");
    } else {
        return false;
    }
}

bool AddressParser::parseList(Variant::List& list)
{
    if (readChar('[')) {
        readListItems(list);
        return readChar(']') || error("Unmatched '['!");
    } else {
        return false;
    }
}


bool AddressParser::readList(Variant& value)
{
    if (readChar('[')) {
        value = Variant::List();
        readListItems(value.asList());
        return readChar(']') || error("Unmatched '['!");
    } else {
        return false;
    }
}

void AddressParser::readListItems(Variant::List& list)
{
    Variant item;
    while (readValueIfExists(item)) {
        list.push_back(item);
        if (!readChar(',')) break;
    }
}

bool AddressParser::readMap(Variant& value)
{
    if (readChar('{')) {
        value = Variant::Map();
        readMapEntries(value.asMap());
        return readChar('}') || error("Unmatched '{'!");
    } else {
        return false;
    }
}

void AddressParser::readMapEntries(Variant::Map& map)
{
    while (readKeyValuePair(map) && readChar(',')) {}
}

bool AddressParser::readKeyValuePair(Variant::Map& map)
{
    std::string key;
    Variant value;
    if (readKey(key)) {
        if (readChar(':') && readValue(value)) {
            map[key] = value;
            return true;
        } else {
            return error("Bad key-value pair, expected ':'");
        }
    } else {
        return false;
    }
}

bool AddressParser::readKey(std::string& key)
{
    return readWord(key) || readQuotedString(key);
}

bool AddressParser::readValue(Variant& value)
{
    return readValueIfExists(value) || error("Expected value");
}

bool AddressParser::readValueIfExists(Variant& value)
{
    return readSimpleValue(value) || readQuotedValue(value) || 
        readMap(value)  || readList(value);
}

bool AddressParser::readString(std::string& value, char delimiter)
{
    if (readChar(delimiter)) {
        std::string::size_type start = current;
        while (!eos()) {
            if (input.at(current) == delimiter) {
                if (current > start) {
                    value = input.substr(start, current - start);
                } else {
                    value = "";
                }
                ++current;
                return true;
            } else {
                ++current;
            }
        }
        return error("Unmatched delimiter");
    } else {
        return false;
    }
}

bool AddressParser::readName(std::string& name)
{
    return readQuotedString(name) || readWord(name, "/;");
}

bool AddressParser::readSubject(std::string& subject)
{
    return readQuotedString(subject) || readWord(subject, ";");
}

bool AddressParser::readQuotedString(std::string& s)
{
    return readString(s, '"') || readString(s, '\'');
}

bool AddressParser::readQuotedValue(Variant& value)
{
    std::string s;
    if (readQuotedString(s)) {
        value = s;
        value.setEncoding("utf8");
        return true;
    } else {
        return false;
    }
}

bool AddressParser::readSimpleValue(Variant& value)
{
    std::string s;
    if (readWord(s)) {
        value.parse(s);
        if (value.getType() == VAR_STRING) value.setEncoding("utf8");
        return true;
    } else {
        return false;
    }
}

bool AddressParser::readWord(std::string& value, const std::string& delims)
{
    //skip leading whitespace
    while (!eos() && iswhitespace()) ++current;

    //read any number of non-whitespace, non-reserved chars into value
    std::string::size_type start = current;
    while (!eos() && !iswhitespace() && !in(delims)) ++current;

    if (current > start) {
        value = input.substr(start, current - start);
        return true;
    } else {
        return false;
    }
}

bool AddressParser::readChar(char c)
{
    while (!eos()) {
        if (iswhitespace()) {
            ++current;
        } else if (input.at(current) == c) {
            ++current;
            return true;
        } else {
            return false;
        }
    }
    return false;
}

bool AddressParser::iswhitespace()
{
    return ::isspace(input.at(current));
}

bool AddressParser::isreserved()
{
    return in(RESERVED);
}

bool AddressParser::in(const std::string& chars)
{
    return chars.find(input.at(current)) != std::string::npos;
}

bool AddressParser::eos()
{
    return current >= input.size();
}

const std::string AddressParser::RESERVED = "\'\"{}[],:/";

}} // namespace qpid::messaging
