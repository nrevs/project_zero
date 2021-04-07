package net.n2p.router.https;

public class Event {
    HttpsExchangeImpl _exchangeImpl;

    protected Event (HttpsExchangeImpl exchangeImpl) {
        _exchangeImpl = exchangeImpl;
    }

}
