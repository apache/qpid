jsonObject = {
  "_tests":
    QPID.iterations( { "__ACK_MODE": [ 0, 1 ] },
      {
          // this is a comment - it wouldn't be allowed if this were pure JSON

          "_name": "Test 1",
          "_queues": [
            {
              "_name": "Json-Queue-Name"
            }
          ],

          "_clients": QPID.times(2,
            {
              "_name": "repeatingClient__CLIENT_INDEX",
              "_connections": [
                {
                  "_name": "connection1",
                  "_sessions": [
                    {
                      "_sessionName": "session1",
                      "_acknowledgeMode": "__ACK_MODE",
                      "_consumers": []
                    }
                  ]
                }
              ]
            },
            "__CLIENT_INDEX"
        )
    })

}