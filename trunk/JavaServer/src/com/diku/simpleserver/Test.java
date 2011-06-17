package com.diku.simpleserver;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

public class Test {

	/**
	 * @param args
	 * @throws InvalidParameterSpecException 
	 * @throws NoSuchPaddingException 
	 * @throws NoSuchAlgorithmException 
	 * @throws InvalidKeyException 
	 * @throws UnsupportedEncodingException 
	 * @throws BadPaddingException 
	 * @throws IllegalBlockSizeException 
	 * @throws InvalidAlgorithmParameterException 
	 * @throws NoSuchProviderException 
	 */
	public static void main(String[] args) throws InvalidParameterSpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchProviderException {
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		kgen.init(256);
		SecretKey key = kgen.generateKey();
		byte[] raw = key.getEncoded();
		Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		byte[] iv ={0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
		IvParameterSpec ips = new IvParameterSpec(iv);
		cipher.init(Cipher.ENCRYPT_MODE, key, ips);
//		AlgorithmParameters params = cipher.getParameters();
//		byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
		byte[] ciphertext = cipher.doFinal("Hello World!".getBytes("UTF-8"));
		System.out.println("Raw: " + raw.length);
		System.out.println("IV: " + iv.length);
		System.out.println("Ciphertext: " + ciphertext.length);
		System.out.println("Decode:");
		SecretKey secret = new SecretKeySpec(raw, "AES");
		System.out.println("Remade: " + secret.getEncoded().length);
		Cipher cipher2 = Cipher.getInstance("AES/CBC/PKCS5Padding");
		cipher2.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
		String plaintext = new String(cipher2.doFinal(ciphertext), "UTF-8");
		System.out.println(plaintext);
		if (true)
			return;
		Tracker tracker = new Tracker();
//		tracker.setOptions(new Options());
		Option option = new Option();
		option.setKey("First test");
		option.setOption(10);
		tracker.getOptions().add(option);
		option = new Option();
		option.setKey("Second test");
		option.setOption(22);
		tracker.getOptions().add(option);
//		tracker.setWhitelist(new Whitelist());
		tracker.getWhitelist().add("google.com.");
		tracker.getWhitelist().add("bold.dk.");
		Peer peer = new Peer();
		peer.setSuper_peer(false);
		peer.setPort(2000);
		peer.setLast_registered("10/12/2001");
		peer.setIp("192.168.1.1");
//		tracker.setPeers(new Peers());
		tracker.getPeers().add(peer);
		peer = new Peer();
		peer.setSuper_peer(true);
		peer.setPort(3030);
		peer.setLast_registered("04/10/1991");
		peer.setIp("122.158.10.3");
		tracker.getPeers().add(peer);
        try {
    		JAXBContext jaxbContext = JAXBContext.newInstance(Tracker.class);

            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            OutputStream os = new FileOutputStream("tracker.xml");

            marshaller.marshal(tracker, os);
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
	}

}
