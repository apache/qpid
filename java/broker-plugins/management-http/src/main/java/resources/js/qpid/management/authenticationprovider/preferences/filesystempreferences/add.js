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
        "dojox/html/entities",
        "dojo/text!../../../../../authenticationprovider/preferences/filesystempreferences/add.html",
        "dojo/domReady!"],
    function (xhr, dom, domConstruct, win, registry, parser, array, event, json, string, entities, template) {
        return {
            show: function(node, data)
            {
                this.destroy();
                this.node = node;
                node.innerHTML = template;
                parser.parse(node);
                var pathWidget = registry.byId("preferencesProvider.path")
                if (data)
                {
                  pathWidget.set("value", entities.encode(String(data["path"])));
                }
            },
            destroy: function()
            {
              if (this.node)
              {
                dojo.forEach(dijit.findWidgets(this.node), function(w) {w.destroyRecursive();});
                this.node = null;
              }
            },
            disable: function(val)
            {
                var pathWidget = registry.byId("preferencesProvider.path");
                pathWidget.set("disabled", val);
            },
            getValues: function()
            {
              return {path: registry.byId("preferencesProvider.path").value};
            }
        };
    });
