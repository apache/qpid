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
define(["dojo/_base/xhr",
        "dojo/_base/connect",
        "dojox/html/entities",
        "dojo/query",
        "dojo/json",
        "dijit/registry",
        "dojox/grid/EnhancedGrid",
        "qpid/common/UpdatableStore",
        "qpid/management/UserPreferences",
        "qpid/common/util",
        "dojo/domReady!"],
  function (xhr, connect, entities, query, json, registry, EnhancedGrid, UpdatableStore, UserPreferences, util)
  {
    var priorityNames = {'_0': 'Never', '_1': 'Default', '_2': 'Normal', '_3': 'High'};
    var nodeFields = ["storePath", "groupName", "role", "address", "designatedPrimary", "priority", "quorumOverride"];

    function findNode(nodeClass, containerNode)
    {
      return query("." + nodeClass, containerNode)[0];
    }

    function sendRequest(nodeName, remoteNodeName, method, attributes)
    {
        var success = false;
        var failureReason = "";
        var url = null;
        if (nodeName == remoteNodeName)
        {
          url = "api/latest/virtualhostnode/" + encodeURIComponent(nodeName);
        }
        else
        {
          url = "api/latest/replicationnode/" + encodeURIComponent(nodeName) + "/" + encodeURIComponent(remoteNodeName);
        }

        if (method == "POST")
        {
          xhr.put({
            url: url,
            sync: true,
            handleAs: "json",
            headers: { "Content-Type": "application/json"},
            putData: json.stringify(attributes),
            load: function(x) {success = true; },
            error: function(error) {success = false; failureReason = error;}
          });
        }
        else if (method == "DELETE")
        {
          xhr.del({url: url, sync: true, handleAs: "json"}).then(
                function(data) { success = true; },
                function(error) {success = false; failureReason = error;}
          );
        }

        if (!success)
        {
            alert("Error:" + failureReason);
        }
        return success;
    }

    function BDBHA(data)
    {
      var containerNode = data.containerNode;
      this.parent = data.parent;
      var that = this;
      util.buildUI(data.containerNode, data.parent, "virtualhostnode/bdb_ha/show.html", nodeFields, this);

      this.designatedPrimaryContainer = findNode("designatedPrimaryContainer", containerNode);
      this.priorityContainer = findNode("priorityContainer", containerNode);
      this.quorumOverrideContainer = findNode("quorumOverrideContainer", containerNode);

      this.membersGridPanel = registry.byNode(query(".membersGridPanel", containerNode)[0]);
      this.membersGrid = new UpdatableStore([],
          findNode("groupMembers", containerNode),
          [
           { name: 'Name', field: 'name', width: '10%' },
           { name: 'Role', field: 'role', width: '10%' },
           { name: 'Address', field: 'address', width: '35%' },
           { name: 'Join Time', field: 'joinTime', width: '25%', formatter: function(value){ return value ? UserPreferences.formatDateTime(value) : "";} },
           { name: 'Replication Transaction ID', field: 'lastKnownReplicationTransactionId', width: '20%', formatter: function(value){ return value > 0 ? value : "N/A";} }
          ],
          null,
          {
            selectionMode: "single",
            keepSelection: true,
            plugins: {
              indirectSelection: true
            }
          },
          EnhancedGrid, true );

      this.removeNodeButton = registry.byNode(query(".removeNodeButton", containerNode)[0]);
      this.transferMasterButton = registry.byNode(query(".transferMasterButton", containerNode)[0]);
      this.transferMasterButton.set("disabled", true);
      this.removeNodeButton.set("disabled", true);

      var nodeControlsToggler = function(rowIndex)
      {
        var data = that.membersGrid.grid.selection.getSelected();
        that.transferMasterButton.set("disabled", data.length != 1|| data[0].role != "REPLICA");
        that.removeNodeButton.set("disabled", data.length != 1 || data[0].role == "MASTER");
      };
      connect.connect(this.membersGrid.grid.selection, 'onSelected',  nodeControlsToggler);
      connect.connect(this.membersGrid.grid.selection, 'onDeselected',  nodeControlsToggler);

      this.transferMasterButton.on("click",
          function(e)
          {
            var data = that.membersGrid.grid.selection.getSelected();
            if (data.length == 1 && confirm("Are you sure you would like to transfer mastership to node '" + data[0].name + "'?"))
            {
                sendRequest(that.data.name, data[0].name, "POST", {role: "MASTER"});
                that.membersGrid.grid.selection.clear();
            }
          }
      );

      this.removeNodeButton.on("click",
          function(e){
            var data = that.membersGrid.grid.selection.getSelected();
            if (data.length == 1 && confirm("Are you sure you would like to delete node '" + data[0].name + "'?"))
            {
                if (sendRequest(that.data.name, data[0].name, "DELETE"))
                {
                  that.membersGrid.grid.selection.clear();
                  if (data[0].name == that.data.name)
                  {
                    that.parent.destroy();
                  }
                }
            }
          }
      );
    }

    BDBHA.prototype.update=function(data)
    {
      this.parent.editNodeButton.set("disabled", false);
      this.data = data;
      for(var i = 0; i < nodeFields.length; i++)
      {
        var name = nodeFields[i];
        if (name == "priority")
        {
          this[name].innerHTML = priorityNames["_" + data[name]];
        }
        else if (name == "quorumOverride")
        {
          this[name].innerHTML = (data[name] == 0 ? "MAJORITY" : entities.encode(String(data[name])));
        }
        else
        {
          this[name].innerHTML = entities.encode(String(data[name]));
        }
      }

      var members = data.remotereplicationnodes;
      if (members)
      {
        members.push({
          id: data.id,
          name: data.name,
          groupName: data.groupName,
          address: data.address,
          role: data.role,
          joinTime: data.joinTime,
          lastKnownReplicationTransactionId: data.lastKnownReplicationTransactionId
        });
      }
      this._updateGrid(members, this.membersGridPanel, this.membersGrid);

      if (!members || members.length < 3)
      {
        this.designatedPrimaryContainer.style.display="block";
        this.priorityContainer.style.display="none";
        this.quorumOverrideContainer.style.display="none";
      }
      else
      {
        this.designatedPrimaryContainer.style.display="none";
        this.priorityContainer.style.display="block";
        this.quorumOverrideContainer.style.display="block";
      }
    };

    BDBHA.prototype._updateGrid=function(conf, panel, updatableGrid)
    {
      if (conf && conf.length > 0)
      {
        panel.domNode.style.display="block";
        var changed = updatableGrid.update(conf);
        if (changed)
        {
          updatableGrid.grid._refresh();
        }
      }
      else
      {
        panel.domNode.style.display="none";
      }
    }

    return BDBHA;
});
