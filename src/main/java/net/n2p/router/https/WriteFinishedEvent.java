package net.n2p.router.https;

public class WriteFinishedEvent extends Event{
    WriteFinishedEvent(HttpsExchangeImpl exchangeImpl) {
        super(exchangeImpl);
        assert !exchangeImpl._writeFinished;
        exchangeImpl._writeFinished = true;
    }
}
