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
        "dojo/parser",
        "dojo/query",
        "dojo/dom-construct",
        "dojo/_base/connect",
        "dojo/_base/window",
        "dojo/_base/event",
        "dojo/_base/json",
        "dijit/registry",
        "dojox/html/entities",
        "qpid/common/util",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/UpdatableStore",
        "dojox/grid/EnhancedGrid",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojox/validate/us", "dojox/validate/web",
        "dijit/Dialog",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/TimeTextBox", "dijit/form/Button",
        "dijit/form/Form",
        "dijit/form/DateTextBox",
        "dojo/domReady!"],
    function (xhr, dom, parser, query, construct, connect, win, event, json, registry, entities, util, properties, updater, UpdatableStore, EnhancedGrid) {
        function AclFile(containerNode, aclProviderObj, controller) {
            var node = construct.create("div", null, containerNode, "last");
            var that = this;
            this.name = aclProviderObj.name;
            xhr.get({url: "accesscontrolprovider/showAclFile.html",
                                    sync: true,
                                    load:  function(data) {
                                        node.innerHTML = data;
                                        parser.parse(node).then(function(instances)
                                        {
                                        that.groupDatabaseUpdater= new AclFileUpdater(node, aclProviderObj, controller);

                                        updater.add( that.groupDatabaseUpdater);

                                        that.groupDatabaseUpdater.update();
                                        });

                                    }});
        }

        AclFile.prototype.close = function() {
            updater.remove( this.groupDatabaseUpdater );
        };

        function AclFileUpdater(node, aclProviderObj, controller)
        {
            this.controller = controller;
            this.query = "api/latest/accesscontrolprovider/"+encodeURIComponent(aclProviderObj.name);
            this.name = aclProviderObj.name;
            this.path = query(".path", node)[0];
        }

        AclFileUpdater.prototype.update = function()
        {
            var that = this;

            xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                .then(function(data) {
                  if (data[0])
                  {
                    that.aclProviderData = data[0];
                    that.path.innerHTML = entities.encode(String(that.aclProviderData.path));
                  }
                });

        };

        return AclFile;
    });
