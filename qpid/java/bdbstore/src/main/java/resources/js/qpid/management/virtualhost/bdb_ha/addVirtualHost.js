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
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/json",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, json) {
        var nodeFields = ["storePath", "groupName", "nodeName", "state", "role", "hostPort", "helperHostPort",
                    "coalescingSync", "designatedPrimary", "durability", "priority",
                    "quorumOverride"];

        return {
            show: function() {

                var node = dom.byId("addVirtualHost.typeSpecificDiv");
                var that = this;

                array.forEach(registry.toArray(),
                              function(item) {
                                  if(item.id.substr(0,27) == "formAddVirtualHost.specific") {
                                      item.destroyRecursive();
                                  }
                              });

                xhr.get({url: "virtualhost/bdb_ha/add.html",
                     sync: true,
                     load:  function(data) {
                                node.innerHTML = data;
                                parser.parse(node);
                                for(var i = 0; i < nodeFields.length; i++)
                                {
                                  that[nodeFields[i]] = registry.byId("formAddVirtualHost.specific." + nodeFields[i]);
                                }
                     }});
            },
            save: function()
            {
              this.success = false;
              var that = this;
              var virtualHostName = registry.byId("formAddVirtualHost.name").get("value");
              var virtualHostNameEncoded = encodeURIComponent(virtualHostName);


              // create virtual host in QUIESCED state
              var hostData =
              {
                  state: "QUIESCED",
                  name: virtualHostName,
                  type: registry.byId("addVirtualHost.type").get("value"),
              };

              xhr.put({url: "rest/virtualhost/" + virtualHostNameEncoded,
                sync: true, handleAs: "json",
                headers: { "Content-Type": "application/json"},
                putData: json.stringify(hostData),
                load: function(x) { that.success = true; },
                error: function(error) {that.success = false; that.failureReason = error;}});

              // if success, create node
              if (this.success)
              {
                var node = {};
                for(var i = 0; i < nodeFields.length; i++)
                {
                  var fieldName = nodeFields[i];
                  var widget = this[fieldName];
                  if (widget)
                  {
                    node[fieldName] = widget.type=="checkbox"? widget.get("checked"): widget.get("value");
                  }
                }

                node.name = this.nodeName.value;
                xhr.put({url: "rest/replicationnode/" + virtualHostNameEncoded + "/" + encodeURIComponent(this.nodeName.value),
                  sync: true, handleAs: "json",
                  headers: { "Content-Type": "application/json"},
                  putData: json.stringify(node),
                  load: function(x) { that.success = true; },
                  error: function(error) {that.success = false; that.failureReason = error;}});

                // if success, change virtual host state to ACTIVE
                if (this.success)
                {
                  xhr.put({url: "rest/virtualhost/" + virtualHostNameEncoded,
                    sync: true, handleAs: "json",
                    headers: { "Content-Type": "application/json"},
                    putData: json.stringify({state: "ACTIVE"}),
                    load: function(x) { that.success = true; },
                    error: function(error) {that.success = false; that.failureReason = error;}});
                }
              }

              return this.success;
            }
        };
    });
