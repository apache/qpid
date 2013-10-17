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
define(["dojo/_base/xhr", "dojox/encoding/base64", "dojox/encoding/digests/_base", "dojox/encoding/digests/MD5"],
    function (xhr, base64, digestsBase, MD5) {

var encodeUTF8 = function encodeUTF8(str) {
    var byteArray = [];
    for (var i = 0; i < str.length; i++) {
        if (str.charCodeAt(i) <= 0x7F) {
            byteArray.push(str.charCodeAt(i));
        }
        else {
            var h = encodeURIComponent(str.charAt(i)).substr(1).split('%');
            for (var j = 0; j < h.length; j++)
                byteArray.push(parseInt(h[j], 16));
        }
    }
    return byteArray;
};

var decodeUTF8 = function decodeUTF8(byteArray)
{
    var str = '';
    for (var i = 0; i < byteArray.length; i++)
        str +=  byteArray[i] <= 0x7F?
                byteArray[i] === 0x25 ? "%25" :
                String.fromCharCode(byteArray[i]) :
                "%" + byteArray[i].toString(16).toUpperCase();
    return decodeURIComponent(str);
};

var errorHandler = function errorHandler(error)
{
    if(error.status == 401)
    {
        alert("Authentication Failed");
    }
    else if(error.status == 403)
    {
        alert("Authorization Failed");
    }
    else
    {
        alert(error);
    }
}

var saslPlain = function saslPlain(user, password, callbackFunction)
{
    var responseArray = [ 0 ].concat(encodeUTF8( user )).concat( [ 0 ] ).concat( encodeUTF8( password ) );
    var plainResponse = base64.encode(responseArray);

    // Using dojo.xhrGet, as very little information is being sent
    dojo.xhrPost({
        // The URL of the request
        url: "rest/sasl",
        content: {
            mechanism: "PLAIN",
            response: plainResponse
        },
        handleAs: "json",
        failOk: true
    }).then(callbackFunction, errorHandler);
};

var saslCramMD5 = function saslCramMD5(user, password, saslMechanism, callbackFunction)
{

    // Using dojo.xhrGet, as very little information is being sent
    dojo.xhrPost({
        // The URL of the request
        url: "rest/sasl",
        content: {
            mechanism: saslMechanism
        },
        handleAs: "json",
        failOk: true
    }).then(function(data)
            {

                var challengeBytes = base64.decode(data.challenge);
                var wa=[];
                var bitLength = challengeBytes.length*8;
                for(var i=0; i<bitLength; i+=8)
                {
                    wa[i>>5] |= (challengeBytes[i/8] & 0xFF)<<(i%32);
                }
                var challengeStr = digestsBase.wordToString(wa).substring(0,challengeBytes.length);

                var digest =  user + " " + MD5._hmac(challengeStr, password, digestsBase.outputTypes.Hex);
                var id = data.id;

                var response = base64.encode(encodeUTF8( digest ));

                dojo.xhrPost({
                        // The URL of the request
                        url: "rest/sasl",
                        content: {
                            id: id,
                            response: response
                        },
                        handleAs: "json",
                        failOk: true
                    }).then(callbackFunction, errorHandler);

            },
            function(error)
            {
                if(error.status == 403)
                {
                    alert("Authentication Failed");
                }
                else
                {
                    alert(error);
                }
            });
};

var containsMechanism = function containsMechanism(mechanisms, mech)
{
    for (var i = 0; i < mechanisms.length; i++) {
        if (mechanisms[i] == mech) {
            return true;
        }
    }

    return false;
};

var SaslClient = {};

SaslClient.authenticate = function(username, password, callbackFunction)
{
    dojo.xhrGet({
        url: "rest/sasl",
        handleAs: "json",
        failOk: true
    }).then(function(data)
            {
               var mechMap = data.mechanisms;
               if (containsMechanism(mechMap, "CRAM-MD5"))
               {
                   saslCramMD5(username, password, "CRAM-MD5", callbackFunction);
               }
               else if (containsMechanism(mechMap, "CRAM-MD5-HEX"))
               {
                   var hashedPassword = MD5(password, digestsBase.outputTypes.Hex);
                   saslCramMD5(username, hashedPassword, "CRAM-MD5-HEX", callbackFunction);
               }
               else if (containsMechanism(mechMap, "PLAIN"))
               {
                   saslPlain(username, password, callbackFunction);
               }
               else
               {
                   alert("No supported SASL mechanism offered: " + mechMap);
               }
            }, errorHandler);
};

SaslClient.getUser = function(callbackFunction)
{
    dojo.xhrGet({
        url: "rest/sasl",
        handleAs: "json",
        failOk: true
    }).then(callbackFunction, errorHandler);
};

return SaslClient;
});
