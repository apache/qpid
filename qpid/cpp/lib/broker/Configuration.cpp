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
#include <Configuration.h>
#include <string.h>

using namespace qpid::broker;
using namespace std;

Configuration::Configuration() : 
    trace('t', "trace", "Print incoming & outgoing frames to the console (default=false)", false),
    port('p', "port", "Sets the port to listen on (default=5672)", 5672),
    workerThreads("worker-threads", "Sets the number of worker threads to use (default=5).", 5),
    maxConnections("max-connections", "Sets the maximum number of connections the broker can accept (default=500).", 500),
    connectionBacklog("connection-backlog", "Sets the connection backlog for the servers socket (default=10)", 10),
    store('s', "store", "Sets the message store module to use (default='' which implies no store)", ""),
    help("help", "Prints usage information", false)
{
    options.push_back(&trace);
    options.push_back(&port);
    options.push_back(&workerThreads);
    options.push_back(&maxConnections);
    options.push_back(&connectionBacklog);
    options.push_back(&store);
    options.push_back(&help);
}

Configuration::~Configuration(){}

void Configuration::parse(int argc, char** argv){
    int position = 1;
    while(position < argc){
        bool matched(false);
        for(op_iterator i = options.begin(); i < options.end() && !matched; i++){
            matched = (*i)->parse(position, argv, argc);
        }
        if(!matched){
            std::cout<< "Warning: skipping unrecognised option " << argv[position] << std::endl;
            position++;
        }
    }
}

void Configuration::usage(){
    for(op_iterator i = options.begin(); i < options.end(); i++){
        (*i)->print(std::cout);
    }
}

bool Configuration::isHelp() const {
    return help.getValue();
}

bool Configuration::isTrace() const {
    return trace.getValue();
}

int Configuration::getPort() const {
    return port.getValue();
}

int Configuration::getWorkerThreads() const {
    return workerThreads.getValue();
}

int Configuration::getMaxConnections() const {
    return maxConnections.getValue();
}

int Configuration::getConnectionBacklog() const {
    return connectionBacklog.getValue();
}

const std::string& Configuration::getStore() const {
    return store.getValue();
}

Configuration::Option::Option(const char _flag, const string& _name, const string& _desc) : 
    flag(string("-") + _flag), name("--" +_name), desc(_desc) {}

Configuration::Option::Option(const string& _name, const string& _desc) : 
    flag(""), name("--" + _name), desc(_desc) {}

Configuration::Option::~Option(){}

bool Configuration::Option::match(const string& arg){
    return flag == arg || name == arg;
}

bool Configuration::Option::parse(int& i, char** argv, int argc){
    const string arg(argv[i]);
    if(match(arg)){
        if(needsValue()){
            if(++i < argc) setValue(argv[i]);
            else throw ParseException("Argument " + arg + " requires a value!");
        }else{
            setValue("");
        }
        i++;
        return true;
    }else{
        return false;
    }
}

void Configuration::Option::print(ostream& out) const {
    out << "    ";
    if(flag.length() > 0){
        out << flag << " or ";
    }
    out << name;
    if(needsValue()) out << "<value>";
    out << std::endl;
    out << "            " << desc << std::endl;
}


// String Option:

Configuration::StringOption::StringOption(const char _flag, const string& _name, const string& _desc, const string _value) : 
    Option(_flag,_name,_desc), defaultValue(_value), value(_value) {}

Configuration::StringOption::StringOption(const string& _name, const string& _desc, const string _value) : 
    Option(_name,_desc), defaultValue(_value), value(_value) {}

Configuration::StringOption::~StringOption(){}

const string& Configuration::StringOption::getValue() const {
    return value;
}

bool Configuration::StringOption::needsValue() const {
    return true;
}

void Configuration::StringOption::setValue(const std::string& _value){
    value = _value;
}

// Int Option:

Configuration::IntOption::IntOption(const char _flag, const string& _name, const string& _desc, const int _value) : 
    Option(_flag,_name,_desc), defaultValue(_value), value(_value) {}

Configuration::IntOption::IntOption(const string& _name, const string& _desc, const int _value) : 
    Option(_name,_desc), defaultValue(_value), value(_value) {}

Configuration::IntOption::~IntOption(){}

int Configuration::IntOption::getValue() const {
    return value;
}

bool Configuration::IntOption::needsValue() const {
    return true;
}

void Configuration::IntOption::setValue(const std::string& _value){
    value = atoi(_value.c_str());
}

// Bool Option:

Configuration::BoolOption::BoolOption(const char _flag, const string& _name, const string& _desc, const bool _value) : 
    Option(_flag,_name,_desc), defaultValue(_value), value(_value) {}

Configuration::BoolOption::BoolOption(const string& _name, const string& _desc, const bool _value) : 
    Option(_name,_desc), defaultValue(_value), value(_value) {}

Configuration::BoolOption::~BoolOption(){}

bool Configuration::BoolOption::getValue() const {
    return value;
}

bool Configuration::BoolOption::needsValue() const {
    return false;
}

void Configuration::BoolOption::setValue(const std::string& /*not required*/){
    //BoolOptions have no value. The fact that the option is specified
    //implies the value is true.
    value = true;
}
