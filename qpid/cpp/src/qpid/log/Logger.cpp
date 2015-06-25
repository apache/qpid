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

#include "qpid/log/Logger.h"
#include "qpid/log/Options.h"
#include "qpid/log/SinkOptions.h"
#include "qpid/memory.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Time.h"
#include "qpid/DisableExceptionLogging.h"

#include "boost/version.hpp"
#if (BOOST_VERSION >= 104000)
#include <boost/serialization/singleton.hpp>
#else
#include <boost/pool/detail/singleton.hpp>
#endif

#include <boost/bind.hpp>
#include <boost/function.hpp>
#include <algorithm>
#include <sstream>
#include <iomanip>
#include <stdexcept>
#include <time.h>


namespace qpid {
namespace log {

using namespace std;

typedef sys::Mutex::ScopedLock ScopedLock;

inline void Logger::enable_unlocked(Statement* s) {
    s->enabled=selector.isEnabled(s->level, s->function, s->category);
}

Logger& Logger::instance() {
#if (BOOST_VERSION >= 104000)
    return boost::serialization::singleton<Logger>::get_mutable_instance();
#else
    return boost::details::pool::singleton_default<Logger>::instance();
#endif
}

Logger::Logger() : flags(0) {
    // Disable automatic logging in Exception constructors to avoid
    // re-entrant use of logger singleton if there is an error in
    // option parsing.
    DisableExceptionLogging del;

    // Initialize myself from env variables so all programs
    // (e.g. tests) can use logging even if they don't parse
    // command line args.
    Options opts;
    opts.parse(0, 0);
    configure(opts);
}

Logger::~Logger() {}

void Logger::select(const Selector& s) {
    ScopedLock l(lock);
    selector=s;
    std::for_each(statements.begin(), statements.end(),
                  boost::bind(&Logger::enable_unlocked, this, _1));
}

Logger::Output::Output()  {}
Logger::Output::~Output() {}

void Logger::log(const Statement& s, const std::string& msg) {
    // Format the message outside the lock.
    std::ostringstream os;
    if (!prefix.empty())
        os << prefix << ": ";
    if (flags&TIME) {
        if (flags&HIRES)
            qpid::sys::outputHiresNow(os);
        else
            qpid::sys::outputFormattedNow(os);
    }
    if (flags&CATEGORY)
        os << "[" << CategoryTraits::name(s.category) << "] ";
    if (flags&LEVEL)
        os << LevelTraits::name(s.level) << " ";
    if (flags&THREAD)
        os << "[0x" << hex << qpid::sys::Thread::logId() << "] ";
    if (flags&FILE)
        os << s.file << ":";
    if (flags&LINE)
        os << dec << s.line << ":";
    if ((flags&FUNCTION) && s.function)
        os << s.function << ":";
    if (flags & (FILE|LINE|FUNCTION))
        os << " ";
    os << msg << endl;
    std::string formatted=os.str();
    {
        ScopedLock l(lock);
        std::for_each(outputs.begin(), outputs.end(),
                      boost::bind(&Output::log, _1, s, formatted));
    }
}

void Logger::output(std::auto_ptr<Output> out) {
    ScopedLock l(lock);
    outputs.push_back(out.release());
}

void Logger::clear() {
    select(Selector());         // locked
    format(0);                  // locked
    ScopedLock l(lock);
    outputs.clear();
}

void Logger::format(int formatFlags) {
    ScopedLock l(lock);
    flags=formatFlags;
}

static int bitIf(bool test, int bit) {
    return test ? bit : 0;
}

int Logger::format(const Options& opts) {
    int flags=
        bitIf(opts.level, LEVEL) |
        bitIf(opts.time, TIME) |
        bitIf(opts.source, (FILE|LINE)) |
        bitIf(opts.function, FUNCTION) |
        bitIf(opts.thread, THREAD) |
        bitIf(opts.hiresTs, HIRES) |
        bitIf(opts.category, CATEGORY);
    format(flags);
    return flags;
}

void Logger::add(Statement& s) {
    ScopedLock l(lock);
    enable_unlocked(&s);
    statements.insert(&s);
}

void Logger::configure(const Options& opts) {
    clear();
    Options o(opts);
    if (o.trace)
        o.selectors.push_back("trace+");
    format(o);
    select(Selector(o));
    options = opts;
    setPrefix(opts.prefix);
    options.sinkOptions->setup(this);
}

void Logger::reconfigure(const std::vector<std::string>& selectors) {
    Options o(options);
    o.selectors = selectors;
    o.deselectors.clear();
    select(Selector(o));
    options = o;                // Don't update options till selectors has been validated.
}

void Logger::setPrefix(const std::string& p) { prefix = p; }


bool Logger::getHiresTimestamp()
{
    return flags & HIRES;
}


void Logger::setHiresTimestamp(bool setting)
{
    ScopedLock l(lock);
    if (setting)
        flags |= HIRES;
    else
        flags &= ~HIRES;
}

}} // namespace qpid::log
