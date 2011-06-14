package com.diku.simpleserver;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement (name="tracker")
public class Tracker {
	private List<Option> options = new ArrayList<Option>();
	private List<String> whitelist = new ArrayList<String>();
	private List<Peer> peers = new ArrayList<Peer>();

	public void setOptions(List<Option> options) {
		this.options = options;
	}

	@XmlElementWrapper (name="options")
	@XmlElement (name="option")
	public List<Option> getOptions() {
		return options;
	}

	public void setWhitelist(List<String> whitelist) {
		this.whitelist = whitelist;
	}

	@XmlElementWrapper (name="whitelist")
	@XmlElement (name="domain")
	public List<String> getWhitelist() {
		return whitelist;
	}

	public void setPeers(List<Peer> peers) {
		this.peers = peers;
	}

	@XmlElementWrapper (name="peers")
	@XmlElement (name="peer")
	public List<Peer> getPeers() {
		return peers;
	}
}
