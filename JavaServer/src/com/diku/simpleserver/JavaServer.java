package com.diku.simpleserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
		http.start();
	}

}

class ProxyHandler implements HttpHandler {
	private static int nextPeer = 0;

	@Override
	public void handle(HttpExchange xchg) throws IOException {
		Tracker tracker = null;
		URLConnection xml = (new URL("http://datanet2011tracker.appspot.com/peers.xml")).openConnection();
		xml.connect();
		xml.getHeaderFields();
        try {
    		JAXBContext jaxbContext = JAXBContext.newInstance(Tracker.class);

    		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

            InputStream is = xml.getInputStream();

            tracker = (Tracker) unmarshaller.unmarshal(is);
		} catch (JAXBException e) {
			e.printStackTrace();
			return;
		}
		System.out.println(xchg.getProtocol());
		if (!xchg.getProtocol().toUpperCase().startsWith("HTTP/1.")) {
			xchg.sendResponseHeaders(403, -1);
			return;
		}
		String method = xchg.getRequestMethod();
		if (!method.equalsIgnoreCase("GET") &&
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
				allow = true;
				break;
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

		Proxy proxy = Proxy.NO_PROXY;
		boolean done = false;
		do {
		try {
		if (maxForwards >= 0) {
			nextPeer = (nextPeer + 1) % tracker.getPeers().size();
			Peer peer = tracker.getPeers().get(nextPeer);
			proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(peer.getIp(), peer.getPort()));
		}

		System.out.println("\nURL: " + uri.toURL() + "\n");
		URLConnection conn = uri.toURL().openConnection(proxy);
		System.out.println("\nRequest Headers:\n");
	    for (Entry<String, List<String>> entry : reqHeaders.entrySet()) {
	    	if (entry.getKey() == null)
	    		continue;
	    	if (entry.getKey().contentEquals("Proxy-connection"))
	    		continue;
	    	if (entry.getKey().contentEquals("Max-Forwards"))
	    		continue;
	    	if (entry.getKey().contentEquals("Via"))
	    		continue;
	    	for (String value : entry.getValue()) {
				conn.addRequestProperty(entry.getKey(), value);
	    		System.out.println(entry.getKey() + ": " + value);
	    	}
	    }
	    if (maxForwards >= 0)
	    	conn.addRequestProperty("Max-Forwards", String.valueOf(maxForwards));
		conn.connect();
	    Map<String, List<String>> connHeaders = conn.getHeaderFields();
	    Headers resHeaders = xchg.getResponseHeaders();
		System.out.println("\nResponse Headers:\n");
		int retCode = 200;
	    for (Entry<String, List<String>> entry : connHeaders.entrySet()) {
	    	if (entry.getKey() == null) {
		    	for (String value : entry.getValue()) {
		    		if (value.startsWith("HTTP/1."))
		    			retCode = Integer.valueOf(value.substring(9, 12));
		    		System.out.println(entry.getKey() + ": " + value);
		    	}
	    		continue;
	    	}
	    	for (String value : entry.getValue()) {
	    	    resHeaders.add(entry.getKey(), value);
	    		System.out.println(entry.getKey() + ": " + value);
	    	}
	    }
	    int len = conn.getContentLength();
	    if (len == -1)
	    	len = 0;
	    xchg.sendResponseHeaders(retCode, len);
	    byte[] response = new byte[1024];
	    InputStream is = conn.getInputStream();
	    OutputStream os = xchg.getResponseBody();
	    while ((len = is.read(response)) != -1)
	    	os.write(response, 0, len);
	    is.close();
	    os.close();
	    done = true;
	    } catch (IOException e) {
	    }
	    } while (!done && maxForwards >= 0);
	}

}
