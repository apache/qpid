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
define(["dojo/dom",
        "dojo/query",
        "dojo/_base/array",
        "dijit/registry",
        "qpid/common/util",
        "qpid/common/metadata",
        "dojo/parser",
        "dojo/text!store/nonjavatruststore/add.html",
        "dojo/domReady!"],
    function (dom, query, array, registry, util, metadata, parser, template)
    {
        var addKeyStore =
        {
            show: function(data)
            {
                var that=this;
                this.containerNode = data.containerNode;
                data.containerNode.innerHTML = template;
                parser.parse(this.containerNode).then(function(instances)
                {
                    that.keyStoreOldBrowserWarning = dom.byId("addStore.oldBrowserWarning");

                    if (!window.FileReader)
                    {
                      that.keyStoreOldBrowserWarning.innerHTML = "File upload requires a more recent browser with HTML5 support";
                      that.keyStoreOldBrowserWarning.className = that.keyStoreOldBrowserWarning.className.replace("hidden", "");
                    }

                    if (data.effectiveData)
                    {
                        that.update(data.effectiveData);
                    }

                    util.applyMetadataToWidgets(data.containerNode, "TrustStore", "NonJavaTrustStore");
                });
            },
            update: function(effectiveData)
            {
                var attributes = metadata.getMetaData("TrustStore", "NonJavaTrustStore").attributes;
                var widgets = registry.findWidgets(this.containerNode);
                array.forEach(widgets, function(item)
                    {
                        var name = item.id.replace("addStore.","");
                        if (name in attributes )
                        {
                            var attribute = attributes[name];
                            var value = effectiveData[name];
                            if (value)
                            {
                                if (attribute.secure)
                                {
                                     if (!/^\*+/.test(value) )
                                     {
                                        item.set("value", value);
                                     }
                                     else
                                     {
                                        item.set("placeHolder", value);
                                        item.set("required", false);
                                     }
                                }
                                else
                                {
                                    item.set("value", value);
                                }
                            }
                        }
                    });
            }
        };

        return addKeyStore;
    }
);
