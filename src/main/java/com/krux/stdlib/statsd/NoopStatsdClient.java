/**
 * 
 */
package com.krux.stdlib.statsd;

import java.net.InetAddress;
import java.net.SocketException;

import org.ubercraft.statsd.StatsdClient;
import org.ubercraft.statsd.StatsdStatType;

/**
 * @author cass
 * 
 */
public class NoopStatsdClient extends StatsdClient {

    public NoopStatsdClient(InetAddress host, int port) throws SocketException {
        super(host, port);
        // TODO Auto-generated constructor stub
    }

    @Override
    public String toString() {
        return NoopStatsdClient.class.getName();
    }

    public long getQueueOfferTimeout() {
        return 0;
    }

    public void setQueueOfferTimeout(long queueOfferTimeout) {
        return;
    }

    public void shutdown() {
        return;
    }

    public boolean count(String key) {
        return false;
    }

    public boolean count(String key, int count) {
        return false;
    }

    public boolean count(String key, double sampleRate) {
        return false;
    }

    public boolean count(String key, int count, double sampleRate) {
        return false;
    }

    public boolean time(String key, long millis) {
        return false;
    }

    public boolean time(String key, long millis, double sampleRate) {
        return false;
    }

    public boolean gauge(String key, int value) {
        return false;
    }

    public boolean stat(StatsdStatType type, String key, long value, double sampleRate) {
        return false;
    }

}
