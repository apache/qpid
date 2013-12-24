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
        "dojo/parser",
        "dojo/string",
        "dojox/html/entities",
        "dojo/query",
        "dijit/registry",
        "dojox/grid/EnhancedGrid",
        "qpid/common/UpdatableStore",
        "qpid/management/UserPreferences",
        "dojo/domReady!"],
    function (xhr, parser, json, entities, query, registry, EnhancedGrid, UpdatableStore, UserPreferences) {

  var hostFields = ["desiredState", "quiesceOnMasterChange"];

  var nodeFields = ["storePath", "name", "state", "role", "hostPort", "helperHostPort",
                    "coalescingSync", "designatedPrimary", "durability", "priority", "quorumOverride"];

  var buttons    = ["activateVirtualHostButton", "quiesceVirtualHostButton", "stopVirtualHostButton",
                    "editVirtualHostButton", "activateNodeButton", "stopNodeButton","editNodeButton",
                    "removeNodeButton", "transferMasterButton"];

  function findNode(nodeClass, containerNode)
  {
    return query("." + nodeClass, containerNode)[0];
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
    for(var i = 0; i < hostFields.length; i++)
    {
      var name = hostFields[i];
      this[name].innerHTML = entities.encode(String(data[name]));
    }
    var nodes = data.replicationnodes;
    if (nodes && nodes.length>0)
    {
      var localNode = nodes[0];
      for(var i = 0; i < nodeFields.length; i++)
      {
        var name = nodeFields[i];
        this[name].innerHTML = entities.encode(String(localNode[name]));
      }
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
      this.membersGrid.update(nodes);
      this.membersGrid.grid._refresh();
    }
  };

  BDBHA.prototype._init = function(containerNode)
  {
    this._initFields(hostFields, containerNode);
    this._initFields(nodeFields, containerNode);

    for(var i = 0; i < buttons.length; i++)
    {
      var buttonName = buttons[i];
      var buttonNode = findNode(buttonName, containerNode);
      if (buttonNode)
      {
        var buttonWidget = registry.byNode(buttonNode);
        if (buttonWidget)
        {
          this[buttonName] = buttonWidget;
          var handler = this["_onClick_" + buttonName];
          if (handler)
          {
            buttonWidget.on("click", function(evt){
              handler(evt);
            });
          }
          else
          {
            buttonWidget.set("disabled", true);
          }
        }
      }
    }

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
