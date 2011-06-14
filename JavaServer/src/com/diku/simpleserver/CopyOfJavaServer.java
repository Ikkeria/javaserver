package com.diku.simpleserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class CopyOfJavaServer {

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

class CopyProxyHandler implements HttpHandler {

	@Override
	public void handle(HttpExchange xchg) throws IOException {
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
		System.out.println("\nURL: " + uri.toURL() + "\n");
		URLConnection conn = uri.toURL().openConnection();
		System.out.println("\nRequest Headers:\n");
	    for (Entry<String, List<String>> entry : reqHeaders.entrySet()) {
	    	if (entry.getKey() == null)
	    		continue;
	    	if (entry.getKey().contentEquals("Proxy-connection"))
	    		continue;
	    	for (String value : entry.getValue()) {
				conn.addRequestProperty(entry.getKey(), value);
	    		System.out.println(entry.getKey() + ": " + value);
	    	}
	    }
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
	}

}
