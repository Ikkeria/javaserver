package com.diku.simpleserver;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlValue;

public class Option {
	private String key;
	private Integer option;
	public void setKey(String key) {
		this.key = key;
	}
	@XmlAttribute (name="key")
	public String getKey() {
		return key;
	}
	public void setOption(Integer option) {
		this.option = option;
	}
	@XmlValue
	public Integer getOption() {
		return option;
	}
}
