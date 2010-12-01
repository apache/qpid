'
'
' Licensed to the Apache Software Foundation (ASF) under one
' or more contributor license agreements.  See the NOTICE file
' distributed with this work for additional information
' regarding copyright ownership.  The ASF licenses this file
' to you under the Apache License, Version 2.0 (the
' "License"); you may not use this file except in compliance
' with the License.  You may obtain a copy of the License at
' 
'   http://www.apache.org/licenses/LICENSE-2.0
' 
' Unless required by applicable law or agreed to in writing,
' software distributed under the License is distributed on an
' "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
' KIND, either express or implied.  See the License for the
' specific language governing permissions and limitations
' under the License.
'
'

Imports System
Imports Org.Apache.Qpid.Messaging
Namespace Org.Apache.Qpid.Messaging.Examples
    Module Module1
        Class Client
            Public Shared Function Main(ByVal args() As String) As Integer
                Dim url As String = "amqp:tcp:127.0.0.1:5672"
                Dim connectionOptions As String = ""

                If args.Length > 0 Then url = args(0)
                If args.Length > 1 Then connectionOptions = args(1)

                Dim connection As Connection
                Try
                    connection = New Connection(url, connectionOptions)
                    connection.Open()

                    Dim session As Session = connection.CreateSession()

                    Dim sender As Sender = session.CreateSender("service_queue")

                    Dim responseQueue As Address = New Address("#response-queue; {create:always, delete:always}")
                    Dim receiver As Receiver = session.CreateReceiver(responseQueue)

                    Dim s(3) As String
                    s(0) = "Twas brillig, and the slithy toves"
                    s(1) = "Did gire and gymble in the wabe."
                    s(2) = "All mimsy were the borogroves,"
                    s(3) = "And the mome raths outgrabe."

                    Dim request As Message = New Message("")
                    request.ReplyTo = responseQueue

                    Dim i As Integer
                    For i = 0 To s.Length - 1
                        request.SetContent(s(i))
                        sender.Send(request)
                        Dim response As Message = receiver.Fetch()
                        Console.WriteLine("{0} -> {1}", request.GetContent(), response.GetContent())
                    Next i
                    connection.Close()
                    Main = 0
                Catch e As Exception
                    Console.WriteLine("Exception {0}.", e)
                    connection.Close()
                    Main = 1
                End Try
            End Function
        End Class
    End Module
End Namespace
