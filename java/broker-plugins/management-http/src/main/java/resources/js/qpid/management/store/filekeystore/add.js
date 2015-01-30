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
define(["dojo/dom","dojo/query", "dojo/_base/array", "dijit/registry","qpid/common/util", "qpid/common/metadata"],
    function (dom, query, array, registry, util, metadata)
    {
        var addKeyStore =
        {
            init: function()
            {
            },
            show: function(data)
            {
                var that=this;
                util.parseHtmlIntoDiv(data.containerNode, "store/filekeystore/add.html");

                this.containerNode = data.containerNode;

                this.keyStoreOldBrowserWarning = dom.byId("addStore.oldBrowserWarning");

                this.addButton = data.parent.addButton;

                if (!window.FileReader)
                {
                  this.keyStoreOldBrowserWarning.innerHTML = "File upload requires a more recent browser with HTML5 support";
                  this.keyStoreOldBrowserWarning.className = this.keyStoreOldBrowserWarning.className.replace("hidden", "");
                }
            },
            update: function(effectiveData)
            {
                var attributes = metadata.getMetaData("KeyStore", "FileKeyStore").attributes;
                var widgets = registry.findWidgets(this.containerNode);
                array.forEach(widgets, function(item)
                    {
                        var name = item.id.replace("addStore.","");
                        if (name in attributes )
                        {
                            if  (item.type != "password")
                            {
                                item.set("value", effectiveData[name]);
                            }
                            else
                            {
                                item.set("required", effectiveData?false:true);
                            }
                        }
                    });
            }
        };

        try
        {
            addKeyStore.init();
        }
        catch(e)
        {
            console.warn(e);
        }
        return addKeyStore;
    }
);
