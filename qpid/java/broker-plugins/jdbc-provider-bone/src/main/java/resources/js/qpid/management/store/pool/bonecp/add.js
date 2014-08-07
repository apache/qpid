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
        "dojo/_base/event",
        "dojo/_base/json",
        "dojo/string",
        "dojo/store/Memory",
        "dijit/form/FilteringSelect",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, string, Memory, FilteringSelect) {
        var fieldNames = ["maxConnectionsPerPartition", "minConnectionsPerPartition", "partitionCount"];
        return {
            show: function(poolSpecificDivId, formFieldPrefix, htmlUrl) {
                var node = dom.byId(poolSpecificDivId);
                var that = this;

                array.forEach(registry.toArray(),
                              function(item) {
                                  if(item.id.substr(0,formFieldPrefix.length) == formFieldPrefix) {
                                      item.destroyRecursive();
                                  }
                              });

                xhr.get({url: htmlUrl,
                     sync: true,
                     load:  function(data) {

                       node.innerHTML = data;
                       parser.parse(node);

                       for ( var i = 0 ; i < fieldNames.length; i++ )
                       {
                         var widgetName = fieldNames[i];
                         var widget = registry.byId(formFieldPrefix + widgetName);
                         widget.set("regExpGen", util.numericOrContextVarRegexp);
                       }

                     }});
            }
        };
    });
