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
                util.parseHtmlIntoDiv(data.containerNode, "store/nonjavatruststore/add.html");

                this.keyStoreOldBrowserWarning = dom.byId("addStore.oldBrowserWarning");
                this.addButton = data.parent.addButton;
                this.containerNode = data.containerNode;

                if (!window.FileReader)
                {
                  this.keyStoreOldBrowserWarning.innerHTML = "File upload requires a more recent browser with HTML5 support";
                  this.keyStoreOldBrowserWarning.className = this.keyStoreOldBrowserWarning.className.replace("hidden", "");
                }

                this._initUploadFields("certificates", "certificates");
            },
            _initUploadFields: function(fieldName, description)
            {
                var that=this;
                this[fieldName] = registry.byId("addStore." + fieldName);
                this[fieldName + "UploadFields"] = dom.byId("addStore." + fieldName +"UploadFields");
                this[fieldName + "UploadContainer"] = dom.byId("addStore." + fieldName + "UploadContainer");
                this[fieldName + "UploadStatusContainer"] = dom.byId("addStore." + fieldName + "UploadStatusContainer");
                this[fieldName + "File"] = registry.byId("addStore." + fieldName + "File");
                this[fieldName + "FileClearButton"] = registry.byId("addStore." + fieldName + "FileClearButton");

                // field to submit
                this[fieldName + "Url"] = registry.byId("addStore." + fieldName + "Url");

                if (window.FileReader)
                {
                  this[fieldName + "Reader"] = new FileReader();
                  this[fieldName + "Reader"].onload = function(evt) {that._uploadFileComplete(evt, fieldName);};
                  this[fieldName + "Reader"].onerror = function(ex) {console.error("Failed to load " + description + " file", ex);};
                  this[fieldName + "File"].on("change", function(selected){that._fileChanged(selected, fieldName)});
                  this[fieldName + "FileClearButton"].on("click", function(event){that._fileClearButtonClicked(event, fieldName)});
                }
                else
                {
                  // Fall back for IE8/9 which do not support FileReader
                  this[fieldName + "UploadFields"].style.display = "none";
                }

                this[fieldName].on("blur", function(){that._pathChanged(fieldName)});
            },
            _fileChanged: function (evt, fieldName)
            {
                var file = this[fieldName + "File"].domNode.children[0].files[0];

                this[fieldName + "UploadContainer"].innerHTML = file.name;
                this[fieldName + "UploadStatusContainer"].className = "loadingIcon";

                console.log("Beginning to read  file " + file.name + " for " + fieldName );
                this[fieldName + "Reader"].readAsDataURL(file);
            },
            _uploadFileComplete: function(evt, fieldName)
            {
                var reader = evt.target;
                var result = reader.result;
                console.log(fieldName + " file read complete, contents " + result);

                // it is not clear the purpose of this operation
                //this.addButton.setDisabled(false);
                this[fieldName + "UploadStatusContainer"].className = "loadedIcon";

                this[fieldName].set("value", "");
                this[fieldName].setDisabled(true);
                this[fieldName].set("required", false);

                this[fieldName + "FileClearButton"].setDisabled(false);

                this[fieldName + "Url"].set("value", result);
            },
             _fileClearButtonClicked: function(event, fieldName)
            {
                this[fieldName + "File"].reset();
                this[fieldName + "UploadStatusContainer"].className = "";
                this[fieldName + "UploadContainer"].innerHTML = "";
                this[fieldName].set("required", true);
                this[fieldName].setDisabled(false);
                this[fieldName + "FileClearButton"].setDisabled(true);

                this[fieldName + "Url"].set("value", "");
            },
            _pathChanged: function(fieldName)
            {
                var serverPathValue = this[fieldName].get("value");
                this[fieldName + "Url"].set("value", serverPathValue);
            },
            update: function(effectiveData)
            {
                var attributes = metadata.getMetaData("TrustStore", "NonJavaTrustStore").attributes;
                var widgets = registry.findWidgets(this.containerNode);
                var that=this;
                array.forEach(widgets, function(item)
                    {
                        var name = item.id.replace("addStore.","");
                        var val = effectiveData[name];
                        item.set("value", val);

                        if (name.indexOf("Url") != -1)
                        {
                            var isDataUrl = val && val.indexOf("data:") == 0;
                            var fieldName = name.substring(0, name.length - 3);
                            if (isDataUrl)
                            {
                                  that[fieldName + "UploadStatusContainer"].className = "loadedIcon";
                                  that[fieldName + "UploadContainer"].innerHTML = "uploaded.jks";
                                  that[fieldName].setDisabled(true);
                                  that[fieldName].set("required", false);
                                  that[fieldName + "FileClearButton"].setDisabled(false);
                            }
                            else
                            {
                                  that[fieldName].set("value", val);
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
