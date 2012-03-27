The bdbstore v5 data were obtained by upgrading the bdbstore v4 data as part of running
test UpgradeFrom4to5Test#testPerformUpgradeWithHandlerAnsweringNo.

The rationale for not using BDBStoreUpgradeTestPreparer in this case is that we need chunked content.
Current implementation of BDBMessageStore only stores messages in one chunk.