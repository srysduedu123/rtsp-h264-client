/*
   Copyright 2010 Voice Technology Ind. e Com. Ltda.
 
   This file is part of RTSPClientLib.

    RTSPClientLib is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    RTSPClientLib is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with RTSPClientLib.  If not, see <http://www.gnu.org/licenses/>.

*/
package br.com.voicetechnology.rtspclient;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import br.com.voicetechnology.rtspclient.concepts.Client;
import br.com.voicetechnology.rtspclient.concepts.ClientListener;
import br.com.voicetechnology.rtspclient.concepts.Header;
import br.com.voicetechnology.rtspclient.concepts.Message;
import br.com.voicetechnology.rtspclient.concepts.MessageBuffer;
import br.com.voicetechnology.rtspclient.concepts.MessageFactory;
import br.com.voicetechnology.rtspclient.concepts.Request;
import br.com.voicetechnology.rtspclient.concepts.Response;
import br.com.voicetechnology.rtspclient.concepts.Transport;
import br.com.voicetechnology.rtspclient.concepts.TransportListener;
import br.com.voicetechnology.rtspclient.concepts.Request.Method;
import br.com.voicetechnology.rtspclient.headers.SessionHeader;
import br.com.voicetechnology.rtspclient.headers.TransportHeader;
import br.com.voicetechnology.rtspclient.headers.TransportHeader.LowerTransport;
import br.com.voicetechnology.rtspclient.messages.RTSPOptionsRequest;

public class RTSPClient implements Client, TransportListener
{
	private Transport transport;

	private MessageFactory messageFactory;

	private MessageBuffer messageBuffer;

	private volatile int cseq;

	private SessionHeader session;

	/**
	 * URI kept from last setup.
	 */
	private URI uri;

	private Map<Integer, Request> outstanding;

	private ClientListener clientListener;

	public RTSPClient()
	{
		messageFactory = new RTSPMessageFactory();
		cseq = 0;
		outstanding = new HashMap<Integer, Request>();
		messageBuffer = new MessageBuffer();
	}

	@Override
	public Transport getTransport()
	{
		return transport;
	}

	@Override
	public void setSession(SessionHeader session)
	{
		this.session = session;
	}

	@Override
	public MessageFactory getMessageFactory()
	{
		return messageFactory;
	}

	@Override
	public URI getURI()
	{
		return uri;
	}

	@Override
	public void options(String uri, URI endpoint) throws URISyntaxException,
			IOException
	{
		try
		{
			RTSPOptionsRequest message = (RTSPOptionsRequest) messageFactory
					.outgoingRequest(uri, Method.OPTIONS, nextCSeq());
			if(!getTransport().isConnected())
				message.addHeader(new Header("Connection", "close"));
			send(message, endpoint);
		} catch(MissingHeaderException e)
		{
			if(clientListener != null)
				clientListener.generalError(this, e);
		}
	}

	@Override
	public void play() throws IOException
	{
		try
		{
			send(messageFactory.outgoingRequest(uri.toString(), Method.PLAY,
					nextCSeq(), session));
		} catch(Exception e)
		{
			if(clientListener != null)
				clientListener.generalError(this, e);
		}
	}

	@Override
	public void record() throws IOException
	{
		throw new UnsupportedOperationException(
				"Recording is not supported in current version.");
	}

	@Override
	public void setClientListener(ClientListener listener)
	{
		clientListener = listener;
	}

	@Override
	public ClientListener getClientListener()
	{
		return clientListener;
	}

	@Override
	public void setTransport(Transport transport)
	{
		this.transport = transport;
		transport.setTransportListener(this);
	}

	@Override
	public void describe(URI uri) throws IOException
	{
		this.uri = uri;
		try
		{
			send(messageFactory.outgoingRequest(uri.toString(), Method.DESCRIBE,
					nextCSeq(), new Header("Accept", "application/sdp")));
		} catch(Exception e)
		{
			if(clientListener != null)
				clientListener.generalError(this, e);
		}
	}

