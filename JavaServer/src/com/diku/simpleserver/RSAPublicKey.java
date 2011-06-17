package com.diku.simpleserver;

import java.math.BigInteger;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement (name="RSAPublicKey")
public class RSAPublicKey {
	private BigInteger n;
	private BigInteger e;
	public void setN(BigInteger n) {
		this.n = n;
	}
	@XmlElement (name="n")
	public BigInteger getN() {
		return n;
	}
	public void setE(BigInteger e) {
		this.e = e;
	}
	@XmlElement (name="e")
	public BigInteger getE() {
		return e;
	}

}
