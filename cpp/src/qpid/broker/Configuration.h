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
#ifndef _Configuration_
#define _Configuration_

#include <cstdlib>
#include <iostream>
#include <vector>
#include "qpid/Exception.h"

namespace qpid {
namespace broker {
class Configuration{

    class Option {
        const std::string flag;
        const std::string name;
        const std::string desc;

        bool match(const std::string& arg);

      protected:
        virtual bool needsValue() const = 0;
        virtual void setValue(const std::string& value) = 0;

      public:
        Option(const char flag, const std::string& name, const std::string& desc);
        Option(const std::string& name, const std::string& desc);
        virtual ~Option();

        bool parse(int& i, char** argv, int argc);
        void print(std::ostream& out) const;
    };

    class IntOption : public Option{
        const int defaultValue;
        int value;
      public:
        IntOption(char flag, const std::string& name, const std::string& desc, const int value = 0);
        IntOption(const std::string& name, const std::string& desc, const int value = 0);
        virtual ~IntOption();

        int getValue() const;
        virtual bool needsValue() const;
        virtual void setValue(const std::string& value);
        virtual void setValue(int _value) { value = _value; }
    };

    class LongOption : public Option{
        const long defaultValue;
        int value;
      public:
        LongOption(char flag, const std::string& name, const std::string& desc, const long value = 0);
        LongOption(const std::string& name, const std::string& desc, const long value = 0);
        virtual ~LongOption();

        long getValue() const;
        virtual bool needsValue() const;
        virtual void setValue(const std::string& value);
        virtual void setValue(int _value) { value = _value; }
    };

    class StringOption : public Option{
        const std::string defaultValue;
        std::string value;
      public:
        StringOption(char flag, const std::string& name, const std::string& desc, const std::string value = "");
        StringOption(const std::string& name, const std::string& desc, const std::string value = "");
        virtual ~StringOption();

        const std::string& getValue() const;
        virtual bool needsValue() const;
        virtual void setValue(const std::string& value);
    };

    class BoolOption : public Option{
        const bool defaultValue;
        bool value;
      public:
        BoolOption(char flag, const std::string& name, const std::string& desc, const bool value = 0);
        BoolOption(const std::string& name, const std::string& desc, const bool value = 0);
        virtual ~BoolOption();

        bool getValue() const;
        virtual bool needsValue() const;
        virtual void setValue(const std::string& value);
        virtual void setValue(bool _value) { value = _value; }
    };

    BoolOption daemon;
    BoolOption trace;
    IntOption port;
    IntOption workerThreads;
    IntOption maxConnections;
    IntOption connectionBacklog;
    StringOption store;
    LongOption stagingThreshold;
    BoolOption help;
    BoolOption version;
    char const *programName;

    typedef std::vector<Option*>::iterator op_iterator;
    std::vector<Option*> options;

  public:

    struct BadOptionException : public Exception {
        template<class T>
        BadOptionException(const T& msg) : Exception(msg) {}
    };
            

    class ParseException : public Exception {
      public:
        template <class T>
        ParseException(const T& msg) : Exception(msg) {}
    };


    Configuration();
    ~Configuration();

    void parse(char const*, int argc, char** argv);

    bool isHelp() const;
    bool isVersion() const;
    bool isDaemon() const;
    bool isTrace() const;
    int getPort() const;
    int getWorkerThreads() const;
    int getMaxConnections() const;
    int getConnectionBacklog() const;
    const std::string& getStore() const;
    long getStagingThreshold() const;

    void setHelp(bool b) { help.setValue(b); }
    void setVersion(bool b) { version.setValue(b); }
    void setDaemon(bool b) { daemon.setValue(b); }
    void setTrace(bool b) { trace.setValue(b); }
    void setPort(int i) { port.setValue(i); }
    void setWorkerThreads(int i) { workerThreads.setValue(i); }
    void setMaxConnections(int i) { maxConnections.setValue(i); }
    void setConnectionBacklog(int i) { connectionBacklog.setValue(i); }
    void setStore(const std::string& s) { store.setValue(s); }
    void setStagingThreshold(long l) { stagingThreshold.setValue(l); }

    void usage();
};
}
}


#endif