	@Override
	public void setup(URI uri, int localPort) throws IOException
	{
		this.uri = uri;
		try
		{
			String portParam = "client_port=" + localPort + "-" + (1 + localPort);

			send(getSetup(uri.toString(), localPort, new TransportHeader(
					LowerTransport.DEFAULT, "unicast", portParam), session));
		} catch(Exception e)
		{
			if(clientListener != null)
				clientListener.generalError(this, e);
		}
	}

	@Override
	public void setup(URI uri, int localPort, String resource) throws IOException
	{
		this.uri = uri;
		try
		{
			String portParam = "client_port=" + localPort + "-" + (1 + localPort);
			String finalURI = uri.toString();
			if(resource != null && resource.length() != 0 && !resource.equals("*"))
				finalURI += '/' + resource;
			send(getSetup(finalURI, localPort, new TransportHeader(
					LowerTransport.DEFAULT, "unicast", portParam), session));
		} catch(Exception e)
		{
			if(clientListener != null)
				clientListener.generalError(this, e);
		}
	}

	@Override
	public void teardown()
	{
		if(session == null)
			return;
		try
		{
			send(messageFactory.outgoingRequest(uri.toString(), Method.TEARDOWN,
					nextCSeq(), session, new Header("Connection", "close")));
		} catch(Exception e)
		{
			if(clientListener != null)
				clientListener.generalError(this, e);
		}
	}

	@Override
	public void connected(Transport t) throws Throwable
	{
	}

	@Override
	public void dataReceived(Transport t, byte[] data, int size) throws Throwable
	{
		messageBuffer.addData(data, size);
		while(messageBuffer.getLength() > 0)
			try
			{
				messageFactory.incomingMessage(messageBuffer);
				messageBuffer.discardData();
				Message message = messageBuffer.getMessage();
				if(message instanceof Request)
					send(messageFactory.outgoingResponse(405, "Method Not Allowed",
							message.getCSeq().getValue()));
				else
				{
					Request request = null;
					synchronized(outstanding)
					{
						request = outstanding.remove(message.getCSeq().getValue());
					}
					Response response = (Response) message;
					request.handleResponse(this, response);
					clientListener.response(this, request, response);
				}
			} catch(IncompleteMessageException ie)
			{
				break;
			} catch(InvalidMessageException e)
			{
				messageBuffer.discardData();
				if(clientListener != null)
					clientListener.generalError(this, e.getCause());
			}
	}

	@Override
	public void dataSent(Transport t) throws Throwable
	{
	}

	@Override
	public void error(Transport t, Throwable error)
	{
		clientListener.generalError(this, error);
	}

	@Override
	public void error(Transport t, Message message, Throwable error)
	{
		clientListener.requestFailed(this, (Request) message, error);
	}

	@Override
	public void remoteDisconnection(Transport t) throws Throwable
	{
		synchronized(outstanding)
		{
			for(Map.Entry<Integer, Request> request : outstanding.entrySet())
				clientListener.requestFailed(this, request.getValue(),
						new SocketException("Socket has been closed"));
		}
	}

	@Override
	public int nextCSeq()
	{
		return cseq++;
	}

	@Override
	public void send(Message message) throws IOException, MissingHeaderException
	{
		send(message, uri);
	}

	private void send(Message message, URI endpoint) throws IOException,
			MissingHeaderException
	{
		if(!transport.isConnected())
			transport.connect(endpoint);

		if(message instanceof Request)
		{
			Request request = (Request) message;
			synchronized(outstanding)
			{
				outstanding.put(message.getCSeq().getValue(), request);
			}
			try
			{
				transport.sendMessage(message);
			} catch(IOException e)
			{
				clientListener.requestFailed(this, request, e);
			}
		} else
			transport.sendMessage(message);
	}

	private Request getSetup(String uri, int localPort, Header... headers)
			throws URISyntaxException
	{
		return getMessageFactory().outgoingRequest(uri, Method.SETUP, nextCSeq(),
				headers);
	}
}