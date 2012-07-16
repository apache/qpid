#include "PersistableQueuedMessage.h"

#include "SimpleQueue.h"
#include "SimpleMessage.h"

namespace tests {
namespace storePerftools {
namespace asyncPerf {

PersistableQueuedMessage::PersistableQueuedMessage()
{}

PersistableQueuedMessage::PersistableQueuedMessage(SimpleQueue* q,
                                                   boost::intrusive_ptr<SimpleMessage> msg) :
        QueuedMessage(q, msg),
        m_enqHandle(q->getStore()->createEnqueueHandle(msg->getHandle(), q->getHandle()))
{}

PersistableQueuedMessage::PersistableQueuedMessage(const PersistableQueuedMessage& pm) :
        QueuedMessage(pm),
        m_enqHandle(pm.m_enqHandle)
{}

PersistableQueuedMessage::PersistableQueuedMessage(PersistableQueuedMessage* const pm) :
        QueuedMessage(pm),
        m_enqHandle(pm->m_enqHandle)
{}

PersistableQueuedMessage::~PersistableQueuedMessage()
{}

PersistableQueuedMessage&
PersistableQueuedMessage::operator=(const PersistableQueuedMessage& rhs)
{
    QueuedMessage::operator=(rhs);
    m_enqHandle = rhs.m_enqHandle;
    return *this;
}

const qpid::broker::EnqueueHandle&
PersistableQueuedMessage::enqHandle() const
{
    return m_enqHandle;
}

qpid::broker::EnqueueHandle&
PersistableQueuedMessage::enqHandle()
{
    return m_enqHandle;
}

}}} // tests::storePerftools::asyncPerf
