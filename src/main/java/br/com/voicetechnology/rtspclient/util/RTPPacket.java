package br.com.voicetechnology.rtspclient.util;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

/***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   Copyright (C) 2005 - Matteo Merli - matteo.merli@gmail.com            *
 *                                                                         *
 ***************************************************************************/


/**
 * This class wraps a RTP packet providing method to convert from and to a
 * {@link IoBuffer}.
 * <p>
 * A RTP packet is composed of an header and the subsequent payload.
 * <p>
 * The RTP header has the following format:
 * 
 * <pre>
 *        0                   1                   2                   3
 *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |                           timestamp                           |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *        |           synchronization source (SSRC) identifier            |
 *        +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
 *        |            contributing source (CSRC) identifiers             |
 *        |                             ....                              |
 *        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 * 
 * The first twelve octets are present in every RTP packet, while the list of
 * CSRC identifiers is present only when inserted by a mixer.
 * 
 * @author Matteo Merli
 */
public class RTPPacket {

	/**
	 * This field identifies the version of RTP. The version defined by this
	 * specification is two (2). (The value 1 is used by the first draft version
	 * of RTP and the value 0 is used by the protocol initially implemented in
	 * the "vat" audio tool.)
	 */
	private byte version;

	/**
	 * Padding flag. If the padding bit is set, the packet contains one or more
	 * additional padding octets at the end which are not part of the payload.
	 * The last octet of the padding contains a count of how many padding octets
	 * should be ignored, including itself. Padding may be needed by some
	 * encryption algorithms with fixed block sizes or for carrying several RTP
	 * packets in a lower-layer protocol data unit.
	 */
	private boolean padding;

	/**
	 * Extension Flag. If the extension bit is set, the fixed header MUST be
	 * followed by exactly one header extension, with a format defined in
	 * Section 5.3.1 of the RFC.
	 */
	private boolean extension;

	/**
	 * The CSRC count contains the number of CSRC identifiers that follow the
	 * fixed header.
	 */
	private byte csrcCount;

	/**
	 * The interpretation of the marker is defined by a profile. It is intended
	 * to allow significant events such as frame boundaries to be marked in the
	 * packet stream. A profile MAY define additional marker bits or specify
	 * that there is no marker bit by changing the number of bits in the payload
	 * type field (see Section 5.3).
	 */
	private boolean marker;

	/**
	 * This field identifies the format of the RTP payload and determines its
	 * interpretation by the application. A profile MAY specify a default static
	 * mapping of payload type codes to payload formats. Additional payload type
	 * codes MAY be defined dynamically through non-RTP means (see Section 3).
	 * <p>
	 * A set of default mappings for audio and video is specified in the
	 * companion RFC 3551 [1]. An RTP source MAY change the payload type during
	 * a session, but this field SHOULD NOT be used for multiplexing separate
	 * media streams (see Section 5.2).
	 */
	private UnsignedByte payloadType;

	/**
	 * The sequence number increments by one for each RTP data packet sent, and
	 * may be used by the receiver to detect packet loss and to restore packet
	 * sequence. The initial value of the sequence number SHOULD be random
	 * (unpredictable) to make known-plaintext attacks on encryption more
	 * difficult, even if the source itself does not encrypt according to the
	 * method in Section 9.1, because the packets may flow through a translator
	 * that does.
	 */
	private UnsignedShort sequence;

	/**
	 * The timestamp reflects the sampling instant of the first octet in the RTP
	 * data packet. The sampling instant MUST be derived from a clock that
	 * increments monotonically and linearly in time to allow synchronization
	 * and jitter calculations (see Section 6.4.1).
	 */
	private UnsignedInt timestamp;

	/**
	 * The SSRC field identifies the synchronization source. This identifier
	 * SHOULD be chosen randomly, with the intent that no two synchronization
	 * sources within the same RTP session will have the same SSRC identifier.
	 */
	private UnsignedInt ssrc;

	/**
	 * The CSRC list identifies the contributing sources for the payload
	 * contained in this packet. The number of identifiers is given by the CC
	 * field. If there are more than 15 contributing sources, only 15 can be
	 * identified.
	 */
	private UnsignedInt[] csrc = {};

	private short profileExtension;

	private byte[] headerExtension = {};

	/**
	 * Content of the packet.
	 */
	private byte[] payload = {};

