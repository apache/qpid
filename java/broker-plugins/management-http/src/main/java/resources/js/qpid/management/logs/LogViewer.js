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
        "dojo/parser",
        "dojo/query",
        "dojo/date/locale",
        "dijit/registry",
        "qpid/common/grid/GridUpdater",
        "qpid/management/logs/LogFileDownloadDialog",
        "dojo/text!../../../logs/showLogViewer.html",
        "dojo/domReady!"],
       function (xhr, parser, query, locale, registry, GridUpdater, LogFileDownloadDialog, markup) {

           var defaulGridRowLimit = 4096;

           function LogViewer(name, parent, controller) {
               var self = this;

               this.name = name;
               this.lastLogId = 0;
               this.contentPane = null;
               this.downloadLogsButton = null;
               this.downloadLogDialog = null;
           }

           LogViewer.prototype.getTitle = function() {
               return "Log Viewer";
           };

           LogViewer.prototype.open = function(contentPane) {
               var self = this;
               this.contentPane = contentPane;
               this.contentPane.containerNode.innerHTML = markup;

               parser.parse(this.contentPane.containerNode);

               this.downloadLogsButton = registry.byNode(query(".downloadLogs", contentPane.containerNode)[0]);
               this.downloadLogDialog = new LogFileDownloadDialog();

               this.downloadLogsButton.on("click", function(evt){
                   self.downloadLogDialog.showDialog();
               });

               var gridStructure = [
                    {
                      hidden: true,
                      name: "ID",
                      field: "id",
                      width: "50px",
                      datatype: "number",
                      filterable: true
                    },
                    {
                      name: "Date", field: "timestamp", width: "100px", datatype: "date",
                        formatter: function(val) {
                        var d = new Date(0);
                        d.setUTCSeconds(val/1000);
                        return locale.format(d, {selector:"date", datePattern: "EEE, MMM d yy"});
                      },
                      dataTypeArgs: {
                        selector: "date",
                        datePattern: "EEE MMMM d yyy"
                      }
                    },
                    { name: "Time", field: "timestamp", width: "150px", datatype: "time",
                     formatter: function(val) {
                       var d = new Date(0);
                       d.setUTCSeconds(val/1000);
                       return locale.format(d, {selector:"time", timePattern: "HH:mm:ss z (ZZZZ)"});
                     },
                     dataTypeArgs: {
                       selector: "time",
                       timePattern: "HH:mm:ss ZZZZ"
                     }
                   },
                   { name: "Level", field: "level", width: "50px", datatype: "string", autoComplete: true, hidden: true},
                   { name: "Logger", field: "logger", width: "150px", datatype: "string", autoComplete: false, hidden: true},
                   { name: "Thread", field: "thread", width: "100px", datatype: "string", hidden: true},
                   { name: "Log Message", field: "message", width: "auto", datatype: "string"}
               ];

               this._buildGrid(gridStructure);
           };

           LogViewer.prototype._buildGrid = function(gridStructure) {
               var self = this;
               var gridNode = query("#broker-logfile", this.contentPane.containerNode)[0];
               try
               {
                 this.updater = new GridUpdater({
                   updatable: false,
                   serviceUrl: function()
                   {
                     return "rest/logrecords?lastLogId=" + self.lastLogId;
                   },
                   onUpdate: function(items)
                   {
                     if (items)
                     {
                       var maxId = -1;
                       for(var i in items)
                       {
                         var item = items[i];
                         if (item.id > maxId)
                         {
                           maxId = item.id
                         }
                       }
                       if (maxId != -1)
                       {
                         self.lastLogId = maxId
                       }
                     }
                   },
                   append: true,
                   appendLimit: defaulGridRowLimit,
                   node: gridNode,
                   structure: gridStructure,
                   gridProperties: {
                     selectable: true,
                     selectionMode: "none",
                     sortInfo: -1,
                     sortFields: [{attribute: 'timestamp', descending: true}],
                     plugins:{
                       nestedSorting:true,
                       enhancedFilter:{defaulGridRowLimit: defaulGridRowLimit},
                       indirectSelection: false
                     }
                   },
                   funct: function (obj)
                   {
                     var onStyleRow = function(row)
                     {
                       var item = obj.grid.getItem(row.index);
                       if(item){
                          var level = obj.dataStore.getValue(item, "level", null);
                          var changed = false;
                          if(level == "ERROR"){
                              row.customClasses += " redBackground";
                              changed = true;
                          } else if(level == "WARN"){
                              row.customClasses += " yellowBackground";
                              changed = true;
                          } else if(level == "DEBUG"){
                              row.customClasses += " grayBackground";
                              changed = true;
                          }
                          if (changed)
                          {
                            obj.grid.focus.styleRow(row);
                          }
                       }
                     };
                     obj.grid.on("styleRow", onStyleRow);
                     obj.grid.startup();
                   }
                 });
               }
               catch(err)
               {
                 console.error(err);
               }
           };

           LogViewer.prototype.close = function() {
             if (this.updater)
             {
                 this.updater.destroy();
                 this.updater = null;
             }
             if (this.downloadLogDialog)
             {
                 this.downloadLogDialog.destroy();
                 this.downloadLogDialog = null;
             }
             if (this.downloadLogsButton)
             {
                 this.downloadLogsButton.destroy();
                 this.downloadLogsButton = null;
             }
           };

           return LogViewer;
       });
