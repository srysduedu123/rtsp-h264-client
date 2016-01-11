package br.com.voicetechnology.rtspclient.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class ReadH264 {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		File file = new File("F:/aa.h264");
		FileInputStream input = new FileInputStream(file);
		byte[] buffer = new byte[1024];
		int i = 0;
		System.out.println("start");
		int count = 0;
		while((i = input.read(buffer, 0, buffer.length)) >= 0){
			StringBuilder builder = new StringBuilder();
			for (int j = 0; j < i; j++) {
				String string = String.format("%08d", Integer.valueOf(Integer.toBinaryString(buffer[j] & 0xff)));
				builder.append(string);
			}
			int length = Math.min(300, builder.length());
			System.out.println(builder.toString().substring(0, length));
			count++;
			if(count > 100){
				break;
			}
		}
	}

}
