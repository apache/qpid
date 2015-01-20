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
define(["dojo/dom","dojo/query","dijit/registry","qpid/common/util"],
    function (dom, query, registry, util)
    {
        var addACLFileAccessControlProvider =
        {
            init: function()
            {
                // Readers are HTML5
                this.reader = window.FileReader ? new FileReader() : undefined;
            },
            show: function(data)
            {
                var that=this;
                util.parseHtmlIntoDiv(data.containerNode, "accesscontrolprovider/aclfile/add.html");

                this.aclServerPath = registry.byId("addAccessControlProvider.serverPath");
                this.aclUploadFields = dom.byId("addAccessControlProvider.uploadFields");
                this.aclSelectedFileContainer = dom.byId("addAccessControlProvider.selectedFile");
                this.aclSelectedFileStatusContainer = dom.byId("addAccessControlProvider.selectedFileStatus");
                this.aclFile = registry.byId("addAccessControlProvider.file");
                this.aclFileClearButton = registry.byId("addAccessControlProvider.fileClearButton");

                //Only submitted field
                this.aclPath = registry.byId("addAccessControlProvider.path");

                this.addButton = data.parent.addButton;

                if (this.reader)
                {
                  this.reader.onload = function(evt) {that._aclUploadFileComplete(evt);};
                  this.reader.onerror = function(ex) {console.error("Failed to load ACL file", ex);};
                  this.aclFile.on("change", function(selected){that._aclFileChanged(selected)});
                  this.aclFileClearButton.on("click", function(event){that._aclFileClearButtonClicked(event)});
                }
                else
                {
                  // Fall back for IE8/9 which do not support FileReader
                  this.aclUploadFields.style.display = "none";
                }

                this.aclServerPath.on("blur", function(){that._aclServerPathChanged()});
            },
            _aclFileChanged: function (evt)
            {
                // We only ever expect a single file
                var file = this.aclFile.domNode.children[0].files[0];

                this.addButton.setDisabled(true);
                this.aclSelectedFileContainer.innerHTML = file.name;
                this.aclSelectedFileStatusContainer.className = "loadingIcon";

                console.log("Beginning to read ACL file " + file.name);
                this.reader.readAsDataURL(file);
            },
            _aclUploadFileComplete: function(evt)
            {
                var reader = evt.target;
                var result = reader.result;
                console.log("ACL file read complete, contents " + result);
                this.addButton.setDisabled(false);
                this.aclSelectedFileStatusContainer.className = "loadedIcon";

                this.aclServerPath.set("value", "");
                this.aclServerPath.setDisabled(true);
                this.aclServerPath.set("required", false);

                this.aclFileClearButton.setDisabled(false);

                this.aclPath.set("value", result);
            },
             _aclFileClearButtonClicked: function(event)
            {
                this.aclFile.reset();
                this.aclSelectedFileStatusContainer.className = "";
                this.aclSelectedFileContainer.innerHTML = "";
                this.aclServerPath.set("required", true);
                this.aclServerPath.setDisabled(false);
                this.aclFileClearButton.setDisabled(true);

                this.aclPath.set("value", "");
            },
            _aclServerPathChanged: function()
            {
                var serverPathValue = this.aclServerPath.get("value");
                this.aclPath.set("value", serverPathValue);
            }
        };

        try
        {
            addACLFileAccessControlProvider.init();
        }
        catch(e)
        {
            console.warn(e);
        }
        return addACLFileAccessControlProvider;
    }
);
