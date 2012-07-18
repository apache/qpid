jsonObject = {
  "_countries":
    QPID.iterations( { "__ITERATING_VALUE": [ 0, 1 ] },
      {
          // this is a comment - it wouldn't be allowed if this were pure JSON

          "_name": "Country",
          "_regions": QPID.times(2,
            {
              "_name": "repeatingRegion__REGION_INDEX",
              "_towns": [
                {
                  "_name": "town1",
                  "_iteratingAttribute": "__ITERATING_VALUE",
                  "_consumers": []
                }
              ]
            },
            "__REGION_INDEX"
        )
    })

}