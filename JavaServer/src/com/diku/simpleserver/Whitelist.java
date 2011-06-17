package com.diku.simpleserver;

import java.util.ArrayList;
import java.util.List;

public class Whitelist {
	private List<String> domain = new ArrayList<String>();

	public void setDomain(List<String> domain) {
		this.domain = domain;
	}

	public List<String> getDomain() {
		return domain;
	}

}
