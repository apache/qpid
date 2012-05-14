#include "TransactionAsyncContext.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

TransactionAsyncContext::TransactionAsyncContext(MockTransactionContext* tc,
                                                 const qpid::asyncStore::AsyncOperation::opCode op):
        qpid::broker::BrokerContext(),
        m_tc(tc),
        m_op(op)
{
    assert(tc != 0);
}

TransactionAsyncContext::~TransactionAsyncContext()
{}

qpid::asyncStore::AsyncOperation::opCode
TransactionAsyncContext::getOpCode() const
{
    return m_op;
}

const char*
TransactionAsyncContext::getOpStr() const
{
    return qpid::asyncStore::AsyncOperation::getOpStr(m_op);
}

MockTransactionContext*
TransactionAsyncContext::getTransactionContext() const
{
    return m_tc;
}

void
TransactionAsyncContext::destroy()
{
    delete this;
}

}}} // namespace tests::storePerftools::asyncPerf
