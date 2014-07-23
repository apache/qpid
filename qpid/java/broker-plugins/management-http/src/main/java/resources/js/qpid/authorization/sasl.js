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
define(["dojo/_base/xhr", "dojox/encoding/base64", "dojox/encoding/digests/_base", "dojox/encoding/digests/MD5", "dojox/uuid/generateRandomUuid", "dojo/request/script"],
    function (xhr, base64, digestsBase, MD5, uuid, script) {

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

var saslServiceUrl="service/sasl";

var saslPlain = function saslPlain(user, password, callbackFunction)
{
    var responseArray = [ 0 ].concat(encodeUTF8( user )).concat( [ 0 ] ).concat( encodeUTF8( password ) );
    var plainResponse = base64.encode(responseArray);

    // Using dojo.xhrGet, as very little information is being sent
    dojo.xhrPost({
        // The URL of the request
        url: saslServiceUrl,
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
            dojo.xhrPost({
                // The URL of the request
                url: saslServiceUrl,
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
                        url: saslServiceUrl,
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

        var saslScramSha1 = function saslScramSha1(user, password, saslMechanism, callbackFunction) {
            saslScram("sha1",user,password,saslMechanism,callbackFunction);
        };

        var saslScramSha256 = function saslScramSha1(user, password, saslMechanism, callbackFunction) {
            saslScram("sha256",user,password,saslMechanism,callbackFunction);
        };

        var saslScram = function saslScramSha1(mechanism, user, password, saslMechanism, callbackFunction) {

            var DIGEST = mechanism.toUpperCase();
            var HMAC = "Hmac"+DIGEST;

            script.get("js/crypto-js/hmac-"+mechanism+".js").then( function()
            {
                script.get("js/crypto-js/enc-base64-min.js").then ( function()
                {

                    var toBase64 = function toBase64( input )
                    {
                        var result = [];
                        for(var i = 0; i < input.length; i++)
                        {
                            result[i] = input.charCodeAt(i);
                        }
                        return base64.encode( result )
                    };

                    var fromBase64 = function fromBase64( input )
                    {
                        var decoded = base64.decode( input );
                        var result = "";
                        for(var i = 0; i < decoded.length; i++)
                        {
                            result+= String.fromCharCode(decoded[i]);
                        }
                        return result;
                    };

                    var xor = function xor(lhs, rhs) {
                        var words = [];
                        for(var i = 0; i < lhs.words.length; i++)
                        {
                            words.push(lhs.words[i]^rhs.words[i]);
                        }
                        return CryptoJS.lib.WordArray.create(words);
                    };

                    var hasNonAscii = function hasNonAscii(name) {
                        for(var i = 0; i < name.length; i++) {
                            if(name.charCodeAt(i) > 127) {
                                return true;
                            }
                        }
                        return false;
                    };

                    var generateSaltedPassword = function generateSaltedPassword(salt, password, iterationCount)
                    {
                        var hmac = CryptoJS.algo.HMAC.create(CryptoJS.algo[DIGEST], password);

                        hmac.update(salt);
                        hmac.update(CryptoJS.enc.Hex.parse("00000001"));

                        var result = hmac.finalize();
                        var previous = null;
                        for(var i = 1 ;i < iterationCount; i++)
                        {
                            hmac = CryptoJS.algo.HMAC.create(CryptoJS.algo[DIGEST], password);
                            hmac.update( previous != null ? previous : result );
                            previous = hmac.finalize();
                            result = xor(result, previous);
                        }
                        return result;

                    };

                    GS2_HEADER = "n,,";

                    if(!hasNonAscii(user)) {

                        user = user.replace(/=/g, "=3D");
                        user = user.replace(/,/g, "=2C");

                        clientNonce = uuid();
                        clientFirstMessageBare = "n=" + user + ",r=" + clientNonce;
                        dojo.xhrPost({
                            // The URL of the request
                            url: saslServiceUrl,
                            content: {
                                mechanism: saslMechanism,
                                response: toBase64(GS2_HEADER + clientFirstMessageBare)
                            },
                            handleAs: "json",
                            failOk: true
                        }).then(function (data) {
                            var serverFirstMessage = fromBase64(data.challenge);
                            var id = data.id;

                            var parts = serverFirstMessage.split(",");
                            nonce = parts[0].substring(2);
                            if (!nonce.substr(0, clientNonce.length) == clientNonce) {
                                alert("Authentication error - server nonce does not start with client nonce")
                            }
                            else {
                                var salt = CryptoJS.enc.Base64.parse(parts[1].substring(2));
                                var iterationCount = parts[2].substring(2);
                                var saltedPassword = generateSaltedPassword(salt, password, iterationCount)
                                var clientFinalMessageWithoutProof = "c=" + toBase64(GS2_HEADER) + ",r=" + nonce;
                                var authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalMessageWithoutProof;
                                var clientKey = CryptoJS[HMAC]("Client Key", saltedPassword);
                                var storedKey = CryptoJS[DIGEST](clientKey);
                                var clientSignature = CryptoJS[HMAC](authMessage, storedKey);
                                var clientProof = xor(clientKey, clientSignature);
                                var serverKey = CryptoJS[HMAC]("Server Key", saltedPassword);
                                serverSignature = CryptoJS[HMAC](authMessage, serverKey);
                                dojo.xhrPost({
                                    // The URL of the request
                                    url: saslServiceUrl,
                                    content: {
                                        id: id,
                                        response: toBase64(clientFinalMessageWithoutProof
                                            + ",p=" + clientProof.toString(CryptoJS.enc.Base64))
                                    },
                                    handleAs: "json",
                                    failOk: true
                                }).then(function (data) {
                                    var serverFinalMessage = fromBase64(data.challenge);
                                    if (serverSignature.toString(CryptoJS.enc.Base64) == serverFinalMessage.substring(2)) {
                                        callbackFunction();
                                    }
                                    else {
                                        errorHandler("Server signature did not match");
                                    }


                                }, errorHandler);
                            }

                        }, errorHandler);
                    }
                    else
                    {
                        alert("Username '"+name+"' is invalid");
                    }

                    }, errorHandler);
                }, errorHandler);
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
        url: saslServiceUrl,
        handleAs: "json",
        failOk: true
    }).then(function(data)
            {
               var mechMap = data.mechanisms;
               if(containsMechanism(mechMap, "SCRAM-SHA-256"))
               {
                   saslScramSha256(username, password, "SCRAM-SHA-256", callbackFunction)
               }
               else if(containsMechanism(mechMap, "SCRAM-SHA-1"))
               {
                   saslScramSha1(username, password, "SCRAM-SHA-1", callbackFunction)
               }
               else if (containsMechanism(mechMap, "CRAM-MD5"))
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
        url: saslServiceUrl,
        handleAs: "json",
        failOk: true
    }).then(callbackFunction, errorHandler);
};

return SaslClient;
});
