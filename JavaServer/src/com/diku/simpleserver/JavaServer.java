package com.diku.simpleserver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import biz.source_code.base64Coder.Base64Coder;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class JavaServer {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws InvalidKeySpecException 
	 * @throws JAXBException 
	 */
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, JAXBException {
		HttpURLConnection xml = (HttpURLConnection)(new URL("http://datanet2011tracker.appspot.com/tracker_pub.xml")).openConnection();
		xml.setUseCaches (false);
		xml.getHeaderFields();

		JAXBContext jaxbContext = JAXBContext.newInstance(RSAPublicKey.class);

		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		InputStream is = xml.getInputStream();

		RSAPublicKey xmlKey = (RSAPublicKey) unmarshaller.unmarshal(is);
		RSAPublicKeySpec keySpec = new RSAPublicKeySpec(xmlKey.getN(), xmlKey.getE());

		KeyFactory fact = KeyFactory.getInstance("RSA");
		ProxyHandler.trackKey = fact.generatePublic(keySpec);

		KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(1024);
		KeyPair kp = kpg.genKeyPair();
		ProxyHandler.pubKey = kp.getPublic();
		ProxyHandler.privKey = kp.getPrivate();

		RSAPublicKeySpec pub = fact.getKeySpec(ProxyHandler.pubKey, RSAPublicKeySpec.class);
		ProxyHandler.pubN = pub.getModulus();
		ProxyHandler.pubE = pub.getPublicExponent();

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
		}, (ProxyHandler.minWait - System.currentTimeMillis()) * 2, period);
		http.start();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					HttpURLConnection xml = (HttpURLConnection)(new URL("http://datanet2011tracker.appspot.com/peers.xml")).openConnection();
					String reqBody = "port=8000&action=unregister&pub_key=" + ProxyHandler.pubN + "+" + ProxyHandler.pubE;
					xml.setRequestMethod("POST");
					xml.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
					xml.setRequestProperty("Content-Signature", ProxyHandler.sign(reqBody.getBytes()));
					xml.setUseCaches (false);
					xml.setDoOutput(true);
					OutputStream os = xml.getOutputStream();
					os.write(reqBody.getBytes());
					os.close();
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
	private static String nonce = "";
	public static long minWait = 0;
	public static PublicKey pubKey;
	public static BigInteger pubN;
	public static BigInteger pubE;
	public static PrivateKey privKey;
	public static PublicKey trackKey;

	public static synchronized String sign(byte[] body) {
		byte[] nonceBytes = nonce.getBytes();
		byte[] sig = new byte[body.length + nonceBytes.length];
		System.arraycopy(body, 0, sig, 0, body.length);
		System.arraycopy(nonceBytes, 0, sig, body.length, nonceBytes.length);
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(sig);
			byte[] bodyHash = md.digest();
			byte[] bodyCode = new byte[128];
			bodyCode[0] = 0x00;
			for (int i = 1; i < 96; i++)
				bodyCode[i] = (byte) 0xFF;
			for (int i = 0; i < 32; i++)
				bodyCode[i + 96] = bodyHash[i];
			Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, privKey);
			byte[] sigCode = cipher.doFinal(bodyCode);
			if (sigCode.length != 128)
				return null;
			return new String(Base64Coder.encode(sigCode));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
		return null;

	}

	private static boolean verify(String sig, byte[] body) {
		try {
			byte[] byteData = Base64Coder.decode(sig);
			Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, trackKey);
			byte[] sigCode = cipher.doFinal(byteData);
			if (sigCode.length != 128)
				return false;
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(body);
			byte[] bodyHash = md.digest();
			byte[] bodyCode = new byte[128];
			bodyCode[0] = 0x00;
			for (int i = 1; i < 96; i++)
				bodyCode[i] = (byte) 0xFF;
			for (int i = 0; i < 32; i++)
				bodyCode[i + 96] = bodyHash[i];
			for (int i = 0; i < 128; i++)
				if (sigCode[i] != bodyCode[i])
					return false;
			return true;
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
		} catch (NoSuchPaddingException e1) {
			e1.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
		return false;
	}

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
		String reqBody = "port=8000&action=register&pub_key=" + pubN + "+" + pubE;
		xml.setRequestMethod("POST");
		xml.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		xml.setRequestProperty("Content-Signature", sign(reqBody.getBytes()));
		System.out.println(xml.getRequestProperties());
		xml.setUseCaches (false);
		xml.setDoOutput(true);
		OutputStream os = xml.getOutputStream();
		os.write(reqBody.getBytes());
		os.close();

		List<String> sigList;
		String sigString = null;
		if ((sigList = xml.getHeaderFields().get("Content-Signature")) != null)
			sigString = sigList.get(0);
		System.out.println(xml.getHeaderFields());
		byte[] body = null;
        try {
    		JAXBContext jaxbContext = JAXBContext.newInstance(Tracker.class);

    		Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

		    InputStream is = xml.getInputStream();

		    int len;
		    byte[] response = new byte[1024];
		    ByteArrayOutputStream ba = new ByteArrayOutputStream();
		    while ((len = is.read(response)) != -1)
		    	ba.write(response, 0, len);
		    ba.flush();
		    body = ba.toByteArray();

            tracker = (Tracker) unmarshaller.unmarshal(new ByteArrayInputStream(body));

        } catch (JAXBException e) {
			e.printStackTrace();
			tracker = null;
			return -1;
		}
        System.out.println(new String(body));
        if (sigString != null)
        	if (!verify(sigString, body)) {
        		System.out.println("Tracker failed signature check");
        		tracker = null;
        		return -3;
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
		boolean encrypt = false;
		System.out.println(xchg.getProtocol());
		if (!xchg.getProtocol().toUpperCase().startsWith("HTTP/1.")) {
			xchg.sendResponseHeaders(403, -1);
			return;
		}
		String method = xchg.getRequestMethod();
		if (method.contentEquals("DATANET"))
			encrypt = true;
		else if (!method.equalsIgnoreCase("POST") &&
				!method.equalsIgnoreCase("GET") &&
				!method.equalsIgnoreCase("HEAD")) {
			xchg.sendResponseHeaders(501, -1);
			return;
		}
		Map<String, List<String>> reqHeaders = xchg.getRequestHeaders();
		URI uri = xchg.getRequestURI();
	    InputStream reqBody = xchg.getRequestBody();
	    Cipher cipher = null;
		if (encrypt) {
			String keyString = reqHeaders.get("Session-Key").get(0);
			byte[] keyBytes = Base64Coder.decode(keyString);
			try {
				cipher = Cipher.getInstance("RSA/ECB/NoPadding");
				cipher.init(Cipher.DECRYPT_MODE, privKey);
				byte[] sessionLock = cipher.doFinal(keyBytes);
				byte[] sessionKey = new byte[32];
				System.arraycopy(sessionLock, 0, sessionKey, 0, 32);
				byte[] sessionIV = new byte[16];
				System.arraycopy(sessionLock, 32, sessionIV, 0, 16);
				SecretKey secretKey = new SecretKeySpec(sessionKey, "AES");
				cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(sessionIV));
			    int len;
			    byte[] response = new byte[1024];
			    ByteArrayOutputStream ba = new ByteArrayOutputStream();
			    while ((len = reqBody.read(response)) != -1)
			    	ba.write(response, 0, len);
			    ba.flush();
			    reqBody.close();
				byte[] plaintext = cipher.doFinal(ba.toByteArray());
				cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(sessionIV));
				reqBody = new ByteArrayInputStream(plaintext);
				reqHeaders = new TreeMap<String, List<String>>();
				BufferedReader br = new BufferedReader(new InputStreamReader(reqBody));
				String header = br.readLine();
				String[] splitAction = header.split(" ", 3);
				if (splitAction.length != 3) {
					xchg.sendResponseHeaders(400, -1);
					return;
				}
				method = splitAction[0];
				uri = new URI(splitAction[1]);
				while ((header = br.readLine())!="") {
					if (header == null)
						break;
					String[] splitHead = header.split(": ", 2);
					if (splitHead.length != 2)
						continue;
					if (reqHeaders.get(splitHead[0]) == null)
						reqHeaders.put(splitHead[0], new LinkedList<String>());
					reqHeaders.get(splitHead[0]).add(splitHead[1]);
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			} catch (NoSuchPaddingException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			} catch (InvalidKeyException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			} catch (IllegalBlockSizeException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			} catch (BadPaddingException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			} catch (InvalidAlgorithmParameterException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			} catch (URISyntaxException e) {
				e.printStackTrace();
				xchg.sendResponseHeaders(400, -1);
				return;
			}
		}
		List<String> host = reqHeaders.get("Host");
		if (host == null) {
			xchg.sendResponseHeaders(400, -1);
			return;
		}
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

		byte[] post = null;
		if (method.contentEquals("POST")) {
		    int len;
		    byte[] response = new byte[1024];
		    ByteArrayOutputStream ba = new ByteArrayOutputStream();
		    while ((len = reqBody.read(response)) != -1)
		    	ba.write(response, 0, len);
		    ba.flush();
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

		String via = new String(xchg.getProtocol() + " " + xchg.getLocalAddress().getHostName() + ":" + xchg.getLocalAddress().getPort());
		System.out.println("\nRequest Headers:\n");
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
	    if (!encrypt) {
	    	xchg.sendResponseHeaders(conn.getResponseCode(), body.size());
	    	os = xchg.getResponseBody();
	    	body.writeTo(os);
	    	os.flush();
	    	os.close();
		    done = true;
		    break;
	    }

		try {
			byte[] ciphertext = cipher.doFinal(body.toByteArray());
			xchg.getResponseHeaders().add("Cache-Control", "no-cache");
			xchg.sendResponseHeaders(700, ciphertext.length);
	    	os = xchg.getResponseBody();
	    	os.write(ciphertext);
	    	os.flush();
	    	os.close();
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
		} catch (BadPaddingException e) {
			e.printStackTrace();
		}
	    done = true;
	    } catch (IOException e) {
	    	delPeer(peer);
	    }
	    } while (!done && maxForwards >= 0);
	}

}
