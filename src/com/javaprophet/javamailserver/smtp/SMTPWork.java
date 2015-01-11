package com.javaprophet.javamailserver.smtp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.mailbox.EmailAccount;

public class SMTPWork {
	public final Socket s;
	public final DataInputStream in;
	public final DataOutputStream out;
	public final boolean ssl;
	public int state = 0;
	public boolean isExtended = false;
	public EmailAccount authUser = null;
	public String mailFrom = "";
	public ArrayList<String> rcptTo = new ArrayList<String>();
	public ArrayList<String> data = new ArrayList<String>();
	
	public SMTPWork(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		this.s = s;
		this.in = in;
		this.out = out;
		this.ssl = ssl;
	}
	
	public void writeMLine(int response, String data) throws IOException {
		System.out.println(hashCode() + ": " + response + "-" + data);
		out.write((response + "-" + data + JavaMailServer.crlf).getBytes());
	}
	
	public void writeLine(int response, String data) throws IOException {
		System.out.println(hashCode() + ": " + response + " " + data);
		out.write((response + " " + data + JavaMailServer.crlf).getBytes());
	}
}
