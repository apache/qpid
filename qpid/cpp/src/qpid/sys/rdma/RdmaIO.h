#ifndef Rdma_Acceptor_h
#define Rdma_Acceptor_h

#include "rdma_wrap.h"

#include "qpid/sys/Dispatcher.h"

#include <netinet/in.h>

#include <boost/function.hpp>
#include <boost/ptr_container/ptr_deque.hpp>
#include <deque>

using qpid::sys::DispatchHandle;
using qpid::sys::Poller;

namespace Rdma {

    class Connection;
    enum ConnectionState {
        IDLE,
        RESOLVE_ADDR,
        RESOLVE_ROUTE,
        LISTENING,
        CONNECTING,
        ACCEPTING,
        ESTABLISHED,
        REJECTED,
        DISCONNECTED,
        ERROR
    };

    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> ConnectedCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> ErrorCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> DisconnectedCallback;
    typedef boost::function1<bool, Rdma::Connection::intrusive_ptr&> ConnectionRequestCallback;
    typedef boost::function1<void, Rdma::Connection::intrusive_ptr&> RejectedCallback;

    class AsynchIO
    {
        typedef boost::function1<void, AsynchIO&> ErrorCallback;
        typedef boost::function2<void, AsynchIO&, Buffer*> ReadCallback;
        typedef boost::function1<void, AsynchIO&>  IdleCallback;

        QueuePair::intrusive_ptr qp;
        DispatchHandle dataHandle;
        int bufferSize;
        int recvBufferCount;
        std::deque<Buffer*> bufferQueue;
        boost::ptr_deque<Buffer> buffers;

        ReadCallback readCallback;
        IdleCallback idleCallback;
        ErrorCallback errorCallback;

    public:
        AsynchIO(
            QueuePair::intrusive_ptr q,
            int s,
            ReadCallback rc,
            IdleCallback ic,
            ErrorCallback ec
        );
        ~AsynchIO();

        void start(Poller::shared_ptr poller);
        void queueReadBuffer(Buffer* buff);
        void queueWrite(Buffer* buff);
        void notifyPendingWrite();
        void queueWriteClose();
        Buffer* getBuffer();

    private:
        void dataEvent(DispatchHandle& handle);
    };

    class Listener
    {
        sockaddr src_addr;
        Connection::intrusive_ptr ci;
        DispatchHandle handle;
        ConnectedCallback connectedCallback;
        ErrorCallback errorCallback;
        DisconnectedCallback disconnectedCallback;
        ConnectionRequestCallback connectionRequestCallback;
        ConnectionState state;

    public:
        Listener(
            const sockaddr& src,
            ConnectedCallback cc,
            ErrorCallback errc,
            DisconnectedCallback dc,
            ConnectionRequestCallback crc = 0
        );
        void start(Poller::shared_ptr poller);

    private:
        void connectionEvent(DispatchHandle& handle);
    };

    class Connector
    {
        sockaddr dst_addr;
        Connection::intrusive_ptr ci;
        DispatchHandle handle;
        ConnectedCallback connectedCallback;
        ErrorCallback errorCallback;
        DisconnectedCallback disconnectedCallback;
        RejectedCallback rejectedCallback;
        ConnectionState state;

    public:
        Connector(
            const sockaddr& dst,
            ConnectedCallback cc,
            ErrorCallback errc,
            DisconnectedCallback dc,
            RejectedCallback rc = 0
        );
        void start(Poller::shared_ptr poller);

    private:
        void connectionEvent(DispatchHandle& handle);
    };
}

#endif // Rdma_Acceptor_h
