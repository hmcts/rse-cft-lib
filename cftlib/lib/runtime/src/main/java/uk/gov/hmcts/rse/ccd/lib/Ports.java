package uk.gov.hmcts.rse.ccd.lib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Port checks used to adopt dependencies that are already running.
 */
public final class Ports {

    private Ports() {
    }

    public static boolean isListening(String host, int port) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isListening(int port) {
        return isListening("localhost", port);
    }
}
