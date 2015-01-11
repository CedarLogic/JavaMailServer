package com.javaprophet.javamailserver.smtp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadWorkerSMTP extends Thread {
	
	public ThreadWorkerSMTP() {
		
	}
	
	public static void clearWork() {
		workQueue.clear();
	}
	
	private static LinkedBlockingQueue<SMTPWork> workQueue = new LinkedBlockingQueue<SMTPWork>();
	
	public static void addWork(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		workQueue.add(new SMTPWork(s, in, out, ssl));
	}
	
	private boolean keepRunning = true;
	
	public void close() {
		keepRunning = false;
	}
	
	public void run() {
		while (keepRunning) {
			SMTPWork focus = workQueue.poll();
			if (focus == null) {
				try {
					Thread.sleep(10L);
				}catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			try {
				if (!focus.s.isClosed()) {
					String line = focus.in.readLine().trim();
					System.out.println(focus.hashCode() + ": " + line);
					String cmd = "";
					if (focus.state != 101) {
						cmd = line.contains(" ") ? line.substring(0, line.indexOf(" ")) : line;
						cmd = cmd.toLowerCase();
						line = line.substring(cmd.length()).trim();
					}
					boolean r = false;
					for (SMTPCommand comm : SMTPHandler.commands) {
						if ((focus.state == 101 || comm.comm.equals(cmd)) && focus.state <= comm.maxState && focus.state >= comm.minState) {
							comm.run(focus, line);
							r = true;
							break;
						}
					}
					if (!r) {
						focus.writeLine(500, "Command not recognized");
					}
				}
			}catch (IOException e) {
				if (!(e instanceof SocketException)) e.printStackTrace();
			}
			if (focus != null && !focus.s.isClosed()) workQueue.add(focus);
		}
	}
}
