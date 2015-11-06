/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
#include "QueueSettings.h"
#include "QueueFlowLimit.h"
#include "MessageGroupManager.h"
#include "qpid/types/Variant.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp_0_10/Codecs.h"


namespace qpid {
namespace broker {

namespace {
const std::string MAX_COUNT("qpid.max_count");
const std::string MAX_SIZE("qpid.max_size");
const std::string MAX_FILE_COUNT("qpid.file_count");
const std::string MAX_FILE_SIZE("qpid.file_size");
const std::string POLICY_TYPE("qpid.policy_type");
const std::string POLICY_TYPE_REJECT("reject");
const std::string POLICY_TYPE_RING("ring");
const std::string POLICY_TYPE_SELF_DESTRUCT("self-destruct");
const std::string NO_LOCAL("no-local");
const std::string BROWSE_ONLY("qpid.browse-only");
const std::string TRACE_ID("qpid.trace.id");
const std::string TRACE_EXCLUDES("qpid.trace.exclude");
const std::string LVQ_KEY("qpid.last_value_queue_key");
const std::string AUTO_DELETE_TIMEOUT("qpid.auto_delete_timeout");
const std::string ALERT_REPEAT_GAP("qpid.alert_repeat_gap");
const std::string ALERT_COUNT("qpid.alert_count");
const std::string ALERT_SIZE("qpid.alert_size");
const std::string ALERT_COUNT_UP("qpid.alert_count_up");
const std::string ALERT_SIZE_UP("qpid.alert_size_up");
const std::string ALERT_COUNT_DOWN("qpid.alert_count_down");
const std::string ALERT_SIZE_DOWN("qpid.alert_size_down");
const std::string PRIORITIES("qpid.priorities");
const std::string FAIRSHARE("qpid.fairshare");
const std::string FAIRSHARE_ALIAS("x-qpid-fairshare");
const std::string PAGING("qpid.paging");
const std::string MAX_PAGES("qpid.max_pages_loaded");
const std::string PAGE_FACTOR("qpid.page_factor");
const std::string FILTER("qpid.filter");
const std::string LIFETIME_POLICY("qpid.lifetime-policy");
const std::string DELETE_ON_CLOSE_KEY("delete-on-close");
const std::string DELETE_IF_UNUSED_KEY("delete-if-unused");
const std::string DELETE_IF_UNUSED_AND_EMPTY_KEY("delete-if-unused-and-empty");
const std::string MANUAL("manual");

const std::string LVQ_LEGACY("qpid.last_value_queue");
const std::string LVQ_LEGACY_KEY("qpid.LVQ_key");
const std::string LVQ_LEGACY_NOBROWSE("qpid.last_value_queue_no_browse");

const std::string SEQUENCING("qpid.queue_msg_sequence");

bool handleFairshareSetting(const std::string& basename, const std::string& key, const qpid::types::Variant& value, QueueSettings& settings)
{
    if (key.find(basename) == 0) {
        qpid::types::Variant index(key.substr(basename.size()+1));
        settings.fairshare[index] = value;
        return true;
    } else {
        return false;
    }
}
bool isFairshareSetting(const std::string& key, const qpid::types::Variant& value, QueueSettings& settings)
{
    return handleFairshareSetting(FAIRSHARE, key, value, settings) || handleFairshareSetting(FAIRSHARE_ALIAS, key, value, settings);
}
}

const QueueSettings::Aliases QueueSettings::aliases;

QueueSettings::QueueSettings(bool d, bool a) :
    durable(d),
    autodelete(a),
    lifetime(DELETE_IF_UNUSED),
    isTemporary(false),
    priorities(0),
    defaultFairshare(0),
    shareGroups(false),
    addTimestamp(false),
    dropMessagesAtLimit(false),
    selfDestructAtLimit(false),
    paging(false),
    maxPages(0),
    pageFactor(0),
    noLocal(false),
    isBrowseOnly(false),
    autoDeleteDelay(0),
    alertRepeatInterval(60),
    maxFileSize(0),
    maxFileCount(0),
    sequencing(false)
{}

bool QueueSettings::handle(const std::string& key, const qpid::types::Variant& value)
{
    if (key == MAX_COUNT) {
        maxDepth.setCount(value);
        return true;
    } else if (key == MAX_SIZE) {
        maxDepth.setSize(value);
        return true;
    } else if (key == POLICY_TYPE) {
        if (value.getString() == POLICY_TYPE_RING) {
            dropMessagesAtLimit = true;
            return true;
        } else if (value.getString() == POLICY_TYPE_SELF_DESTRUCT) {
            selfDestructAtLimit = true;
            return true;
        } else if (value.getString() == POLICY_TYPE_REJECT) {
            //do nothing, thats the default
            return true;
        } else {
            QPID_LOG(warning, "Unrecognised policy option: " << value);
            return false;
        }
    } else if (key == NO_LOCAL) {
        noLocal = value;
        return true;
    } else if (key == BROWSE_ONLY) {
        isBrowseOnly = value;
        return true;
    } else if (key == TRACE_ID) {
        traceId = value.asString();
        return true;
    } else if (key == TRACE_EXCLUDES) {
        traceExcludes = value.asString();
        return true;
    } else if (key == PRIORITIES) {
        priorities = value;
        return true;
    } else if (key == FAIRSHARE) {
        defaultFairshare = value;
        return true;
    } else if (isFairshareSetting(key, value, *this)) {
        return true;
    } else if (key == MessageGroupManager::qpidMessageGroupKey) {
        groupKey = value.asString();
        return true;
    } else if (key == MessageGroupManager::qpidSharedGroup) {
        shareGroups = value;
        return true;
    } else if (key == MessageGroupManager::qpidMessageGroupTimestamp) {
        addTimestamp = value;
        return true;
    } else if (key == LVQ_KEY) {
        lvqKey = value.asString();
        return true;
    } else if (key == LVQ_LEGACY) {
        if (lvqKey.empty()) lvqKey = LVQ_LEGACY_KEY;
        return true;
    } else if (key == LVQ_LEGACY_NOBROWSE) {
        QPID_LOG(warning, "Ignoring 'no-browse' directive for LVQ; it is no longer necessary");
        if (lvqKey.empty()) lvqKey = LVQ_LEGACY_KEY;
        return true;
    } else if (key == AUTO_DELETE_TIMEOUT) {
        autoDeleteDelay = value;
        if (autoDeleteDelay) autodelete = true;
        return true;
    } else if (key == QueueFlowLimit::flowStopCountKey) {
        flowStop.setCount(value);
        return true;
    } else if (key == QueueFlowLimit::flowResumeCountKey) {
        flowResume.setCount(value);
        return true;
    } else if (key == QueueFlowLimit::flowStopSizeKey) {
        flowStop.setSize(value);
        return true;
    } else if (key == QueueFlowLimit::flowResumeSizeKey) {
        flowResume.setSize(value);
        return true;
    } else if (key == ALERT_REPEAT_GAP) {
        alertRepeatInterval = value;
        return true;
    } else if ((key == ALERT_COUNT) || (key == ALERT_COUNT_UP)) {
        alertThreshold.setCount(value);
        return true;
    } else if ((key == ALERT_SIZE) || (key == ALERT_SIZE_UP)) {
        alertThreshold.setSize(value);
        return true;
    } else if (key == ALERT_COUNT_DOWN) {
        alertThresholdDown.setCount(value);
        return true;
    } else if (key == ALERT_SIZE_DOWN) {
        alertThresholdDown.setSize(value);
        return true;
    } else if (key == MAX_FILE_COUNT && value.asUint64() > 0) {
        maxFileCount = value.asUint64();
        return false; // 'handle' here and also pass to store
    } else if (key == MAX_FILE_SIZE && value.asUint64() > 0) {
        maxFileSize = value.asUint64();
        return false; // 'handle' here and also pass to store
    } else if (key == PAGING) {
        paging = value;
        return true;
    } else if (key == MAX_PAGES) {
        maxPages = value;
        return true;
    } else if (key == PAGE_FACTOR) {
        pageFactor = value;
        return true;
    } else if (key == SEQUENCING) {
        sequenceKey = value.getString();
        sequencing = !sequenceKey.empty();
        return true;
    } else if (key == FILTER) {
        filter = value.asString();
        return true;
    } else if (key == LIFETIME_POLICY) {
        if (value.asString() == DELETE_IF_UNUSED_KEY) {
            lifetime = DELETE_IF_UNUSED;
            autodelete = true;
        } else if (value.asString() == DELETE_IF_UNUSED_AND_EMPTY_KEY) {
            lifetime = DELETE_IF_UNUSED_AND_EMPTY;
            autodelete = true;
        } else if (value.asString() == MANUAL) {
            autodelete = false;
        } else if (value.asString() == DELETE_ON_CLOSE_KEY) {
            lifetime = DELETE_ON_CLOSE;
            autodelete = true;
        } else {
            QPID_LOG(warning, "Invalid value for " << LIFETIME_POLICY << ": " << value);
        }
        return true;
    } else {
        return false;
    }
}

void QueueSettings::validate() const
{
    if (lvqKey.size() && priorities > 0)
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify " << LVQ_KEY << " and " << PRIORITIES << " for the same queue"));
    if ((fairshare.size() || defaultFairshare) && priorities == 0)
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify fairshare settings when queue is not enabled for priorities"));
    if (fairshare.size() > priorities)
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot have fairshare set for priority levels greater than " << priorities));
    if (groupKey.size() && lvqKey.size())
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify " << LVQ_KEY << " and " << MessageGroupManager::qpidMessageGroupKey << " for the same queue"));
    if (groupKey.size() && priorities)
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify " << PRIORITIES << " and " << MessageGroupManager::qpidMessageGroupKey << " for the same queue"));
    if (shareGroups && groupKey.empty()) {
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Can only specify " << MessageGroupManager::qpidSharedGroup
                                                               << " if " << MessageGroupManager::qpidMessageGroupKey << " is set"));
    }
    if (addTimestamp && groupKey.empty()) {
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Can only specify " << MessageGroupManager::qpidMessageGroupTimestamp
                                                               << " if " << MessageGroupManager::qpidMessageGroupKey << " is set"));
    }

    // @todo: remove once "sticky" consumers are supported - see QPID-3347
    if (!shareGroups && groupKey.size()) {
        throw qpid::framing::InvalidArgumentException(QPID_MSG("Only shared groups are supported at present; " << MessageGroupManager::qpidSharedGroup
                                                               << " is required if " << MessageGroupManager::qpidMessageGroupKey << " is set"));
    }

    if (paging) {
        if(lvqKey.size()) {
            throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify " << LVQ_KEY << " and " << PAGING << " for the same queue"));
        }
        if(priorities) {
            throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify " << PRIORITIES << " and " << PAGING << " for the same queue"));
        }
        if(groupKey.size()) {
            throw qpid::framing::InvalidArgumentException(QPID_MSG("Cannot specify " << MessageGroupManager::qpidMessageGroupKey << " and " << PAGING << " for the same queue"));
        }
    } else {
        if (maxPages) {
            throw qpid::framing::InvalidArgumentException(QPID_MSG("Can only specify " << MAX_PAGES << " if " << PAGING << " is set"));
        }
        if (pageFactor) {
            throw qpid::framing::InvalidArgumentException(QPID_MSG("Can only specify " << PAGE_FACTOR << " if " << PAGING << " is set"));
        }
    }
}

