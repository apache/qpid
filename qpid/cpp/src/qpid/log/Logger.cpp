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

#include "Logger.h"
#include "qpid/memory.h"
#include "qpid/sys/Thread.h"
#include <boost/pool/detail/singleton.hpp>
#include <boost/bind.hpp>
#include <boost/function.hpp>
#include <boost/scoped_ptr.hpp>
#include <algorithm>
#include <sstream>
#include <fstream>
#include <iomanip>
#include <stdexcept>
#include <syslog.h>
#include <time.h>


namespace qpid {
namespace log {

using namespace std;

typedef sys::Mutex::ScopedLock ScopedLock;

inline void Logger::enable_unlocked(Statement* s) {
    s->enabled=selector.isEnabled(s->level, s->function);
}

struct OstreamOutput : public Logger::Output {
    OstreamOutput(std::ostream& o) : out(&o) {}

    OstreamOutput(const string& file)
        : out(new ofstream(file.c_str(), ios_base::out | ios_base::app)),
          mine(out)
    {
        if (!out->good())
            throw std::runtime_error("Can't open log file: "+file);
    }

    void log(const Statement&, const std::string& m) {
        *out << m << flush;
    }
    
    ostream* out;
    boost::scoped_ptr<ostream> mine;
};

struct SyslogOutput : public Logger::Output {
    SyslogOutput(const Options& opts)
        : name(opts.syslogName), facility(opts.syslogFacility.value)
    {
        ::openlog(name.c_str(), LOG_PID, facility);
    }

    ~SyslogOutput() {
        ::closelog();
    }
    
    void log(const Statement& s, const std::string& m)
    {
        syslog(LevelTraits::priority(s.level), "%s", m.c_str());
    }
    
    std::string name;
    int facility;
};

Logger& Logger::instance() {
    return boost::details::pool::singleton_default<Logger>::instance();
}

Logger::Logger() : flags(0) {
    // Initialize myself from env variables so all programs
    // (e.g. tests) can use logging even if they don't parse
    // command line args.
    Options opts("");
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
    if (flags&TIME) 
    {
        const char * month_abbrevs[] = { "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec" };
        time_t rawtime;
        struct tm * timeinfo;

        time ( & rawtime );
        timeinfo = localtime ( &rawtime );
        char time_string[100];
        sprintf ( time_string,
                  "%d-%s-%02d %02d:%02d:%02d",
                  1900 + timeinfo->tm_year,
                  month_abbrevs[timeinfo->tm_mon],
                  timeinfo->tm_mday,
                  timeinfo->tm_hour,
                  timeinfo->tm_min,
                  timeinfo->tm_sec
                );
        os << time_string << " ";
    }
    if (flags&LEVEL)
        os << LevelTraits::name(s.level) << " ";
    if (flags&THREAD)
        os << "[0x" << hex << qpid::sys::Thread::logId() << "] ";
    if (flags&FILE)
        os << s.file << ":";
    if (flags&LINE)
        os << dec << s.line << ":";
    if (flags&FUNCTION)
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

void Logger::output(std::ostream& out) {
    output(make_auto_ptr<Output>(new OstreamOutput(out)));
}

void Logger::syslog(const Options& opts) {
    output(make_auto_ptr<Output>(new SyslogOutput(opts)));
}

void Logger::output(const std::string& name, const Options& opts) {
    if (name=="stderr")
        output(clog);
    else if (name=="stdout")
        output(cout);
    else if (name=="syslog")
        syslog(opts);
    else 
        output(make_auto_ptr<Output>(new OstreamOutput(name)));
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
        bitIf(opts.thread, THREAD);
    format(flags);
    return flags;
}

void Logger::add(Statement& s) {
    ScopedLock l(lock);
    enable_unlocked(&s);
    statements.insert(&s);
}

void Logger::configure(const Options& opts) {
    options = opts;
    clear();
    Options o(opts);
    if (o.trace)
        o.selectors.push_back("trace+");
    format(o); 
    select(Selector(o));
    void (Logger::* outputFn)(const std::string&, const Options&) = &Logger::output;
    for_each(o.outputs.begin(), o.outputs.end(),
             boost::bind(outputFn, this, _1, boost::cref(o)));
    setPrefix(opts.prefix);
}

void Logger::setPrefix(const std::string& p) { prefix = p; }

}} // namespace qpid::log
