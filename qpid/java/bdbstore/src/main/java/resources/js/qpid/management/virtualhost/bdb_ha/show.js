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
        "dojo/_base/lang",
        "dojo/_base/connect",
        "dojo/parser",
        "dojo/string",
        "dojox/html/entities",
        "dojo/query",
        "dojo/json",
        "dijit/registry",
        "dojox/grid/EnhancedGrid",
        "qpid/common/UpdatableStore",
        "qpid/management/UserPreferences",
        "qpid/management/virtualhost/bdb_ha/edit",
        "dojo/domReady!"],
    function (xhr, lang, connect, parser, json, entities, query, json, registry, EnhancedGrid, UpdatableStore, UserPreferences, nodeEditor) {

  var hostFields = ["desiredState", "quiesceOnMasterChange"];

  var nodeFields = ["storePath", "name", "state", "role", "hostPort", "helperHostPort",
                    "coalescingSync", "designatedPrimary", "durability", "priority", "quorumOverride"];

  var buttons    = ["activateVirtualHostButton", "quiesceVirtualHostButton", "stopVirtualHostButton",
                    "editVirtualHostButton", "removeNodeButton"];

  function findNode(nodeClass, containerNode)
  {
    return query("." + nodeClass, containerNode)[0];
  }

  function convertMap(map)
  {
    var data =[];
    for(var propName in map)
    {
        if(map.hasOwnProperty(propName))
        {
          data.push({id: propName, name: propName, value: map[propName]});
        }
    }
    return data;
  }

  function BDBHA(containerNode) {
    var that = this;
    xhr.get({url: "virtualhost/bdb_ha/show.html",
      sync: true,
      load:  function(template) {
        containerNode.innerHTML = template;
        parser.parse(containerNode);
      }});
    this._init(containerNode);
    this.designatedPrimaryContainer = findNode("designatedPrimaryContainer", containerNode);
    this.priorityContainer = findNode("priorityContainer", containerNode);
    this.quorumOverrideContainer = findNode("quorumOverrideContainer", containerNode);
  }

  BDBHA.prototype.update=function(data)
  {
    this.data = data
    for(var i = 0; i < hostFields.length; i++)
    {
      var name = hostFields[i];
      this[name].innerHTML = entities.encode(String(data[name]));
    }
    var nodes = data.replicationnodes;
    if (nodes && nodes.length>0)
    {
      var localNode = nodes[0];
      this.stopNodeButton.set("disabled", localNode.state == "STOPPED");
      this.activateNodeButton.set("disabled", localNode.state == "ACTIVE");
      for(var i = 0; i < nodeFields.length; i++)
      {
        var name = nodeFields[i];
        this[name].innerHTML = entities.encode(String(localNode[name]));
      }
      this.parametersGrid.update(convertMap(localNode.parameters));
      this.parametersGrid.grid._refresh();
      this.replicationParametersGrid.update(convertMap(localNode.replicationParameters));
      this.replicationParametersGrid.grid._refresh();
      if (nodes.length < 3)
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
      if (this.membersGrid.update(nodes))
      {
        this.membersGrid.grid._refresh();
      }
    }
  };

  BDBHA.prototype._init = function(containerNode)
  {
    this._initFields(hostFields, containerNode);
    this._initFields(nodeFields, containerNode);

    var that = this;
    for(var i = 0; i < buttons.length; i++)
    {
      var buttonName = buttons[i];
      var buttonNode = findNode(buttonName, containerNode);
      if (buttonNode)
      {
        var buttonWidget = registry.byNode(buttonNode);
        if (buttonWidget)
        {
            buttonWidget.set("disabled", true);
        }
      }
    }

    this.editNodeButton = registry.byNode(findNode("editNodeButton", containerNode));
    this.editNodeButton.on("click", function(e){
        var nodeName = null;
        if (that.data.replicationnodes && that.data.replicationnodes.length > 0)
        {
            nodeName = that.data.replicationnodes[0].name;
        }
        nodeEditor.show(that.data.name, nodeName);
    });

    function changeNodeAttributes(nodeName, data)
    {
        var success = false;
        var failureReason = "";
        xhr.put({
            url: "rest/replicationnode/" + encodeURIComponent(that.data.name) + "/" + encodeURIComponent(nodeName),
            sync: true,
            handleAs: "json",
            headers: { "Content-Type": "application/json"},
            putData: json.stringify(data),
            load: function(x) {success = true; },
            error: function(error) {success = false; failureReason = error;}
        });
        if (!success)
        {
            alert("Error:" + failureReason);
        }
    }

    function changeStatus(newState)
    {
        if (confirm("Are you sure you would like to change node state to '" + newState + "'?"))
        {
            changeNodeAttributes(that.data.replicationnodes[0].name, {desiredState: newState});
        }
    }

    this.stopNodeButton = registry.byNode(findNode("stopNodeButton", containerNode));
    this.stopNodeButton.on("click", function(e){changeStatus("STOPPED");});
    this.activateNodeButton = registry.byNode(findNode("activateNodeButton", containerNode));
    this.activateNodeButton.on("click", function(e){changeStatus("ACTIVE");});
    this.transferMasterButton = registry.byNode(findNode("transferMasterButton", containerNode));
    this.transferMasterButton.on("click", function(e){
        var data = that.membersGrid.grid.selection.getSelected();
        if (data.length == 1 && confirm("Are you sure you would like to transfer mastership to node '" + data[0].name + "'?"))
        {
            changeNodeAttributes(data[0].name, {role: "MASTER"});
            that.membersGrid.grid.selection.clear();
        }
    });
    this.transferMasterButton.set("disabled", true);

    this.membersGrid = new UpdatableStore([],
        findNode("cluserNodes", containerNode),
        [
         { name: 'Name', field: 'name', width: '10%' },
         { name: 'Role', field: 'role', width: '10%' },
         { name: 'Host and Port', field: 'hostPort', width: '35%' },
         { name: 'Join Time', field: 'joinTime', width: '25%', formatter: function(value){ return value ? UserPreferences.formatDateTime(value) : "";} },
         { name: 'Replication Transaction ID', field: 'lastKnownReplicationTransactionId', width: '20%' }
        ],
        null,
        {
          keepSelection: true,
          plugins: {
            indirectSelection: true
          }
        },
        EnhancedGrid, true );

    var transferMasterToggler = function(rowIndex){
        var data = that.membersGrid.grid.selection.getSelected();
        var isButtonDisabled = data.length != 1 || data[0].role != "REPLICA";
        that.transferMasterButton.set("disabled", isButtonDisabled);
      };
      connect.connect(this.membersGrid.grid.selection, 'onSelected',  transferMasterToggler);
      connect.connect(this.membersGrid.grid.selection, 'onDeselected',  transferMasterToggler);

    this.parametersGrid = new UpdatableStore([],
        findNode("parameters", containerNode),
        [
         { name: 'Name', field: 'name', width: '50%' },
         { name: 'Value', field: 'value', width: '50%' }
        ],
        null, null, null, true );

    this.replicationParametersGrid = new UpdatableStore([],
        findNode("replicationParameters", containerNode),
        [
         { name: 'Name', field: 'name', width: '50%' },
         { name: 'Value', field: 'value', width: '50%' }
        ],
        null, null, null, true );
  }

  BDBHA.prototype._initFields = function(nodeFields, containerNode)
  {
    for(var i = 0; i < nodeFields.length; i++)
    {
      this[nodeFields[i]] = findNode(nodeFields[i], containerNode);
    }
  }

  return BDBHA;
});
