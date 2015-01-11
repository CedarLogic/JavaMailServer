package com.javaprophet.javamailserver.imap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.mailbox.EmailAccount;
import com.javaprophet.javamailserver.mailbox.Mailbox;

public class IMAPWork {
	public final Socket s;
	public final DataInputStream in;
	public final DataOutputStream out;
	public final boolean ssl;
	public int state = 0;
	public String authMethod = "";
	public EmailAccount authUser = null;
	public Mailbox selectedMailbox = null;
	public boolean isExamine = false;
	
	public IMAPWork(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		this.s = s;
		this.in = in;
		this.out = out;
		this.ssl = ssl;
	}
	
	public void writeLine(IMAPWork w, String id, String data) throws IOException {
		System.out.println(w.hashCode() + ": " + id + " " + data);
		w.out.write(((id.length() > 0 ? (id + " ") : "") + data + JavaMailServer.crlf).getBytes());
	}
}
