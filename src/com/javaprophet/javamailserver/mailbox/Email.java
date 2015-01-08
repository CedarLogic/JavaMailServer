package com.javaprophet.javamailserver.mailbox;

import java.util.ArrayList;

public class Email {
	public final ArrayList<String> flags = new ArrayList<String>();
	public final String data;
	public final int uid;
	
	public Email(String data, int uid) {
		this.data = data;
		this.uid = uid;
	}
}
