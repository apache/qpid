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
define(["dojo/_base/xhr", "dojo/domReady!"], function (xhr) {

    var preferencesDialog = null;
    var helpURL = null;

    return {
        showPreferencesDialog: function () {
          if (preferencesDialog == null)
          {
             require(["qpid/management/Preferences", "dojo/ready"], function(PreferencesDialog, ready){
                ready(function(){
                  preferencesDialog = new PreferencesDialog();
                  preferencesDialog.showDialog();
                });
             });
          }
          else
          {
              preferencesDialog.showDialog();
          }
        },
        getHelpUrl: function()
        {
          if (!helpURL)
          {
            xhr.get({
              sync: true,
              url: "api/latest/broker",
              content: { "depth" : 1, "includeSysContext" : true },
              handleAs: "json"
             }).then(function(data) {
              var broker = data[0];
              if ("context" in broker && "qpid.helpURL" in broker["context"] )
              {
                helpURL = broker["context"]["qpid.helpURL"];
              }
              else
              {
                helpURL = "http://qpid.apache.org/";
              }
             });
          }
          return helpURL;
        },
        showHelp: function()
        {
          var newWindow = window.open(this.getHelpUrl(),'QpidHelp','height=600,width=600,scrollbars=1,location=1,resizable=1,status=0,toolbar=0,titlebar=1,menubar=0', true);
          newWindow.focus();
        }
    };

});
