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
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser) {
        var fieldNames = ["maxConnectionsPerPartition", "minConnectionsPerPartition", "partitionCount"];
        return {
            show: function(data) {
                var that = this;
                xhr.get({url: "virtualhost/store/pool/bonecp/add.html",
                     sync: true,
                     load:  function(template) {
                        for ( var i = 0 ; i < fieldNames.length; i++ )
                        {
                          var widgetName = fieldNames[i];
                          var widget = registry.byId("formAddVirtualHost.qpid.jdbcstore.bonecp." + widgetName);
                          if (widget)
                          {
                             widget.destroyRecursive();
                          }
                        }
                        data.containerNode.innerHTML = template;
                        parser.parse(data.containerNode);
                        for ( var i = 0 ; i < fieldNames.length; i++ )
                        {
                          var widgetName = fieldNames[i];
                          registry.byId("formAddVirtualHost.qpid.jdbcstore.bonecp." + widgetName).set("value", data.data.context["qpid.jdbcstore.bonecp." + widgetName]);
                        }
                     }});
            }
        };
    });
