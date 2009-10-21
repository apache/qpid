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

#include <qpid/Exception.h>
#include <qpid/log/Statement.h>

#include "BindingRecordset.h"
#include "BlobAdapter.h"
#include "BlobEncoder.h"
#include "VariantHelper.h"

namespace qpid {
namespace store {
namespace ms_sql {

void
BindingRecordset::add(uint64_t exchangeId,
                      const std::string& queueName,
                      const std::string& routingKey,
                      const qpid::framing::FieldTable& args)
{
    VariantHelper<std::string> queueNameStr(queueName);
    VariantHelper<std::string> routingKeyStr(routingKey);
    BlobEncoder blob (args);   // Marshall field table to a blob
    rs->AddNew();
    rs->Fields->GetItem("exchangeId")->Value = exchangeId;
    rs->Fields->GetItem("queueName")->Value = queueNameStr;
    rs->Fields->GetItem("routingKey")->Value = routingKeyStr;
    rs->Fields->GetItem("fieldTableBlob")->AppendChunk(blob);
    rs->Update();
}

void
BindingRecordset::remove(uint64_t exchangeId,
                         const std::string& queueName,
                         const std::string& routingKey,
                         const qpid::framing::FieldTable& /*args*/)
{
    // Look up the affected binding.
    std::ostringstream filter;
    filter << "exchangeId = " << exchangeId
           << " AND queueName = '" << queueName << "'"
           << " AND routingKey = '" << routingKey << "'" << std::ends;
    rs->PutFilter (VariantHelper<std::string>(filter.str()));
    if (rs->RecordCount != 0) {
        // Delete the records
        rs->Delete(adAffectGroup);
        rs->Update();
    }
    requery();
}

void
BindingRecordset::remove(uint64_t exchangeId)
{
    // Look up the affected bindings by the exchange ID
    std::ostringstream filter;
    filter << "exchangeId = " << exchangeId << std::ends;
    rs->PutFilter (VariantHelper<std::string>(filter.str()));
    if (rs->RecordCount != 0) {
        // Delete the records
        rs->Delete(adAffectGroup);
        rs->Update();
    }
    requery();
}

void
BindingRecordset::remove(const std::string& queueName)
{
    // Look up the affected bindings by the exchange ID
    std::ostringstream filter;
    filter << "queueName = '" << queueName << "'" << std::ends;
    rs->PutFilter (VariantHelper<std::string>(filter.str()));
    if (rs->RecordCount != 0) {
        // Delete the records
        rs->Delete(adAffectGroup);
        rs->Update();
    }
    requery();
}

void
BindingRecordset::recover(qpid::broker::RecoveryManager& recoverer,
                          std::map<uint64_t, broker::RecoverableExchange::shared_ptr> exchMap)
{
    if (rs->BOF && rs->EndOfFile)
        return;   // Nothing to do
    rs->MoveFirst();
    Binding b;
    IADORecordBinding *piAdoRecordBinding;
    rs->QueryInterface(__uuidof(IADORecordBinding), 
                       (LPVOID *)&piAdoRecordBinding);
    piAdoRecordBinding->BindToRecordset(&b);
    while (!rs->EndOfFile) {
        long blobSize = rs->Fields->Item["fieldTableBlob"]->ActualSize;
        BlobAdapter blob(blobSize);
        blob = rs->Fields->Item["fieldTableBlob"]->GetChunk(blobSize);
        broker::RecoverableExchange::shared_ptr exch = exchMap[b.exchangeId];
        std::string q(b.queueName), k(b.routingKey);
        exch->bind(q, k, blob);
        rs->MoveNext();
    }

    piAdoRecordBinding->Release();
}

void
BindingRecordset::dump()
{
    Recordset::dump();
    if (rs->EndOfFile && rs->BOF)    // No records
        return;
    rs->MoveFirst();

    Binding b;
    IADORecordBinding *piAdoRecordBinding;
    rs->QueryInterface(__uuidof(IADORecordBinding), 
                       (LPVOID *)&piAdoRecordBinding);
    piAdoRecordBinding->BindToRecordset(&b);
   
    while (VARIANT_FALSE == rs->EndOfFile) {
      QPID_LOG(notice, "exch " << b.exchangeId
                       << ", q: " << b.queueName
                       << ", k: " << b.routingKey);
      rs->MoveNext();
    }

    piAdoRecordBinding->Release();
}

}}}  // namespace qpid::store::ms_sql
