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
define(["dojo/_base/xhr",
        "dojo/parser",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/json",
        "dijit/registry",
        "dojo/text!virtualhostnode/bdb_ha/add/existinggroup/add.html",
        "qpid/common/util",
        "dijit/form/ValidationTextBox",
        "dijit/form/CheckBox",
        "dojo/domReady!"],
  function (xhr, parser, dom, domConstruct, json, registry, template, util)
  {
    return {
        show: function(data)
        {
            this.containerNode = domConstruct.create("div", {innerHTML: template}, data.containerNode);
            parser.parse(this.containerNode).then(function(instances)
            {
                registry.byId("addVirtualHostNode.groupName").set("regExpGen", util.nameOrContextVarRegexp);
                registry.byId("addVirtualHostNode.helperNodeName").set("regExpGen", util.nameOrContextVarRegexp);
                registry.byId("addVirtualHostNode.helperAddress").set("regExpGen", util.nodeAddressOrContextVarRegexp);
                registry.byId("addVirtualHostNode.address").set("regExpGen", util.nodeAddressOrContextVarRegexp);

                dom.byId("addVirtualHostNode.uploadFields").style.display = "none";
            });
        }
    };
  }
);
