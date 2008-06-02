#ifndef LOGGER_H
#define LOGGER_H

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
#include "qpid/sys/Mutex.h"
#include <boost/ptr_container/ptr_vector.hpp>
#include <boost/noncopyable.hpp>
#include <set>

namespace qpid {
namespace log {

class Options;

/**
 * Central logging agent.
 *
 * Thread safe, singleton.
 */
class Logger : private boost::noncopyable {
  public:
    /** Flags indicating what to include in the log output */
    enum FormatFlag { FILE=1, LINE=2, FUNCTION=4, LEVEL=8, TIME=16, THREAD=32};

    /** Interface for log output destination.
     * 
     * Implementations must be thread safe.
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

    /** Add an ostream to outputs.
     * 
     * The ostream must not be destroyed while the Logger might
     * still be using it. This is the case for std streams cout,
     * cerr, clog. 
     */
    void output(std::ostream&);

    /** Add syslog to outputs. */
    void syslog(const Options&);

    /** Add an output.
     *@param name a file name or one of the special tokens:
     *stdout, stderr, syslog.
     */
    void output(const std::string& name, const Options&);

    /** Add an output destination for messages */
    void output(std::auto_ptr<Output> out); 

    /** Reset the logger to it's original state. */
    void clear();

  private:
    typedef boost::ptr_vector<Output> Outputs;
    typedef std::set<Statement*> Statements;

    sys::Mutex lock;
    inline void enable_unlocked(Statement* s);

    Statements statements;
    Outputs outputs;
    Selector selector;
    int flags;
};

}} // namespace qpid::log


#endif  /*!LOGGER_H*/