	/**
	 * Construct a new RTPPacket reading the fields from a IoBuffer
	 * 
	 * @param buffer
	 *            the buffer containing the packet
	 */
	public RTPPacket(byte[] buffer, int off, int len) {
		// Read the packet header
		byte c = buffer[off++];
		// |V=2|P=1|X=1| CC=4 |
		this.version = (byte) ((c & 0xC0) >> 6);
		this.padding = ((c & 0x20) >> 5) == 1;
		this.extension = ((c & 0x10) >> 4) == 1;
		this.csrcCount = (byte) (c & 0x0F);

		c = buffer[off++];
		// |M=1| PT=7 |
		this.marker = ((c & 0x80) >> 7) == 1;
		this.payloadType = new UnsignedByte(c & 0x7F);

		this.sequence = UnsignedShort.fromBytes(buffer, off);
		off+=2;
		this.timestamp = UnsignedInt.fromBytes(buffer, off);
		off+=4;
		this.ssrc = UnsignedInt.fromBytes(buffer, off);
		off+=4;

		// CSRC list
		csrc = new UnsignedInt[csrcCount];
		for (int i = 0; i < csrcCount; i++) {
			csrc[i] = UnsignedInt.fromBytes(buffer, off);
			off+=4;
		}

		// Read the extension header if present
		if (extension) {
			this.profileExtension = UnsignedShort.fromBytes(buffer, off).shortValue();
			off+=2;
			int length = UnsignedShort.fromBytes(buffer, off).intValue();
			off+=2;
			this.headerExtension = new byte[length];
			System.arraycopy(buffer, off, headerExtension, 0, length);
			off+=length;
		}

		// Read the payload
		int payloadSize = len - off;
		if(padding){
			payloadSize -= buffer[len - 1];
		}
		this.payload = new byte[payloadSize];
		System.arraycopy(buffer, off, payload, 0, payloadSize);
		if (version != 2) {
			//log.debug("Packet Version is not 2.");
		}
	}

	protected RTPPacket() {
		// Creates an empty packet
	}

	/**
	 * @return Returns the csrc.
	 */
	public UnsignedInt[] getCsrc() {
		return csrc;
	}

	/**
	 * @param csrc
	 *            The csrc to set.
	 */
	public void setCsrc(UnsignedInt[] csrc) {
		this.csrc = csrc;
	}

	/**
	 * @return Returns the csrcCount.
	 */
	public byte getCsrcCount() {
		return csrcCount;
	}

	/**
	 * @param csrcCount
	 *            The csrcCount to set.
	 */
	public void setCsrcCount(byte csrcCount) {
		this.csrcCount = csrcCount;
	}

	/**
	 * @return Returns the extension.
	 */
	public boolean isExtension() {
		return extension;
	}

	/**
	 * @param extension
	 *            The extension to set.
	 */
	public void setExtension(boolean extension) {
		this.extension = extension;
	}

	/**
	 * @return Returns the marker.
	 */
	public boolean isMarker() {
		return marker;
	}

	/**
	 * @param marker
	 *            The marker to set.
	 */
	public void setMarker(boolean marker) {
		this.marker = marker;
	}

	/**
	 * @return Returns the padding.
	 */
	public boolean isPadding() {
		return padding;
	}

	/**
	 * @param padding
	 *            The padding to set.
	 */
	public void setPadding(boolean padding) {
		this.padding = padding;
	}

	/**
	 * @return Returns the payload.
	 */
	public byte[] getPayload() {
		return payload;
	}

	/**
	 * @param payload
	 *            The payload to set.
	 */
	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	/**
	 * @return Returns the payloadType.
	 */
	public UnsignedByte getPayloadType() {
		return payloadType;
	}

	/**
	 * @param payloadType
	 *            The payloadType to set.
	 */
	public void setPayloadType(UnsignedByte payloadType) {
		this.payloadType = payloadType;
	}

	/**
	 * @return Returns the sequence.
	 */
	public UnsignedShort getSequence() {
		return sequence;
	}

	/**
	 * @param sequence
	 *            The sequence to set.
	 */
	public void setSequence(UnsignedShort sequence) {
		this.sequence = sequence;
	}

	/**
	 * @return Returns the ssrc.
	 */
	public UnsignedInt getSsrc() {
		return ssrc;
	}

	/**
	 * @param ssrc
	 *            The ssrc to set.
	 */
	public void setSsrc(UnsignedInt ssrc) {
		this.ssrc = ssrc;
	}

	/**
	 * @return Returns the timestamp.
	 */
	public UnsignedInt getTimestamp() {
		return timestamp;
	}

	/**
	 * @param timestamp
	 *            The timestamp to set.
	 */
	public void setTimestamp(UnsignedInt timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * @return Returns the version.
	 */
	public byte getVersion() {
		return version;
	}

	/**
	 * @param version
	 *            The version to set.
	 */
	public void setVersion(byte version) {
		this.version = version;
	}

}
