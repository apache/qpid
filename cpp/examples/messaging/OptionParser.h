#ifndef OPTIONPARSER_H
#define OPTIONPARSER_H

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

#include <map>
#include <string>
#include <vector>

class Option;

class OptionParser
{
  public:
    OptionParser(const std::string& usage, const std::string& description);
    ~OptionParser();
    void add(const std::string& name, std::string& value, const std::string& description = std::string());
    void add(const std::string& name, int& value, const std::string& description = std::string());
    void add(const std::string& name, bool& value, const std::string& description = std::string());
    void add(const std::string& name, std::vector<std::string>& value, const std::string& description = std::string());
    bool parse(int argc, char** argv);
    void error(const std::string& message);
    std::vector<std::string>& getArguments();
  private:
    typedef std::vector<Option*> Options;

    const std::string summary;
    const std::string description;
    bool help;
    Options options;
    std::vector<std::string> arguments;

    void add(Option*);
    Option* getOption(const std::string& argument);
};

#endif  /*!OPTIONPARSER_H*/
