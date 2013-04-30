#ifndef SELECTOR_H
#define SELECTOR_H

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

#include "qpid/log/Statement.h"
#include "qpid/CommonImportExport.h"
#include <vector>

namespace qpid {
namespace log {
struct Options;

/**
 * SelectorElement parses a cli/mgmt enable/disable entry into usable fields
 * where cliEntry = [!]LEVEL[+-][:PATTERN]
 */
struct SelectorElement {
    QPID_COMMON_EXTERN SelectorElement(const std::string cliEntry);
    std::string levelStr;
    std::string patternStr;
    Level level;
    Category category;
    bool isDisable;
    bool isCategory;
    bool isLevelAndAbove;
    bool isLevelAndBelow;
};

/**
 * A selector identifies the set of log messages to enable.
 *
 * Thread object unsafe, pass-by-value type.
 */
class Selector {
  public:
    /** Empty selector selects nothing */
    QPID_COMMON_EXTERN Selector();

    /** Set selector from Options */
    QPID_COMMON_EXTERN Selector(const Options&);

    /** Equavlient to: Selector s; s.enable(l, s) */
    QPID_COMMON_EXTERN Selector(Level l, const std::string& s=std::string());

    /** Selector from string */
    QPID_COMMON_EXTERN Selector(const std::string& selector);

    /** push option settings into runtime lookup structs */
    QPID_COMMON_EXTERN void enable(const std::string& enableStr);
    QPID_COMMON_EXTERN void disable(const std::string& disableStr);

    /**
     * Enable/disable messages with level in levels where the file
     * name contains substring. Empty string matches all.
     */
    QPID_COMMON_EXTERN void enable(Level level, const std::string& substring=std::string());
    QPID_COMMON_EXTERN void disable(Level level, const std::string& substring=std::string());

    /** Tests to determine if function names are in enable/disable tables */
    QPID_COMMON_EXTERN bool isEnabled(Level level, const char* function);
    QPID_COMMON_EXTERN bool isDisabled(Level level, const char* function);

    /** Test to determine if log Statement is enabled */
    QPID_COMMON_EXTERN bool isEnabled(Level level, const char* function, Category category);

  private:
    typedef std::vector<std::string> FunctionNameTable [LevelTraits::COUNT];
    FunctionNameTable enabledFunctions;   // log function names explicitly enabled
    FunctionNameTable disabledFunctions;  // log function names explicitly disabled
    bool enableFlags[LevelTraits::COUNT][CategoryTraits::COUNT];
    bool disableFlags[LevelTraits::COUNT][CategoryTraits::COUNT];

    bool lookupFuncName(Level level, const char* function, FunctionNameTable& table);
    /** Reset the category enable flags */
    QPID_COMMON_EXTERN void reset();
};


}} // namespace qpid::log


#endif  /*!SELECTOR_H*/
