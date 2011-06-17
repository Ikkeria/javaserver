package com.diku.simpleserver;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class JavaServer {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
		HttpServer http = HttpServer.create(new InetSocketAddress(8000), 0);
		http.createContext("/", new ProxyHandler());
		http.setExecutor(new Executor() {
			public void execute(Runnable r) {
				new Thread(r).start();
			}
		});
		long period = ProxyHandler.updateTracker();
		if (period < 0) {
			System.out.println("No tracker!");
			return;
		}
		period /= 2;
		(new Timer()).scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					ProxyHandler.updateTracker();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}, period, period);
		http.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				HttpURLConnection xml;
				try {
					xml = (HttpURLConnection)(new URL("http://datanet2011tracker.appspot.com/peers.xml")).openConnection();
					xml.setRequestMethod("POST");
					xml.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
					xml.setUseCaches (false);
					xml.setDoOutput(true);
					PrintWriter pw = new PrintWriter(xml.getOutputStream());
					pw.print("port=8000&action=unregister");
					pw.flush();
					pw.close();
					xml.disconnect();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

}

class ProxyHandler implements HttpHandler {
	private static int nextPeer = 0;
	private static Tracker tracker = null;
	private static long minWait = 0;

	private synchronized Peer getPeer() throws IOException {
    	if (tracker.getPeers().size() < 4 && minWait < System.currentTimeMillis())
    		if (updateTracker() < 0)
    			return null;
		if (tracker.getPeers().size() == 0)
			return null;
		nextPeer = (nextPeer + 1) % tracker.getPeers().size();
		return tracker.getPeers().get(nextPeer);
	}

	private synchronized void delPeer(Peer peer) {
    	tracker.getPeers().remove(peer);
	}

	public static synchronized long updateTracker() throws IOException {
		HttpURLConnection xml = (HttpURLConnection)(new URL("http://datanet2011tracker.appspot.com/peers.xml")).openConnection();
		xml.setRequestMethod("POST");
		xml.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		xml.setUseCaches (false);
		xml.setDoOutput(true);
		PrintWriter pw = new PrintWriter(xml.getOutputStream());
		pw.print("port=8000&action=register");
		pw.flush();
		pw.close();
		xml.getHeaderFields();
        try {
    		JAXBContext jaxbContext = JAXBContext.newInstance(Tracker.class);

    		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            InputStream is = xml.getInputStream();

            tracker = (Tracker) unmarshaller.unmarshal(is);
		} catch (JAXBException e) {
			e.printStackTrace();
			tracker = null;
			return -1;
		}
		long retVal = -2;
		for (Option o : tracker.getOptions()) {
			if (o.getKey().contentEquals("expire")) {
				retVal = o.getOption() * 1000;
			}
			if (o.getKey().contentEquals("min_wait")) {
				minWait = System.currentTimeMillis() + (o.getOption() * 1000);
			}
		}
		return retVal;
	}

