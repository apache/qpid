#ifndef tests_storePerftools_asyncPerf_PersistableQueuedMessage_h_
#define tests_storePerftools_asyncPerf_PersistableQueuedMessage_h_

#include "QueuedMessage.h"

#include "qpid/broker/EnqueueHandle.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class PersistableQueuedMessage : public QueuedMessage {
public:
    PersistableQueuedMessage();
    PersistableQueuedMessage(SimpleQueue* q,
                             boost::intrusive_ptr<SimpleMessage> msg);
    PersistableQueuedMessage(const PersistableQueuedMessage& pqm);
    PersistableQueuedMessage(PersistableQueuedMessage* const pqm);
    virtual ~PersistableQueuedMessage();
    PersistableQueuedMessage& operator=(const PersistableQueuedMessage& rhs);

    const qpid::broker::EnqueueHandle& enqHandle() const;
    qpid::broker::EnqueueHandle& enqHandle();

private:
    qpid::broker::EnqueueHandle m_enqHandle;
};

}}} // tests::storePerftools::asyncPerf

#endif // tests_storePerftools_asyncPerf_PersistableQueuedMessage_h_
