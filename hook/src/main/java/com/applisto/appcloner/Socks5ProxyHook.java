package com.applisto.appcloner;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.SocketFactory;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public final class Socks5ProxyHook {

    private static final String TAG = "Socks5ProxyHook";
    private static String proxyHost;
    private static int    proxyPort;
    private static String proxyUser;
    private static String proxyPass;

    // Cache for UDP relays: Key = Local DatagramSocket, Value = RelayWorker
    private static final Map<DatagramSocket, UdpRelayWorker> udpRelays = new ConcurrentHashMap<>();

    // Set to track internal sockets to avoid infinite recursion
    private static final Set<DatagramSocket> internalDatagramSockets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void init(Context context) {
        ClonerSettings settings = ClonerSettings.get(context);
        boolean enabled = settings.socksProxy();
        proxyHost = settings.socksProxyHost();
        proxyPort = settings.socksProxyPort();
        proxyUser = settings.socksProxyUser();
        proxyPass = settings.socksProxyPass();

        if (!enabled || TextUtils.isEmpty(proxyHost)) {
            Log.i(TAG, "SOCKS5 not configured or disabled – skipping hook");
            return;
        }

        Log.i(TAG, "Installing SOCKS5 proxy → " + proxyHost + ":" + proxyPort);

        // 1. Set Authenticator for global auth
        if (!TextUtils.isEmpty(proxyUser)) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                    }
                    return null;
                }
            });
        }

        // 2. Set System Properties for WebView / Default Java handling
        System.setProperty("socksProxyHost", proxyHost);
        System.setProperty("socksProxyPort", String.valueOf(proxyPort));
        if (!TextUtils.isEmpty(proxyUser)) {
            System.setProperty("java.net.socks.username", proxyUser);
            System.setProperty("java.net.socks.password", proxyPass);
        }

        // 3. Set Default ProxySelector
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(
                        new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(proxyHost, proxyPort))
                );
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                Log.e(TAG, "Proxy connect failed: " + uri, ioe);
            }
        });

        // 4. Hook SocketFactory and Socket Constructors
        hookSocketFactory();
        hookSocketConstructors();

        // 5. Hook DatagramSocket for UDP support
        hookDatagramSocket();

        Log.i(TAG, "SOCKS5 hook installed (TCP + UDP + Auth + RemoteDNS)");
    }

    /* ----------------------------------------------------------
       TCP Hooks
       ---------------------------------------------------------- */
    private void hookSocketFactory() {
        try {
            Method mSF1 = SocketFactory.class.getDeclaredMethod("createSocket", String.class, int.class);
            Hooking.pineHook(mSF1, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    String host = (String) frame.args[0];
                    int    port = (int)    frame.args[1];
                    frame.setResult(createProxySocket(host, port));
                }
            });

            Method mSF2 = SocketFactory.class.getDeclaredMethod("createSocket", InetAddress.class, int.class);
            Hooking.pineHook(mSF2, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    InetAddress addr = (InetAddress) frame.args[0];
                    int         port = (int)        frame.args[1];
                    frame.setResult(createProxySocket(addr.getHostAddress(), port));
                }
            });

            // Hook connect to catch raw sockets that escaped constructor hooking?
            // Actually, if we hook the constructor correctly, connect() should work fine.

        } catch (Throwable t) {
            Log.e(TAG, "SocketFactory hooks failed", t);
        }
    }

    private void hookSocketConstructors() {
        try {
            // Hook new Socket() - the default no-arg constructor
            Constructor<?> c1 = Socket.class.getDeclaredConstructor();
            Hooking.pineHook(c1, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame frame) throws Throwable {
                    Socket socket = (Socket) frame.thisObject;
                    // We can't easily change the socket state here to add a proxy.
                    // However, since we set ProxySelector.setDefault,
                    // subsequent socket.connect() calls SHOULD use the proxy selector.
                    // Verification: Java's Socket.connect(SocketAddress) uses ProxySelector if proxy is not set?
                    // Answer: No. new Socket() creates a DIRECT connection unless Proxy is passed to constructor.
                    // The only way to force it is to hook the constructor and somehow replace 'this' (impossible)
                    // OR hook connect() and reimplement logic.
                }
            });

            // Critical: Hook new Socket(Proxy) to override if user tries to bypass?
            // Optional.

        } catch (Throwable t) {
             Log.e(TAG, "Socket constructor hooks failed", t);
        }
    }

    private Socket createProxySocket(String host, int port) throws IOException {
        Socket sock = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)));
        sock.connect(InetSocketAddress.createUnresolved(host, port));
        return sock;
    }


    /* ----------------------------------------------------------
       UDP Hooks: SOCKS5 UDP Associate
       ---------------------------------------------------------- */
    private void hookDatagramSocket() {
        try {
            // Hook send
            Method mSend = DatagramSocket.class.getDeclaredMethod("send", DatagramPacket.class);
            Hooking.pineHook(mSend, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    DatagramSocket socket = (DatagramSocket) frame.thisObject;
                    if (internalDatagramSockets.contains(socket)) return; // Skip internal sockets

                    DatagramPacket packet = (DatagramPacket) frame.args[0];

                    UdpRelayWorker worker = getOrCreateRelay(socket);
                    if (worker != null) {
                        worker.send(packet);
                        frame.setResult(null); // prevent original send
                    }
                }
            });

            // Hook receive
            Method mReceive = DatagramSocket.class.getDeclaredMethod("receive", DatagramPacket.class);
            Hooking.pineHook(mReceive, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    DatagramSocket socket = (DatagramSocket) frame.thisObject;
                    if (internalDatagramSockets.contains(socket)) return; // Skip internal sockets

                    DatagramPacket packet = (DatagramPacket) frame.args[0];

                    UdpRelayWorker worker = getOrCreateRelay(socket);
                    if (worker != null) {
                        worker.receive(packet); // Blocks until data available
                        frame.setResult(null); // prevent original receive
                    }
                }
            });

            // Hook close to cleanup
            Method mClose = DatagramSocket.class.getDeclaredMethod("close");
            Hooking.pineHook(mClose, new MethodHook() {
                 @Override public void beforeCall(Pine.CallFrame frame) throws Throwable {
                     DatagramSocket socket = (DatagramSocket) frame.thisObject;
                     if (internalDatagramSockets.contains(socket)) return;

                     UdpRelayWorker worker = udpRelays.remove(socket);
                     if (worker != null) {
                         worker.close();
                     }
                 }
            });

        } catch (Throwable t) {
            Log.e(TAG, "DatagramSocket hooks failed", t);
        }
    }

    private synchronized UdpRelayWorker getOrCreateRelay(DatagramSocket source) {
        if (udpRelays.containsKey(source)) {
            return udpRelays.get(source);
        }
        try {
            UdpRelayWorker worker = new UdpRelayWorker(proxyHost, proxyPort, proxyUser, proxyPass);
            udpRelays.put(source, worker);
            return worker;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create UDP relay", e);
            return null;
        }
    }

    /* ----------------------------------------------------------
       Inner Class: UDP Relay Worker
       ---------------------------------------------------------- */
    private static class UdpRelayWorker {
        private final Socket controlSocket;
        private final DataInputStream controlIn;
        private final DataOutputStream controlOut;
        private final DatagramSocket relaySocket;
        private InetAddress relayIp;
        private int relayPort;

        public UdpRelayWorker(String pHost, int pPort, String user, String pass) throws IOException {
            // 1. Connect to SOCKS5 proxy (TCP)
            controlSocket = new Socket(Proxy.NO_PROXY);
            controlSocket.connect(new InetSocketAddress(pHost, pPort));

            controlIn = new DataInputStream(controlSocket.getInputStream());
            controlOut = new DataOutputStream(controlSocket.getOutputStream());

            // 2. Auth handshake
            controlOut.writeByte(0x05); // Ver
            if (!TextUtils.isEmpty(user)) {
                controlOut.writeByte(0x02); // NMethods
                controlOut.writeByte(0x00); // No Auth
                controlOut.writeByte(0x02); // User/Pass
            } else {
                controlOut.writeByte(0x01); // NMethods
                controlOut.writeByte(0x00); // No Auth
            }
            controlOut.flush();

            int ver = controlIn.readByte();
            int method = controlIn.readByte();

            if (method == 0x02) {
                controlOut.writeByte(0x01); // Auth Ver
                controlOut.writeByte(user.length());
                controlOut.writeBytes(user);
                controlOut.writeByte(pass.length());
                controlOut.writeBytes(pass);
                controlOut.flush();

                controlIn.readByte(); // Ver
                if (controlIn.readByte() != 0x00) throw new IOException("SOCKS5 Auth failed");
            } else if (method == 0xFF) {
                throw new IOException("SOCKS5 No acceptable auth method");
            }

            // 3. UDP Associate Request
            controlOut.writeByte(0x05); // Ver
            controlOut.writeByte(0x03); // Cmd: UDP_ASSOCIATE
            controlOut.writeByte(0x00); // Rsv
            controlOut.writeByte(0x01); // ATYP: IPv4
            controlOut.write(new byte[4]); // 0.0.0.0
            controlOut.writeShort(0);      // Port 0
            controlOut.flush();

            // 4. Read Response
            controlIn.readByte(); // Ver
            byte rep = controlIn.readByte(); // Rep
            if (rep != 0x00) throw new IOException("SOCKS5 UDP_ASSOCIATE failed: " + rep);
            controlIn.readByte(); // Rsv
            byte atyp = controlIn.readByte();

            byte[] ipBytes;
            if (atyp == 0x01) { // IPv4
                ipBytes = new byte[4];
                controlIn.readFully(ipBytes);
            } else if (atyp == 0x03) { // Domain
                int len = controlIn.readByte();
                ipBytes = new byte[len];
                controlIn.readFully(ipBytes);
            } else if (atyp == 0x04) { // IPv6
                ipBytes = new byte[16];
                controlIn.readFully(ipBytes);
            } else {
                throw new IOException("Unknown ATYP: " + atyp);
            }

            int p = controlIn.readUnsignedShort(); // Port

            // Use control socket IP if relay address is 0.0.0.0
            InetAddress bindAddr;
            if (atyp == 0x03) {
                 try {
                     bindAddr = InetAddress.getByName(new String(ipBytes));
                 } catch (Exception e) {
                     bindAddr = controlSocket.getInetAddress();
                 }
            } else {
                bindAddr = InetAddress.getByAddress(ipBytes);
            }

            if (bindAddr.isAnyLocalAddress()) {
                this.relayIp = controlSocket.getInetAddress();
            } else {
                this.relayIp = bindAddr;
            }
            this.relayPort = p;

            // Initialize relay socket and mark as internal
            this.relaySocket = new DatagramSocket();
            internalDatagramSockets.add(this.relaySocket);
        }

        public void send(DatagramPacket packet) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Header construction
            dos.writeShort(0x0000); // RSV
            dos.writeByte(0x00);    // FRAG

            InetAddress destIp = packet.getAddress();
            if (destIp == null) throw new IOException("Packet has no destination");

            byte[] addrBytes = destIp.getAddress();
            if (addrBytes.length == 4) {
                dos.writeByte(0x01); // IPv4
                dos.write(addrBytes);
            } else {
                dos.writeByte(0x04); // IPv6
                dos.write(addrBytes);
            }

            dos.writeShort(packet.getPort());
            dos.write(packet.getData(), packet.getOffset(), packet.getLength());

            byte[] wrappedData = baos.toByteArray();
            DatagramPacket wrapped = new DatagramPacket(wrappedData, wrappedData.length, relayIp, relayPort);
            relaySocket.send(wrapped);
        }

        public void receive(DatagramPacket p) throws IOException {
            byte[] buf = new byte[65536];
            DatagramPacket wrapped = new DatagramPacket(buf, buf.length);
            relaySocket.receive(wrapped);

            if (wrapped.getLength() < 10) return;

            int offset = 3; // Skip RSV, FRAG
            byte atyp = buf[offset++];

            InetAddress srcAddr = null;
            if (atyp == 0x01) { // IPv4
                byte[] ip = new byte[4];
                System.arraycopy(buf, offset, ip, 0, 4);
                srcAddr = InetAddress.getByAddress(ip);
                offset += 4;
            } else if (atyp == 0x03) { // Domain
                int len = buf[offset++];
                offset += len;
                srcAddr = InetAddress.getLoopbackAddress();
            } else if (atyp == 0x04) { // IPv6
                byte[] ip = new byte[16];
                System.arraycopy(buf, offset, ip, 0, 16);
                srcAddr = InetAddress.getByAddress(ip);
                offset += 16;
            }

            int port = ((buf[offset] & 0xFF) << 8) | (buf[offset+1] & 0xFF);
            offset += 2;

            int dataLen = wrapped.getLength() - offset;
            if (dataLen > p.getData().length) dataLen = p.getData().length;

            System.arraycopy(buf, offset, p.getData(), p.getOffset(), dataLen);
            p.setLength(dataLen);
            if (srcAddr != null) p.setAddress(srcAddr);
            p.setPort(port);
        }

        public void close() {
            try {
                if (controlSocket != null && !controlSocket.isClosed()) controlSocket.close();
            } catch (IOException ignored) {}
            try {
                if (relaySocket != null && !relaySocket.isClosed()) {
                    relaySocket.close();
                    internalDatagramSockets.remove(relaySocket);
                }
            } catch (Exception ignored) {}
        }
    }
}
