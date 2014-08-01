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
define(["qpid/common/util",
        "dijit/registry",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dojo/domReady!"],
   function (util, registry, Memory, ObjectStore)
   {
       var fields = [ "storePath", "name", "groupName", "address",
                      "designatedPrimary", "priority",  "quorumOverride"];

       return {
           show: function(data)
           {
              var node = data.data;
              util.buildEditUI(data.containerNode, "virtualhostnode/bdb_ha/edit.html", "editVirtualHostNode.", fields, node);
              if ( !(data.data.state == "ERRORED" || data.data.state == "STOPPED"))
              {
                  registry.byId("editVirtualHostNode.storePath").set("disabled", true);
              }

              var overrideData = [{id: '0', name: 'Majority', selected: '1'}];
              if (node.remotereplicationnodes && node.remotereplicationnodes.length>1)
              {
                  registry.byId("editVirtualHostNode.designatedPrimary").set("disabled", true);
                  registry.byId("editVirtualHostNode.priority").set("disabled", false);
                  registry.byId("editVirtualHostNode.quorumOverride").set("disabled", false);
                  var overrideLimit = Math.floor((node.remotereplicationnodes.length + 1)/2);
                  for(var i = 1; i <= overrideLimit; i++)
                  {
                    overrideData.push({id: i, name: i + ""});
                  }
              }
              else
              {
                  registry.byId("editVirtualHostNode.designatedPrimary").set("disabled", false);
                  registry.byId("editVirtualHostNode.priority").set("disabled", true);
                  registry.byId("editVirtualHostNode.quorumOverride").set("disabled", true);
              }
              var store = new Memory({data :overrideData, idProperty: "id" });
              registry.byId("editVirtualHostNode.quorumOverride").set("store", new ObjectStore({objectStore: store}));
           }
       };
   }
);
