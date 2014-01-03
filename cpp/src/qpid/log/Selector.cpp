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

#include "qpid/log/Selector.h"
#include "qpid/log/Options.h"
#include <boost/bind.hpp>
#include <algorithm>
#include <string.h>

namespace qpid {
namespace log {

using namespace std;

const char LOG_SYMBOL_DISABLE  ('!');
const char LOG_SYMBOL_SEPERATOR(':');
const char LOG_SYMBOL_AND_ABOVE('+');
const char LOG_SYMBOL_AND_BELOW('-');

//
// Parse an enable or disable entry into usable fields.
// Throws if 'level' field is not recognized.
//
SelectorElement::SelectorElement(const std::string cliEntry) :
    level(qpid::log::debug),
    category(qpid::log::unspecified),
    isDisable(false),
    isCategory(false),
    isLevelAndAbove(false),
    isLevelAndBelow(false)
{
    if (cliEntry.empty())
        return;
    std::string working(cliEntry);
    if (LOG_SYMBOL_DISABLE == working[0]) {
        isDisable = true;
        working = working.substr(1);
    }
    size_t c=working.find(LOG_SYMBOL_SEPERATOR);
    if (c==string::npos) {
        levelStr=working;
    } else {
        levelStr=working.substr(0,c);
        patternStr=working.substr(c+1);
    }
    if (!levelStr.empty()) {
        if (levelStr[levelStr.size()-1]==LOG_SYMBOL_AND_ABOVE) {
            isLevelAndAbove = true;
            levelStr = levelStr.substr(0, levelStr.size()-1);
        } else if (levelStr[levelStr.size()-1]==LOG_SYMBOL_AND_BELOW) {
            isLevelAndBelow = true;
            levelStr = levelStr.substr(0, levelStr.size()-1);
        }
    }
    level = LevelTraits::level(levelStr);  // throws if bad level name
    isCategory = CategoryTraits::isCategory(patternStr);
    if (isCategory) {
        category = CategoryTraits::category(patternStr);
    }
}

// Empty selector
Selector::Selector() {
    reset();
}


// Selector from options
Selector::Selector(const Options& opt){
    reset();
    for_each(opt.selectors.begin(), opt.selectors.end(),
             boost::bind(&Selector::enable, this, _1));
    for_each(opt.deselectors.begin(), opt.deselectors.end(),
             boost::bind(&Selector::disable, this, _1));
}


// Selector from single level
Selector::Selector(Level l, const std::string& s) {
    reset();
    enable(l, s);
}


// Selector from single enable
Selector::Selector(const std::string& selector) {
    reset();
    enable(selector);
}


/**
 * Process a single CLI --log-enable option
 */
void Selector::enable(const string& enableStr) {
    if (enableStr.empty())
        return;
    SelectorElement se(enableStr);
    if (se.isDisable) {
        // Disable statements are allowed in an enable string as a convenient
        // way to process management strings that have enable/disable mixed.
        disable(enableStr);
    } else if (se.isLevelAndAbove) {
        for (int lvl = se.level; lvl < LevelTraits::COUNT; ++lvl) {
            if (se.isCategory) {
                enableFlags[lvl][se.category] = true;
            } else {
                enable(Level(lvl), se.patternStr);
            }
        }
    } else if (se.isLevelAndBelow) {
        for (int lvl = se.level; lvl >= 0; --lvl) {
            if (se.isCategory) {
                enableFlags[lvl][se.category] = true;
            } else {
                enable(Level(lvl), se.patternStr);
            }
        }
    } else {
        if (se.isCategory) {
            enableFlags[se.level][se.category] = true;
        } else {
            enable(se.level, se.patternStr);
        }
    }
}

void Selector::disable(const string& disableStr) {
    if (disableStr.empty())
        return;
    SelectorElement se(disableStr);
    if (se.isLevelAndAbove) {
        for (int lvl = se.level; lvl < LevelTraits::COUNT; ++lvl) {
            if (se.isCategory) {
                disableFlags[lvl][se.category] = true;
            } else {
                disable(Level(lvl), se.patternStr);
            }
        }
    } else if (se.isLevelAndBelow) {
        for (int lvl = se.level; lvl >= 0; --lvl) {
            if (se.isCategory) {
                disableFlags[lvl][se.category] = true;
            } else {
                disable(Level(lvl), se.patternStr);
            }
        }
    } else {
        if (se.isCategory) {
            disableFlags[se.level][se.category] = true;
        } else {
            disable(se.level, se.patternStr);
        }
    }
}


/**
* Enable/disable messages with level in levels where the file
* name contains substring.
*/
void Selector::enable(Level level, const std::string& substring) {
    enabledFunctions[level].push_back(substring);
}


void Selector::disable(Level level, const std::string& substring) {
    disabledFunctions[level].push_back(substring);
}


void Selector::reset() {
    // Initialize fields in a Selector that are not automatically set
    for (int lt = 0; lt < LevelTraits::COUNT; ++lt)
        for (int ct = 0; ct < CategoryTraits::COUNT; ++ct)
            enableFlags[lt][ct] = disableFlags[lt][ct] = false;
}


bool Selector::lookupFuncName(Level level, const char* function, FunctionNameTable& table) {
    const char* functionEnd = function+::strlen(function);
    for (std::vector<std::string>::iterator i=table[level].begin();
         i != table[level].end();
         ++i)
    {
        if (std::search(function, functionEnd, i->begin(), i->end()) != functionEnd)
            return true;
    }
    return false;
}


bool Selector::isEnabled(Level level, const char* function) {
    return lookupFuncName(level, function, enabledFunctions);
}

bool Selector::isDisabled(Level level, const char* function) {
    return lookupFuncName(level, function, disabledFunctions);
}

//
// isEnabled
//
// Determines if all the fields in this Selector enable or disable a
// level/function/category set from an actual QPID_LOG Statement.
//
bool Selector::isEnabled(Level level, const char* function, Category category) {
    if (level==critical)
        return true;                    // critical cannot be disabled
    if (isDisabled(level, function))
        return false;                   // Disabled by function name
    if (disableFlags[level][category])
        return false;                   // Disabled by category name
    if (isEnabled(level, function))
        return true;                    // Enabled by function name
    if (enableFlags[level][category])
        return true;                    // Enabled by category name
    else
        return false;                   // Unspecified defaults to disabled
}

}} // namespace qpid::log
