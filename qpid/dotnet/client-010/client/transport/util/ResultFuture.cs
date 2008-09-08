using System;
using System.Threading;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace common.org.apache.qpid.transport.util
{
    public class ResultFuture<T> : Future<T> where T : Struct 
    {
        const long _timeout = 60000;
        private Struct _result;
        private Session _session;
        private static readonly Logger log = Logger.get(typeof(ResultFuture<T>));

        public Struct get(long timeout)
        {
            lock (this)
            {
                long start = DateTime.Now.Millisecond;
                long elapsed = 0;
                while (! _session.Closed && _timeout - elapsed > 0 && _result == null)
                {
                        log.debug("{0} waiting for result: {1}", _session, this );
                        Monitor.Wait(this, (int) (timeout - elapsed));
                        elapsed = DateTime.Now.Millisecond - start;                   
                }
            }
            if( _session.Closed )
            {
                throw new SessionException(_session.getExceptions());
            }
           return _result;
        }

        public T Result
        {
            get { return (T) get(_timeout); }
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
