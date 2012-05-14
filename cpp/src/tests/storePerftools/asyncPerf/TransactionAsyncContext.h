#ifndef tests_storePerftools_asyncPerf_TransactionAsyncContext_h_
#define tests_storePerftools_asyncPerf_TransactionAsyncContext_h_

#include "MockTransactionContext.h"
#include "qpid/broker/BrokerContext.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class TransactionAsyncContext: public qpid::broker::BrokerContext {
public:
    TransactionAsyncContext(MockTransactionContext* tc,
                            const qpid::asyncStore::AsyncOperation::opCode op);
    virtual ~TransactionAsyncContext();
    qpid::asyncStore::AsyncOperation::opCode getOpCode() const;
    const char* getOpStr() const;
    MockTransactionContext* getTransactionContext() const;
    void destroy();
protected:
    MockTransactionContext* m_tc;
    const qpid::asyncStore::AsyncOperation::opCode m_op;
};

}}} // namespace tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_TransactionAsyncContext_h_