void QueueSettings::populate(const std::map<std::string, qpid::types::Variant>& inputs, std::map<std::string, qpid::types::Variant>& unused)
{
    original = inputs;
    for (qpid::types::Variant::Map::const_iterator i = inputs.begin(); i != inputs.end(); ++i) {
        Aliases::const_iterator a = aliases.find(i->first);
        if (!handle((a != aliases.end() ? a->second : i->first), i->second)) unused.insert(*i);
    }
}
void QueueSettings::populate(const qpid::framing::FieldTable& inputs, qpid::framing::FieldTable& unused)
{
    qpid::types::Variant::Map o;
    qpid::amqp_0_10::translate(inputs, original);
    populate(original, o);
    qpid::amqp_0_10::translate(o, unused);
}
std::map<std::string, qpid::types::Variant> QueueSettings::asMap() const
{
    return original;
}

QueueSettings::Aliases::Aliases()
{
    insert(value_type("x-qpid-priorities", "qpid.priorities"));
    insert(value_type("x-qpid-fairshare", "qpid.fairshare"));
    insert(value_type("x-qpid-minimum-alert-repeat-gap", "qpid.alert_repeat_gap"));
    insert(value_type("x-qpid-maximum-message-count", "qpid.alert_count"));
    insert(value_type("x-qpid-maximum-message-size", "qpid.alert_size"));
}

std::string QueueSettings::getLimitPolicy() const
{
    if (dropMessagesAtLimit) return POLICY_TYPE_RING;
    else if (selfDestructAtLimit) return POLICY_TYPE_SELF_DESTRUCT;
    else return POLICY_TYPE_REJECT;
}
}} // namespace qpid::broker
