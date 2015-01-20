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
                // Readers are HTML5
                this.reader = window.FileReader ? new FileReader() : undefined;
            },
            show: function(data)
            {
                var that=this;
                util.parseHtmlIntoDiv(data.containerNode, "store/filekeystore/add.html");

                this.containerNode = data.containerNode;
                this.keyStoreServerPath = registry.byId("addStore.serverPath");
                this.keyStoreUploadFields = dom.byId("addStore.uploadFields");
                this.keyStoreSelectedFileContainer = dom.byId("addStore.selectedFile");
                this.keyStoreSelectedFileStatusContainer = dom.byId("addStore.selectedFileStatus");
                this.keyStoreFile = registry.byId("addStore.file");
                this.keyStoreFileClearButton = registry.byId("addStore.fileClearButton");
                this.keyStoreOldBrowserWarning = dom.byId("addStore.oldBrowserWarning");

                //Only submitted field
                this.keyStorePath = registry.byId("addStore.path");

                this.addButton = data.parent.addButton;

                if (this.reader)
                {
                  this.reader.onload = function(evt) {that._keyStoreUploadFileComplete(evt);};
                  this.reader.onerror = function(ex) {console.error("Failed to load key store file", ex);};
                  this.keyStoreFile.on("change", function(selected){that._keyStoreFileChanged(selected)});
                  this.keyStoreFileClearButton.on("click", function(event){that._keyStoreFileClearButtonClicked(event)});
                }
                else
                {
                  // Fall back for IE8/9 which do not support FileReader
                  this.keyStoreUploadFields.style.display = "none";
                  this.keyStoreOldBrowserWarning.innerHTML = "File upload requires a more recent browser with HTML5 support";
                  this.keyStoreOldBrowserWarning.className = this.keyStoreOldBrowserWarning.className.replace("hidden", "");
                }

                this.keyStoreServerPath.on("blur", function(){that._keyStoreServerPathChanged()});
            },
            _keyStoreFileChanged: function (evt)
            {
                // We only ever expect a single file
                var file = this.keyStoreFile.domNode.children[0].files[0];

                this.addButton.setDisabled(true);
                this.keyStoreSelectedFileContainer.innerHTML = file.name;
                this.keyStoreSelectedFileStatusContainer.className = "loadingIcon";

                console.log("Beginning to read key store file " + file.name);
                this.reader.readAsDataURL(file);
            },
            _keyStoreUploadFileComplete: function(evt)
            {
                var reader = evt.target;
                var result = reader.result;
                console.log("Key store file read complete, contents " + result);
                this.addButton.setDisabled(false);
                this.keyStoreSelectedFileStatusContainer.className = "loadedIcon";

                this.keyStoreServerPath.set("value", "");
                this.keyStoreServerPath.setDisabled(true);
                this.keyStoreServerPath.set("required", false);

                this.keyStoreFileClearButton.setDisabled(false);

                this.keyStorePath.set("value", result);
            },
             _keyStoreFileClearButtonClicked: function(event)
            {
                this.keyStoreFile.reset();
                this.keyStoreSelectedFileStatusContainer.className = "";
                this.keyStoreSelectedFileContainer.innerHTML = "";
                this.keyStoreServerPath.set("required", true);
                this.keyStoreServerPath.setDisabled(false);
                this.keyStoreFileClearButton.setDisabled(true);

                this.keyStorePath.set("value", "");
            },
            _keyStoreServerPathChanged: function()
            {
                var serverPathValue = this.keyStoreServerPath.get("value");
                this.keyStorePath.set("value", serverPathValue);
            },
            update: function(effectiveData)
            {
                var attributes = metadata.getMetaData("KeyStore", "FileKeyStore").attributes;
                var widgets = registry.findWidgets(this.containerNode);
                array.forEach(widgets, function(item)
                    {
                        var name = item.id.replace("addStore.","");
                        if (name in attributes && item.type != "password")
                        {
                            item.set("value", effectiveData[name]);
                        }
                    });

                var keyStorePathValue = effectiveData["path"];
                var isDataUrl = keyStorePathValue.indexOf("data:") == 0;

                if (isDataUrl)
                {
                     this.keyStoreSelectedFileStatusContainer.className = "loadedIcon";
                     this.keyStoreSelectedFileContainer.innerHTML = "uploaded.jks";
                     this.keyStoreServerPath.setDisabled(true);
                     this.keyStoreServerPath.set("required", false);
                     this.keyStoreFileClearButton.setDisabled(false);
                }
                else
                {
                     this.keyStoreServerPath.set("value", keyStorePathValue);
                }
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
