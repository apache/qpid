using System;
using System.Threading;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace common.org.apache.qpid.transport.util
{
    public class ResultFuture : Future
    {
        const long _timeout = 60000;
        private Struct _result;
        private Session _session;
        private static readonly Logger log = Logger.get(typeof(ResultFuture));

        public Struct get(long timeout)
        {
            lock (this)
            {
                DateTime start = DateTime.Now;
                long elapsed = 0;
                while (! _session.Closed && timeout - elapsed > 0 && _result == null)
                {
                        log.debug("{0} waiting for result: {1}", _session, this );
                        Monitor.Wait(this, (int) (timeout - elapsed));
                        elapsed = (long) (DateTime.Now.Subtract(start)).TotalMilliseconds;
                }
            }
            if( _session.Closed )
            {
                throw new SessionException(_session.getExceptions());
            }
           return _result;
        }

        public Struct Result
        {
            get { return get(_timeout); }
            set
            {
                lock (this)
                {
                    _result = value;
                    Monitor.PulseAll(this);
                }
            }
        }

        public Session Session
        {
            set { _session = value; }
        }

        public String toString()
        {
            return String.Format("Future({0})", _result);
        }

    }
}