	@Override
	public void handle(HttpExchange xchg) throws IOException {
		if (tracker == null)
			return;
		System.out.println(xchg.getProtocol());
		if (!xchg.getProtocol().toUpperCase().startsWith("HTTP/1.")) {
			xchg.sendResponseHeaders(403, -1);
			return;
		}
		String method = xchg.getRequestMethod();
		if (!method.equalsIgnoreCase("POST") &&
				!method.equalsIgnoreCase("GET") &&
				!method.equalsIgnoreCase("HEAD")) {
			xchg.sendResponseHeaders(501, -1);
			return;
		}
		Headers reqHeaders = xchg.getRequestHeaders();
		String host = reqHeaders.getFirst("Host");
		if (host == null) {
			xchg.sendResponseHeaders(400, -1);
			return;
		}
		URI uri = xchg.getRequestURI();
		String rHost = uri.getHost();
		if (rHost == null) {
			xchg.sendResponseHeaders(400, -1);
			return;
		}
		rHost += ".";
		boolean allow = false;
		for (String wHost : tracker.getWhitelist()) {
			if (rHost.endsWith(wHost)) {
				if (rHost.length() == wHost.length() ||
						rHost.charAt(rHost.length() - wHost.length() - 1) == '.') {
					allow = true;
					break;
				}
			}
		}
		if (!allow) {
			xchg.sendResponseHeaders(403, -1);
			return;
		}

		int maxForwards = 4;
		List<String> mfList = reqHeaders.get("Max-Forwards");
		if (mfList != null)
			for (String mfString : mfList)
				maxForwards = Integer.valueOf(mfString);
		maxForwards--;
		maxForwards = -1;

		byte[] post = null;
		if (method.contentEquals("POST")) {
		    InputStream is = xchg.getRequestBody();
		    int len;
		    byte[] response = new byte[1024];
		    ByteArrayOutputStream ba = new ByteArrayOutputStream();
		    while ((len = is.read(response)) != -1)
		    	ba.write(response, 0, len);
		    post = ba.toByteArray();
		}

		Proxy proxy = Proxy.NO_PROXY;
		boolean done = false;
		Peer peer = null;
		do {
		try {
		if (maxForwards >= 0) {
			if ((peer = getPeer()) == null)
				return; // No peers
			System.out.println("Trying peer " + peer.getIp() + ":" + peer.getPort());
			proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(peer.getIp(), peer.getPort()));
		}

		System.out.println("\nURL: " + uri.toURL() + "\n");
		HttpURLConnection conn = (HttpURLConnection)uri.toURL().openConnection(proxy);
		conn.setRequestMethod(method);

		String via = new String(xchg.getProtocol() + " " + xchg.getLocalAddress().getHostName() + ":" + xchg.getLocalAddress().getPort());		System.out.println("\nRequest Headers:\n");
		boolean useVia = false;
	    for (Entry<String, List<String>> entry : reqHeaders.entrySet()) {
	    	if (entry.getKey() == null)
	    		continue;
	    	if (entry.getKey().contentEquals("Proxy-connection"))
	    		continue;
	    	if (entry.getKey().contentEquals("Max-Forwards"))
	    		continue;
	    	if (entry.getKey().contentEquals("Via") && proxy == Proxy.NO_PROXY) {
	    		useVia = true;
	    		continue;
	    	}
	    	for (String value : entry.getValue()) {
				conn.addRequestProperty(entry.getKey(), value);
	    		System.out.println(entry.getKey() + ": " + value);
	    	}
	    	if (entry.getKey().contentEquals("Via")) {
	    		System.out.println("Local via: " + via);
	    		conn.addRequestProperty(entry.getKey(), via);
	    		useVia = true;
	    	}
	    }
	    if (maxForwards >= 0)
	    	conn.addRequestProperty("Max-Forwards", String.valueOf(maxForwards));

	    if (post != null) {
			conn.setDoOutput(true);
		    OutputStream cos = conn.getOutputStream();
		    cos.write(post);
		    cos.close();
	    }
	    conn.connect();
	    int len;
	    byte[] response = new byte[1024];
	    InputStream is;
	    OutputStream os;

		ByteArrayOutputStream body = new ByteArrayOutputStream();
		boolean encrypt = false;
	    PrintWriter pw = null;
	    if (encrypt)
	    	pw = new PrintWriter(body);
	    Map<String, List<String>> connHeaders = conn.getHeaderFields();
	    Headers resHeaders = xchg.getResponseHeaders();
		System.out.println("\nResponse Headers:\n");
		if (encrypt)
			pw.print(connHeaders.get(null).get(0) + "\r\n");
	    for (Entry<String, List<String>> entry : connHeaders.entrySet()) {
	    	if (entry.getKey() == null)
	    		continue;
	    	if (entry.getKey().contentEquals("Transfer-Encoding"))
	    		continue;
	    	for (String value : entry.getValue()) {
	    	    System.out.println(entry.getKey() + ": " + value);
	    	    if (encrypt)
		    		pw.print(entry.getKey() + ": " + value + "\r\n");
	    	    else
	    	    	resHeaders.add(entry.getKey(), value);
	    	}
	    }
	    if (useVia)
	    	if (encrypt)
	    		pw.print("Via: " + via + "\r\n");
	    	else
	    		resHeaders.add("Via", via);
	    if (encrypt) {
	    	pw.print("\r\n");
	    	pw.flush();
	    }

	    is = conn.getInputStream();
	    while ((len = is.read(response)) != -1)
	    	body.write(response, 0, len);
	    body.flush();
	    is.close();
	    xchg.sendResponseHeaders(conn.getResponseCode(), body.size());
	    os = xchg.getResponseBody();
	    body.writeTo(os);
	    os.close();

	    done = true;
	    } catch (IOException e) {
	    	delPeer(peer);
	    }
	    } while (!done && maxForwards >= 0);
	}

}
