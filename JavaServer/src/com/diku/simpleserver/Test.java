package com.diku.simpleserver;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

public class Test {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
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
