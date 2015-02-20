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
define(["dojo/_base/xhr","dojo/query","dijit/registry","qpid/common/util","qpid/common/metadata","dojo/store/Memory","dijit/form/FilteringSelect","dijit/form/ValidationTextBox","dijit/form/CheckBox"],
    function (xhr, query, registry, util, metadata, Memory)
    {
        return {
            show: function(data)
            {
                var that = this;
                util.parseHtmlIntoDiv(data.containerNode, "authenticationprovider/simpleldap/add.html", function(){that._postParse(data);});
            },
            _postParse: function(data)
            {
                var that = this;
                xhr.get({url: "api/latest/truststore", sync: true, handleAs: "json"}).then(
                    function(trustStores)
                    {
                        that._initTrustStores(trustStores, data.containerNode);
                    }
                );

                if (data.data)
                {
                    this._initFields(data.data, data.containerNode );
                }
            },
            _initTrustStores: function(trustStores, containerNode)
            {
                var data = [];
                for (var i=0; i< trustStores.length; i++)
                {
                    data.push( {id: trustStores[i].name, name: trustStores[i].name} );
                }
                var trustStoresStore = new Memory({ data: data });

                var trustStore = registry.byNode(query(".trustStore", containerNode)[0]);
                trustStore.set("store", trustStoresStore);
            },
            _initFields:function(data, containerNode)
            {
                var attributes = metadata.getMetaData("AuthenticationProvider", "SimpleLDAP").attributes;
                for(var name in attributes)
                {
                    var domNode = query("." + name, containerNode)[0];
                    if (domNode)
                    {
                        var widget = registry.byNode(domNode);
                        if (widget)
                        {
                            if (widget instanceof dijit.form.CheckBox)
                            {
                                widget.set("checked", data[name]);
                            }
                            else
                            {
                                widget.set("value", data[name]);
                            }
                        }
                    }
                }
            }
        };
    }
);
