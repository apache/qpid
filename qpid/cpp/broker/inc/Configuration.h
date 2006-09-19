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
#ifndef _Configuration_
#define _Configuration_

#include <cstdlib>
#include <iostream>
#include <vector>

namespace qpid {
    namespace broker {
        class Configuration{
            class Option{
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
            };

            BoolOption trace;
            IntOption port;
            IntOption workerThreads;
            IntOption maxConnections;
            IntOption connectionBacklog;
            StringOption acceptor;
            BoolOption help;

            typedef std::vector<Option*>::iterator op_iterator;
            std::vector<Option*> options;

        public:
            class ParseException{
            public:
                const std::string& error;
                ParseException(const std::string& _error) : error(_error) {}
            };


            Configuration();
            ~Configuration();

            void parse(int argc, char** argv);

            bool isHelp();
            bool isTrace();
            int getPort();
            int getWorkerThreads();
            int getMaxConnections();
            int getConnectionBacklog();
            const std::string& getAcceptor();

            void usage();
        };
    }
}


#endif
