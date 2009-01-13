#ifndef QPID_LOG_LOGGER_H
#define QPID_LOG_LOGGER_H

/*
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Selector.h"
#include "Options.h"
#include "qpid/sys/Mutex.h"
#include <boost/ptr_container/ptr_vector.hpp>
#include <boost/noncopyable.hpp>
#include <set>

namespace qpid {
namespace log {

/**
 * Central logging agent.
 *
 * Thread safe, singleton.
 *
 * The Logger provides all needed functionality for selecting and
 * formatting logging output. The actual outputting of log records
 * is handled by Logger::Output-derived classes instantiated by the
 * platform's sink-related options.
 */
class Logger : private boost::noncopyable {
  public:
    /** Flags indicating what to include in the log output */
    enum FormatFlag { FILE=1, LINE=2, FUNCTION=4, LEVEL=8, TIME=16, THREAD=32};

    /**
     * Logging output sink.
     *
     * The Output sink provides an interface to direct logging output to.
     * Logging sinks are primarily platform-specific as provided for on
     * each platform.
     *
     * Implementations of Output must be thread safe.
     */
    class Output {
      public:
        Output();
        virtual ~Output();
        /** Receives the statemnt of origin and formatted message to log. */
        virtual void log(const Statement&, const std::string&) =0;
    };

    static Logger& instance();

    Logger();
    ~Logger();
    
    /** Select the messages to be logged. */
    void select(const Selector& s);

    /** Set the formatting flags, bitwise OR of FormatFlag values. */
    void format(int formatFlags);

    /** Set format flags from options object.
     *@returns computed flags.
     */
    int format(const Options&);

    /** Configure logger from Options */
    void configure(const Options& o);

    /** Add a statement. */
    void add(Statement& s);

    /** Log a message. */
    void log(const Statement&, const std::string&);

    /** Add an output destination for messages */
    void output(std::auto_ptr<Output> out);

    /** Set a prefix for all messages */
    void setPrefix(const std::string& prefix);
    
    /** Reset the logger. */
    void clear();

    /** Get the options used to configure the logger. */
    const Options& getOptions() const { return options; }
    
    
  private:
    typedef boost::ptr_vector<Output> Outputs;
    typedef std::set<Statement*> Statements;

    sys::Mutex lock;
    inline void enable_unlocked(Statement* s);

    Statements statements;
    Outputs outputs;
    Selector selector;
    int flags;
    std::string prefix;
    Options options;
};

}} // namespace qpid::log


#endif  /*!QPID_LOG_LOGGER_H*/
