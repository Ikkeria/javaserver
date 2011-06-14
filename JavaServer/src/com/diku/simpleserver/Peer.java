package com.diku.simpleserver;

import javax.xml.bind.annotation.XmlAttribute;

public class Peer {
	private Boolean super_peer;
	private int port;
	private String last_registered;
	private String ip;

	public void setSuper_peer(Boolean super_peer) {
		this.super_peer = super_peer;
	}
	@XmlAttribute (name="superPeer")
	public Boolean getSuper_peer() {
		return super_peer;
	}
	public void setPort(int port) {
		this.port = port;
	}
	@XmlAttribute (name="port")
	public int getPort() {
		return port;
	}
	public void setLast_registered(String last_registered) {
		this.last_registered = last_registered;
	}
	@XmlAttribute (name="lastRegistered")
	public String getLast_registered() {
		return last_registered;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	@XmlAttribute (name="ip")
	public String getIp() {
		return ip;
	}
}
