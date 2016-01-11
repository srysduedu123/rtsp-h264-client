package br.com.voicetechnology.rtspclient.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;


import br.com.voicetechnology.rtspclient.util.RTPPacket;


public class Server {

	private static final int PORT = 2000;
	private DatagramSocket dataSocket;
	private DatagramPacket dataPacket;
	private byte buffer[];
	private String receiveStr;
	private static byte[] startCode = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01};

	public Server() {
		Init();
	}

	public void Init() {
		try {
			dataSocket = new DatagramSocket(PORT);
			buffer = new byte[2048];
			dataPacket = new DatagramPacket(buffer, buffer.length);
			receiveStr = "";
			int i = 0;
			H264Handler handler = new H264Handler();
			int count = 0;
			System.out.println("start");
			while (i == 0)// 无数据，则循环
			{
				dataSocket.receive(dataPacket);
				i = dataPacket.getLength();
				// 接收数据
				if (i > 0) {
					StringBuilder builder = new StringBuilder();
					for (int j = 12; j < Math.min(50, buffer.length); j++) {
						String string = String.format("%08d", Integer.valueOf(Integer.toBinaryString(buffer[j] & 0xff)));
						builder.append(string);
					}
					RTPPacket rtpPacket = new RTPPacket(buffer, 0, i);
					count++;
					if(count < 300){
						System.out.println(builder.toString() + "     " + rtpPacket.getTimestamp().longValue());
					}
					handler.handle(rtpPacket.getPayload(), 0, rtpPacket.getPayload().length);
					i = 0;// 循环接收
				} else {
					//System.out.println("a paket end " + i);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {
		new Server();
	}

	static class H264Handler extends Thread {
		private File file;
		private FileOutputStream output;
		private ByteArrayOutputStream buffer;
		private boolean start = false; 
		public H264Handler(){
			file = new File("F:/aa.h264");
			try {
				output = new FileOutputStream(file);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			buffer = new ByteArrayOutputStream();
		}
		
		public void handle(byte[] bs, int off, int len) throws Exception{
			if(getType(bs, off, len) == 7){
				start = true;
			}
			if(!start){
				return;
			}
			if(isSingle(bs, off, len)){
				output.write(startCode);
				output.write(bs, off, len);
				output.flush();
			}else if(isPice(bs, off, len)){
				if(isBegan(bs, off, len)){
					//System.out.println("get began " + buffer.size());
					if(buffer.size() != 0){
						save();
						//buffer.reset();
					}
					bs[off + 1] = (byte)((bs[off] & 0xE0) ^ (bs[off + 1] & 0x1F));
					buffer.write(bs, off + 1, len - 1);
				}else if(isMiddle(bs, off, len)){
					//System.out.println("get middle " + buffer.toByteArray().length);
					if(buffer.size() != 0){
						buffer.write(bs, off + 2, len - 2);
					}else{
						bs[off + 1] = (byte)((bs[off] & 0xE0) ^ (bs[off + 1] & 0x1F));
						buffer.write(bs, off + 1, len - 1);
					}
				}else if(isEnd(bs, off, len)){
					//System.out.println("get end " + buffer.size());
					if(buffer.size() != 0){
						buffer.write(bs, off + 2, len - 2);
					}else{
						bs[off + 1] = (byte)((bs[off] & 0xE0) ^ (bs[off + 1] & 0x1F));
						buffer.write(bs, off + 1, len - 1);
					}
					save();
				}
			}
		}
		
		public void save() throws IOException{
			output.write(startCode);
			output.write(buffer.toByteArray());
			output.flush();
			buffer.reset();
		}
		
		private int getType(byte[] bs, int off, int len) throws Exception{
			if(bs == null || bs.length - off < len || len < 1){
				throw new Exception();
			}
			int type = bs[off] & 0x1F;
			return type;
		}
		
		private boolean isSingle(byte[] bs, int off, int len) throws Exception{
			if(bs == null || bs.length - off < len || len < 1){
				throw new Exception();
			}
			int type = bs[off] & 0x1F;
			if(type < 24 && type > 0){
				return true;
			}else{
				return false;
			}
		}
		
		private boolean isPice(byte[] bs, int off, int len) throws Exception{
			if(bs == null || bs.length - off < len || len < 1){
				throw new Exception();
			}
			int type = bs[off] & 0x1F;
			if(type == 28 || type == 29){
				return true;
			}else{
				return false;
			}
		}
		
		private boolean isBegan(byte[] bs, int off, int len) throws Exception{
			if(bs == null  || bs.length - off < len || len < 2){
				throw new Exception();
			}
			int type = bs[off + 1] & 0xE0;
			if(type == 128){
				return true;
			}else{
				return false;
			}
		}
		
		private boolean isMiddle(byte[] bs, int off, int len) throws Exception{
			if(bs == null || bs.length - off < len || len < 2){
				throw new Exception();
			}
			int type = bs[off + 1] & 0xE0;
			if(type == 0){
				return true;
			}else{
				return false;
			}
		}
		
		private boolean isEnd(byte[] bs, int off, int len) throws Exception{
			if(bs == null  || bs.length - off < len || len < 2){
				throw new Exception();
			}
			int type = bs[off + 1] & 0xE0;
			if(type == 64){
				return true;
			}else{
				return false;
			}
		}
	}
}
