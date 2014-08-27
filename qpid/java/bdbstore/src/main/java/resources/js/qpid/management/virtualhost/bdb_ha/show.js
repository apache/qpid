/*
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
 */

define(["qpid/common/util", "dojo/query", "dojo/domReady!"],
  function (util, query)
  {
    var localTransactionSynchronizationPolicy = "localTransactionSynchronizationPolicy";
    var remoteTransactionSynchronizationPolicy = "remoteTransactionSynchronizationPolicy";

    var fields = [ "storeUnderfullSize", "storeOverfullSize"];

    function BDB(data)
    {
        util.buildUI(data.containerNode, data.parent, "virtualhost/bdb_ha/show.html", fields, this);

        this[localTransactionSynchronizationPolicy]= query("." + localTransactionSynchronizationPolicy, data.containerNode)[0];
        this[remoteTransactionSynchronizationPolicy]= query("."+ remoteTransactionSynchronizationPolicy, data.containerNode)[0];
    }

    BDB.prototype.update = function(data)
    {
        util.updateUI(data, fields, this);

        var localSyncPolicy =  data[localTransactionSynchronizationPolicy] ? data[localTransactionSynchronizationPolicy].toLowerCase() : "";
        var remoteSyncPolicy =  data[remoteTransactionSynchronizationPolicy] ? data[remoteTransactionSynchronizationPolicy].toLowerCase() : "";

        for(var i=0; i<this[localTransactionSynchronizationPolicy].children.length;i++)
        {
            var child = this[localTransactionSynchronizationPolicy].children[i];
            if (child.className == localTransactionSynchronizationPolicy + "-" + localSyncPolicy)
            {
                child.style.display = "block";
            }
            else
            {
                child.style.display = "none";
            }
        }

        for(var j=0; j<this[remoteTransactionSynchronizationPolicy].children.length;j++)
        {
            var child = this[remoteTransactionSynchronizationPolicy].children[j];
            if (child.className == remoteTransactionSynchronizationPolicy + "-" + remoteSyncPolicy)
            {
                child.style.display = "block";
            }
            else
            {
                child.style.display = "none";
            }
        }
    }

    return BDB;
  }
);
