package com.javaprophet.javamailserver.mailbox;

import java.util.ArrayList;

public class Email {
	public final ArrayList<String> flags = new ArrayList<String>();
	public final String data;
	
	public Email(String data) {
		this.data = data;
	}
}
