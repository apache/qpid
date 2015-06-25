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
#include "OptionParser.h"
#include <qpid/types/Exception.h>
#include <algorithm>
#include <iostream>
#include <iomanip>
#include <sstream>
#include <cstdlib>

class Option
{
  public:
    Option(const std::string& name, const std::string& description);
    virtual ~Option() {}
    virtual void setValue(const std::string&) = 0;
    virtual bool isValueExpected() = 0;
    bool match(const std::string&);
    std::ostream& print(std::ostream& out);
  private:
    std::string longName;
    std::string shortName;
    std::string description;
    std::ostream& printNames(std::ostream& out);
  friend class OptionParser;
};

class StringOption : public Option
{
  public:
    StringOption(const std::string& name, const std::string& description, std::string& v) : Option(name, description), value(v) {}
    void setValue(const std::string& v) { value = v; }
    bool isValueExpected() { return true; }
  private:
    std::string& value;
};

class IntegerOption : public Option
{
  public:
    IntegerOption(const std::string& name, const std::string& description, int& v) : Option(name, description), value(v) {}
    void setValue(const std::string& v) { value = atoi(v.c_str()); }
    bool isValueExpected() { return true; }
  private:
    int& value;
};

class BooleanOption : public Option
{
  public:
    BooleanOption(const std::string& name, const std::string& description, bool& v) : Option(name, description), value(v) {}
    void setValue(const std::string&) { value = true; }
    bool isValueExpected() { return false; }
  private:
    bool& value;
};

class MultiStringOption : public Option
{
  public:
    MultiStringOption(const std::string& name, const std::string& description, std::vector<std::string>& v) : Option(name, description), value(v) {}
    void setValue(const std::string& v) { value.push_back(v); }
    bool isValueExpected() { return true; }
  private:
    std::vector<std::string>& value;
};

class OptionMatch
{
  public:
    OptionMatch(const std::string& argument);
    bool operator()(Option* option);
    bool isOption();
  private:
    std::string name;
};

class OptionsError : public qpid::types::Exception
{
  public:
    OptionsError(const std::string& message) : qpid::types::Exception(message) {}
};

Option::Option(const std::string& name, const std::string& desc) : description(desc)
{
    std::string::size_type i = name.find(",");
    if (i != std::string::npos) {
        longName = name.substr(0, i);
        if (i + 1 < name.size())
            shortName = name.substr(i+1);
    } else {
        longName = name;
    }
}

bool Option::match(const std::string& name)
{
    return name == longName || name == shortName;
}

std::ostream& Option::printNames(std::ostream& out)
{
    if (shortName.size()) {
        out << "-" << shortName;
        if (isValueExpected()) out << " VALUE";
        out << ", --" << longName;
        if (isValueExpected()) out << " VALUE";
    } else {
        out << "--" << longName;
        if (isValueExpected()) out << " VALUE";
    }
    return out;
}

std::ostream& Option::print(std::ostream& out)
{
    std::stringstream names;
    printNames(names);
    out << std::setw(30) << std::left << names.str() << description << std::endl;
    return out;
}

std::vector<std::string>& OptionParser::getArguments() { return arguments; }

void OptionParser::add(Option* option)
{
    options.push_back(option);
}

void OptionParser::add(const std::string& name, std::string& value, const std::string& description)
{
    add(new StringOption(name, description, value));
}
void OptionParser::add(const std::string& name, int& value, const std::string& description)
{
    add(new IntegerOption(name, description, value));
}
void OptionParser::add(const std::string& name, bool& value, const std::string& description)
{
    add(new BooleanOption(name, description, value));
}
void OptionParser::add(const std::string& name, std::vector<std::string>& value, const std::string& description)
{
    add(new MultiStringOption(name, description, value));
}

OptionMatch::OptionMatch(const std::string& argument)
{
    if (argument.find("--") == 0) {
        name = argument.substr(2);
    } else if (argument.find("-") == 0) {
        name = argument.substr(1);
    }
}

bool OptionMatch::operator()(Option* option)
{
    return option->match(name);
}

bool OptionMatch::isOption()
{
    return name.size() > 0;
}

OptionParser::OptionParser(const std::string& s, const std::string& d) : summary(s), description(d), help(false)
{
    add("help,h", help, "show this message");
}

Option* OptionParser::getOption(const std::string& argument)
{
    OptionMatch match(argument);
    if (match.isOption()) {
        Options::iterator i = std::find_if(options.begin(), options.end(), match);
        if (i == options.end()) {
            std::stringstream error;
            error << "Unrecognised option: " << argument;
            throw OptionsError(error.str());
        } else {
            return *i;
        }        
    } else {
        return 0;
    }
}

void OptionParser::error(const std::string& message)
{
    std::cout << summary << std::endl << std::endl;
    std::cerr << "Error: " << message << "; try --help for more information" << std::endl;
}

bool OptionParser::parse(int argc, char** argv)
{
    try {
        for (int i = 1; i < argc; ++i) {
            std::string argument = argv[i];
            Option* o = getOption(argument);
            if (o) {
                if (o->isValueExpected()) {
                    if (i + 1 < argc) {
                        o->setValue(argv[++i]);
                    } else {
                        std::stringstream error;
                        error << "Value expected for option " << o->longName;
                        throw OptionsError(error.str());
                    }
                } else {
                    o->setValue("");
                }
            } else {
                arguments.push_back(argument);
            }
        }
        if (help) {
            std::cout << summary << std::endl << std::endl;
            std::cout << description << std::endl << std::endl;
            std::cout << "Options: " << std::endl;
            for (Options::iterator i = options.begin(); i != options.end(); ++i) {
                (*i)->print(std::cout);
            }
            return false;
        } else {
            return true;
        }
    } catch (const std::exception& e) {
        error(e.what());
        return false;
    }
}


OptionParser::~OptionParser()
{
    for (Options::iterator i = options.begin(); i != options.end(); ++i) {        
        delete *i;
    }
}
