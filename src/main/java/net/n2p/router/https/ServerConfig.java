package net.n2p.router.https;



public class ServerConfig {
    private static final int DEFAULT_CLOCK_TICK = 1000;
    
    private static final long DEFAULT_IDLE_INTERVAL = 30 ; // 5 min
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 200 ;

    private static final long DEFAULT_MAX_REQ_TIME = -1; // default: forever
    private static final long DEFAULT_MAX_RSP_TIME = -1; // default: forever
    private static final long DEFAULT_TIMER_MILLIS = 1000;
    private static final int  DEFAULT_MAX_REQ_HEADERS = 200;
    private static final long DEFAULT_DRAIN_AMOUNT = 64 * 1024;

    private static int _clockTick = DEFAULT_CLOCK_TICK;
    private static long _idleInterval = DEFAULT_IDLE_INTERVAL;
    // The maximum number of bytes to drain from an inputstream
    private static long _drainAmount = DEFAULT_DRAIN_AMOUNT;
    private static int _maxIdleConnections = DEFAULT_MAX_IDLE_CONNECTIONS;
    // The maximum number of request headers allowable
    private static int _maxReqHeaders = DEFAULT_MAX_REQ_HEADERS;
    // max time a request or response is allowed to take
    private static long _maxReqTime = DEFAULT_MAX_REQ_TIME;
    private static long _maxRspTime = DEFAULT_MAX_RSP_TIME;
    private static long _timerMillis = DEFAULT_TIMER_MILLIS;
    
    private static boolean _noDelay;



    static long getIdleInterval() {
        return _idleInterval;
    }

    static int getClockTick() {
        return _clockTick;
    }

    static int getMaxIdleConnections() {
        return _maxIdleConnections;
    }

    static long getDrainAmount() {
        return _drainAmount;
    }

    static int getMaxReqHeaders() {
        return _maxReqHeaders;
    }

    static long getMaxReqTime() {
        return _maxReqTime;
    }

    static long getMaxRspTime() {
        return _maxRspTime;
    }

    static long getTimerMillis() {
        return _timerMillis;
    }

    static boolean noDelay() {
        return _noDelay;
    }
}
