package cc.sferalabs.sfera.drivers.gsm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import cc.sferalabs.sfera.drivers.gsm.events.CallEvent;
import cc.sferalabs.sfera.drivers.gsm.events.GsmSignalEvent;
import cc.sferalabs.sfera.drivers.gsm.events.SmsEvent;
import cc.sferalabs.sfera.events.Bus;
import cc.sferalabs.sfera.io.comm.CommPort;
import cc.sferalabs.sfera.io.comm.CommPortException;
import cc.sferalabs.sfera.io.comm.CommPortListener;

class CommunicationHandler implements CommPortListener {

	private static final char SUB = 0x1A;

	private final Gsm gsm;
	private final CommPort commPort;
	private final Logger logger;
	private final String countryCode;
	private final boolean useSimPhase2;

	private int commState;
	private int restart;
	private StringBuilder currData;
	private ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(10);
	Map<Integer, PartialSms> partialSmss = new HashMap<Integer, PartialSms>();

	/**
	 * 
	 * @param gsm
	 * @param commPort
	 * @param countryCode
	 * @param useSimPhase2
	 * @param logger
	 */
	CommunicationHandler(Gsm gsm, CommPort commPort, Integer countryCode, boolean useSimPhase2,
			Logger logger) {
		this.gsm = gsm;
		this.commPort = commPort;
		this.countryCode = (countryCode == null) ? null : "" + countryCode;
		this.useSimPhase2 = useSimPhase2;
		this.logger = logger;
	}

	/**
	 * 
	 * @return
	 * @throws InterruptedException
	 */
	String poll() throws InterruptedException {
		return poll(2, TimeUnit.SECONDS);
	}

	/**
	 * 
	 * @param timeout
	 * @param unit
	 * @return
	 * @throws InterruptedException
	 */
	String poll(long timeout, TimeUnit unit) throws InterruptedException {
		return messages.poll(timeout, unit);
	}

	/**
	 * 
	 */
	void clearMessages() {
		messages.clear();
	}

	@Override
	public void onRead(byte[] bytes) {
		for (byte b : bytes) {
			char c = (char) (b & 0xFF);
			if (commState == 0) {
				if (c == '\r') {
					commState = 1;
					restart = 0;
				}

				else if (c == 'A') {
					restart = 1;
				}

				else if (c == 'T' && restart == 1) {
					restart = 2;
				}

				else if (c == '-' && restart == 2) {
					logger.warn("Stop communication message received. Quitting...");
					gsm.quit();
				}
			}

			else if (commState == 1 || commState == 3) {
				if (c == '\n') {
					currData = new StringBuilder();
					commState++;
				}
			}

			else if (commState == 2 || commState == 4) {
				if (c == '\r') {
					processData(currData.toString());

				} else {
					currData.append(c);
					if (c == ' ') {
						String dataStr = currData.toString();
						if (dataStr.equals("> ")) {
							processData(dataStr);
						}
					}
				}
			}
		}
	}

	@Override
	public void onError(Throwable t) {
		logger.error("Error reading from comm port", t);
		gsm.quit();
	}

	/**
	 * 
	 * @param data
	 */
	private void processData(String data) {
		logger.debug("<< {}", data);

		if (commState == 4) { // PDU
			processPDU(data);
			if (useSimPhase2) {
				try {
					write("AT+CNMA=0\r");
					poll();
				} catch (Exception e) {
				}
			}
			commState = 0;

		} else if (data.startsWith("+CMT:")) {
			commState = 3;

		} else if (data.startsWith("+CLIP:")) {
			processCall(data);
			commState = 0;

		} else if (data.equals("OK") || data.equals("> ") || data.contains("ERROR")
				|| data.startsWith("+CPIN:") || data.startsWith("+CSCS:")
				|| data.startsWith("+CMGS:") || data.startsWith("+CSQ:")
				|| data.startsWith("+CSCA:") || data.startsWith("+CSMS:")
				|| data.startsWith("+COPS?:") || data.startsWith("+CREG?:")) {
			if (messages != null) {
				messages.add(data);
			}
			commState = 0;

		} else if (data.startsWith("AT-")) {
			logger.warn("Stop communication message received. Quitting...");
			gsm.quit();

		} else {
			commState = 0;
		}
	}

	/**
	 * 
	 * @param data
	 */
	private void processCall(String data) {
		String number = data.split(",")[0].replace("+CLIP:", "").replace('"', ' ').replace('"', ' ')
				.trim();
		if (number.length() == 0) {
			number = null;
		} else if (number.startsWith("+" + countryCode)) {
			number = number.substring(countryCode.length() + 1);
		}
		Bus.post(new CallEvent(gsm, number));
	}

	/**
	 * 
	 * @param dataString
	 */
	private void processPDU(String dataString) {
		byte[] pdu = hexStringToBytes(dataString);
		boolean udh = isMultipartMessage(pdu);
		String number = getNumber(pdu);

		int shift = (pdu[0] & 0xFF) + 2;
		int len = pdu[shift] & 0xFF;
		if (len % 2 == 1) {
			len++;
		}
		len /= 2;
		shift += len + 3;
		int alph = (pdu[shift] >>> 2) & 3;

		shift += 8;
		int numOfSeptets = pdu[shift++] & 0xFF;

		int numOfParts = 0;
		int partNum = 0;
		int ref = 0;
		boolean padding = false;
		if (udh) {
			byte udl = pdu[shift];
			shift += 3;
			if (udl == (byte) 0x05) {
				ref = pdu[shift++] & 0xFF;
				numOfSeptets -= 7;
				padding = true;
			} else {
				ref = ((pdu[shift++] & 0xFF) << 4) + (pdu[shift++] & 0xFF);
				numOfSeptets -= 8;
			}

			numOfParts = pdu[shift++];
			partNum = pdu[shift++];
		}

		byte[] data = new byte[pdu.length - shift];
		System.arraycopy(pdu, shift, data, 0, data.length);

		if (padding) {
			// remove padding bit
			int bit = 0;
			for (int i = data.length - 1; i >= 0; i--) {
				int x = data[i] & 1;
				data[i] = (byte) (((data[i] & 0xFF) >>> 1) | (bit << 7));
				bit = x;
			}
		}

		boolean error = false;

		String body;
		if (alph == 0) {
			// Default alphabet
			body = septetsToString(data, numOfSeptets);
		} else if (alph == 1) {
			// 8 bit data
			body = new String(data, StandardCharsets.ISO_8859_1);
		} else if (alph == 2) {
			// UCS2 (16bit)
			body = new String(data, StandardCharsets.UTF_16BE);
		} else {
			// Unknown DCS alphabet
			body = "";
			error = true;
		}

		String completeBody;
		if (udh) {
			PartialSms ps = null;
			ps = partialSmss.get(ref);
			if (ps == null) {
				ps = new PartialSms(numOfParts);
				partialSmss.put(ref, ps);
			}
			ps.addPart(partNum, body, error);
			if ((completeBody = ps.getCompleteMessage()) != null) {
				partialSmss.remove(ref);
			}
			error = ps.hasErrors();
		} else {
			completeBody = body;
		}

		if (completeBody != null) {
			Bus.post(new SmsEvent(gsm, number, completeBody, error));
		}
	}

	/**
	 * 
	 * @param pduStr
	 * @return
	 */
	private byte[] hexStringToBytes(String hex) {
		ByteArrayOutputStream ret = new ByteArrayOutputStream(hex.length() / 2);
		for (int i = 0; i < hex.length(); i += 2) {
			ret.write(Integer.parseInt(hex.substring(i, i + 2), 16));
		}

		return ret.toByteArray();
	}

	/**
	 * 
	 * @param pdu
	 * @return
	 */
	private boolean isMultipartMessage(byte[] pdu) {
		byte fo = pdu[(pdu[0] & 0xFF) + 1];
		return ((fo >>> 6) & 1) == 1;
	}

	/**
	 * 
	 * @param pdu
	 * @return
	 */
	private String getNumber(byte[] pdu) {
		int shift = (pdu[0] & 0xFF) + 2;
		int len = pdu[shift] & 0xFF;

		if (len % 2 == 1) {
			len++;
		}
		len /= 2;

		int typeOfNum = (pdu[++shift] >>> 4) & 7;
		shift++;

		String num;
		if (typeOfNum == 5) {
			// Alphanumeric
			byte[] data = new byte[len];
			System.arraycopy(pdu, shift, data, 0, len);
			int numOfSeptets = data.length * 8 / 7;
			num = septetsToString(data, numOfSeptets);

		} else {
			StringBuilder sb = new StringBuilder();
			for (int i = shift; i < shift + len; i++) {
				sb.append((pdu[i] & 0x0F));
				int n = (pdu[i] >>> 4) & 0x0F;
				if (n != 0x0F) {
					sb.append(n);
				}
			}

			num = sb.toString();

			if (typeOfNum == 1) {
				// International number
				if (countryCode != null && num.startsWith(countryCode)) {
					num = num.substring(countryCode.length());
				} else {
					num = "+" + num;
				}
			}
		}

		return num;
	}

	/**
	 * 
	 * @param pdu
	 * @param shift
	 * @param len
	 * @return
	 */
	private String septetsToString(byte[] data, int numOfSeptets) {
		return decodedSeptetsToString(encodedSeptetsToDecodedSeptets(data, numOfSeptets));
	}

	/**
	 * 
	 * @param bytes
	 * @return
	 */
	private String decodedSeptetsToString(byte[] bytes) {
		StringBuffer text = new StringBuffer();
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] == (byte) 0x1B) {
				if (i < bytes.length - 1) {
					boolean gotIt = false;
					char ext = (char) ((0x1B << 8) + (bytes[++i] & 0xFF));
					for (int j = 0; j < Symbols.EXT_BYTES.length; j++) {
						if (Symbols.EXT_BYTES[j] == ext) {
							text.append(Symbols.EXT_ALPHABET[j]);
							gotIt = true;
							break;
						}
					}
					if (!gotIt) { // Unknown char replacement
						text.append("?");
					}
				}
			} else {
				text.append(Symbols.STD_ALPHABET[bytes[i]]);
			}
		}

		return text.toString();
	}

	/**
	 * 
	 * @param pdu
	 * @param shift
	 * @param len
	 * @return
	 */
	private byte[] encodedSeptetsToDecodedSeptets(byte[] octetBytes, int numOfSeptets) {
		BitSet bitSet = new BitSet(octetBytes.length * 8);
		for (int i = 0; i < octetBytes.length; i++) {
			for (int j = 0; j < 8; j++) {
				if ((octetBytes[i] & (1 << j)) != 0) {
					bitSet.set(i * 8 + j);
				}
			}
		}

		byte[] ret = new byte[numOfSeptets];
		for (int i = 0; i < numOfSeptets; i++) {
			for (int j = 0; j < 7; j++) {
				if (bitSet.get(i * 7 + j)) {
					ret[i] |= (byte) (1 << j);
				}
			}
		}

		return ret;
	}

	/**
	 * 
	 * @param pin
	 * @param serviceCenterAddr
	 * @throws Exception
	 */
	synchronized void initGsm(Integer pin, String serviceCenterAddr) throws Exception {
		// Check connection
		write("AT\r");
		String resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("No connection");
		}

		// Disable echo
		write("ATE0\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("ATE0: " + resp);
		}

		// Enable verbose error messages
		write("AT+CMEE=1\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("CMEE: " + resp);
		}

		// PIN status
		write("AT+CPIN?\r");
		resp = poll();
		if (resp == null || !resp.startsWith("+CPIN:")) {
			throw new Exception("CPIN 1: " + resp);

		} else if (resp.contains("SIM PIN")) {
			resp = poll();
			if (resp == null || !resp.equals("OK")) {
				logger.debug("No SIM PIN OK: {}", resp);
			}

			if (pin == null) {
				throw new Exception("PIN required: add it to configuration");
			}
			String pinString = "" + pin;
			if (pinString.length() != 4) {
				throw new Exception("PIN format error: modify it in configuration");
			}

			// Insert PIN
			write("AT+CPIN=\"" + pinString + "\"\r");
			resp = poll(10, TimeUnit.SECONDS);
			if (resp == null || !resp.equals("OK")) {
				logger.error("PIN error: modify it in configuration");
				while (true) {
					Thread.sleep(5 * 60 * 1000);
					logger.warn("Wrong PIN, waiting...");
				}
			}

			Thread.sleep(4000);

		} else if (resp.contains("SIM PUK")) {
			poll();
			throw new Exception("SIM locked: PUK required");

		} else if (resp.contains("READY")) {
			resp = poll();
			if (resp == null || !resp.equals("OK")) {
				logger.debug("No READY OK: {}", resp);
			}

		} else {
			throw new Exception("PIN status resp: " + resp);
		}

		if (serviceCenterAddr != null) {
			write("AT+CSCA=\"" + serviceCenterAddr + "\",145\r");
			resp = poll();
			if (resp == null || !resp.equals("OK")) {
				throw new Exception("CSCA 1: " + resp);
			}

			write("AT+CSCA?\r");
			resp = poll();
			if (!resp.contains(serviceCenterAddr)) {
				throw new Exception("CSCA 2: " + resp);
			}

			resp = poll();
			if (resp == null || !resp.equals("OK")) {
				throw new Exception("CSCA 3: " + resp);
			}

		} else {
			write("AT+CSCA?\r");
			resp = poll();
			if (!resp.startsWith("+CSCA")) {
				throw new Exception("CSCA 4: " + resp);
			}
			try {
				if (resp.substring(resp.indexOf(':') + 1, resp.indexOf(',')).replaceAll("\\s", "")
						.equals("\"\"")) {
					logger.warn("Service center address not set");
				}
			} catch (Exception e) {
				logger.debug("CSCA response error: {}", resp);
			}

			resp = poll();
			if (resp == null || !resp.equals("OK")) {
				throw new Exception("CSCA 5: " + resp);
			}
		}

		// Set PDU mode
		write("AT+CMGF=0\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("CMGF: " + resp);
		}

		// Set DCS
		write("AT+CSMP=17,167,0,0\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("CSMP 1: " + resp);
		}

		if (useSimPhase2) {
			// Enable GSM phase 2+
			write("AT+CSMS=1\r");
			resp = poll();
			if (resp == null || !resp.equals("OK")) {
				throw new Exception("CSMS: " + resp);
			}
		}

		// Include text in sms notifications
		write("AT+CNMI=1,2,0,0,0\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("CNMI: " + resp);
		}

		// Include caller number in call notifications
		write("AT+CLIP=1\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			throw new Exception("CLIP: " + resp);
		}

		// reset powerup message
		write("AT$PWRMSG=\"\"\r");
		resp = poll();
		if (resp == null || !resp.equals("OK")) {
			// This operation is not supported by some modems
			logger.debug("No PWRMSG OK: {}", resp);
		}
	}

	/**
	 * 
	 * @return
	 * @throws Exception
	 */
	synchronized boolean checkState() throws Exception {
		boolean ok = true;

		// Check GSM signal level
		try {
			write("AT+CSQ\r");

			String resp = poll();
			if (resp == null || !resp.startsWith("+CSQ:")) {
				logger.debug("Response error (CSQ): {}", resp);
				ok = false;

			} else {
				try {
					resp = resp.split(",")[0].replace("+CSQ:", "").trim();
					int v = Integer.parseInt(resp);
					if (v >= 0 && v <= 31) {
						Bus.postIfChanged(new GsmSignalEvent(gsm, v));
					} else {
						throw new Exception();
					}
				} catch (Exception e) {
					logger.debug("Value format error (CSQ): {}", resp);
					ok = false;
				}
			}

			resp = poll();
			if (resp == null || !resp.startsWith("OK")) {
				logger.debug("Response error (CSQ 2): {}", resp);
				ok = false;
			}
		} catch (CommPortException e) {
			logger.debug("Write error (CSQ)");
			ok = false;
		}

		// PIN status
		try {
			write("AT+CPIN?\r");

			String resp = poll();
			if (resp == null || !resp.startsWith("+CPIN:")) {
				logger.debug("Response error (CPIN 1): {}", resp);
				ok = false;

			} else if (resp.contains("READY")) {
				resp = poll();
				if (resp == null || !resp.equals("OK")) {
					logger.debug("No READY OK: {}", resp);
				}

			} else {
				throw new Exception("modem not initialized: " + resp);
			}
		} catch (CommPortException e) {
			logger.debug("Write error (CPIN)");
			ok = false;
		}

		return ok;
	}

	/**
	 * 
	 * @param to
	 * @param body
	 * @return
	 */
	synchronized boolean send(String to, String body) {
		logger.debug("Send to {}: {}", to, body);
		if (to == null) {
			throw new NullPointerException("to");
		}
		if (body == null) {
			throw new NullPointerException("body");
		}

		if (countryCode != null && !to.startsWith("+")) {
			to = countryCode + to;
		} else {
			to = to.replace("+", "");
		}

		try {
			byte[] septets = stringToUnencodedSeptets(body);
			List<byte[]> parts = new ArrayList<byte[]>();

			if (septets.length <= 160) {
				parts.add(septets);
			} else {
				for (int i = 0; i < septets.length;) {
					byte[] partSeptes;
					if (i + 153 >= septets.length) {
						partSeptes = new byte[septets.length - i];
					} else if (septets[i + 152] == (byte) 0x1B) {
						partSeptes = new byte[152];
					} else {
						partSeptes = new byte[153];
					}

					for (int j = 0; j < partSeptes.length; j++) {
						partSeptes[j] = septets[i++];
					}

					parts.add(partSeptes);
				}
			}

			int mr = 0;
			for (int p = 0; p < parts.size(); p++) {
				byte[] partSeptes = parts.get(p);
				byte[] octects = decodedSeptetsToEncodedSeptets(partSeptes);

				int len = partSeptes.length;

				if (parts.size() > 1) {
					len += 7;

					// add padding bit
					int bit = 0;
					for (int i = 0; i < octects.length; i++) {
						int x = (octects[i] >>> 7) & 1;
						octects[i] = (byte) ((octects[i] << 1) | bit);
						bit = x;
					}
					if (octects.length % 7 == 0) {
						// need to add another octet
						byte[] octets2 = new byte[octects.length + 1];
						for (int i = 0; i < octects.length; i++) {
							octets2[i] = octects[i];
						}
						octets2[octects.length] = (byte) bit;
						octects = octets2;
					}
				}

				String pdu = buildPDU(octects, len, parts.size(), p + 1, to);

				write("AT+CMGS=" + ((pdu.length() - 2) / 2) + "\r");
				String resp = poll();

				if (resp == null || !resp.equals("> ")) {
					throw new Exception("error 1 (" + resp + ")");
				}

				write(pdu + SUB);
				resp = poll(15, TimeUnit.SECONDS);
				if (resp == null) {
					throw new Exception("error 2 (null)");
				}

				try {
					mr = Integer.parseInt(resp.replace("+CMGS:", "").trim());
				} catch (Exception e) {
					throw new Exception("error 3 (" + resp + ")");
				}

				resp = poll();
				if (resp == null || !resp.equals("OK")) {
					throw new Exception("error 4 (" + resp + ")");
				}
			}

			logger.info("SMS sent to {} (mr: {})", to, mr);
			return true;

		} catch (Exception e) {
			logger.warn("Error sending SMS to " + to, e);
			return false;
		}
	}

	/**
	 * 
	 * @param text
	 * @return
	 */
	private byte[] stringToUnencodedSeptets(String text) {
		ByteArrayOutputStream ret = new ByteArrayOutputStream();

		char ch;
		for (int i = 0; i < text.length(); i++) {
			ch = text.charAt(i);
			int index = -1;
			for (int j = 0; j < Symbols.EXT_ALPHABET.length; j++) {
				if (Symbols.EXT_ALPHABET[j] == ch) {
					index = j;
					break;
				}
			}
			// An extended char...
			if (index != -1) {
				ret.write((byte) (Symbols.EXT_BYTES[index] >>> 8));
				ret.write((byte) Symbols.EXT_BYTES[index]);
			} else { // Maybe a standard char...
				index = -1;
				for (int j = 0; j < Symbols.STD_ALPHABET.length; j++) {
					if (Symbols.STD_ALPHABET[j] == ch) {
						index = j;
						ret.write((byte) j);
						break;
					}
				}

				// Maybe a Greek Char...
				if (index == -1) {
					for (int j = 0; j < Symbols.GRC_ALPHABET_REMAPPING.length; j++) {
						if (Symbols.GRC_ALPHABET_REMAPPING[j][0] == ch) {
							index = j;
							ch = Symbols.GRC_ALPHABET_REMAPPING[j][1];
							break;
						}
					}

					if (index != -1) {
						for (int j = 0; j < Symbols.STD_ALPHABET.length; j++) {
							if (Symbols.STD_ALPHABET[j] == ch) {
								index = j;
								ret.write((byte) j);
								break;
							}
						}
					} else { // Unknown char replacement
						ret.write((byte) '?');
					}
				}
			}
		}

		return ret.toByteArray();
	}

	/**
	 * 
	 * @param septetBytes
	 * @return
	 */
	private byte[] decodedSeptetsToEncodedSeptets(byte[] septetBytes) {
		BitSet bits = new BitSet();
		for (int i = 0; i < septetBytes.length; i++) {
			for (int j = 0; j < 7; j++) {
				if ((septetBytes[i] & (1 << j)) != 0) {
					bits.set((i * 7) + j);
				}
			}
		}

		int encodedSeptetByteArrayLength = septetBytes.length * 7 / 8
				+ (((septetBytes.length * 7) % 8 != 0) ? 1 : 0);
		byte[] ret = new byte[encodedSeptetByteArrayLength];
		for (int i = 0; i < encodedSeptetByteArrayLength; i++) {
			for (int j = 0; j < 8; j++) {
				ret[i] |= (byte) ((bits.get((i * 8) + j) ? 1 : 0) << j);
			}
		}

		return ret;
	}

	/**
	 * 
	 * @param userData
	 * @param length
	 * @param numOfParts
	 * @param partNum
	 * @param number
	 * @return
	 */
	private String buildPDU(byte[] userData, int length, int numOfParts, int partNum,
			String number) {
		StringBuilder pdu = new StringBuilder("00");
		pdu.append((numOfParts > 1) ? "41" : "01");
		pdu.append("00");
		pdu.append(Integer.toHexString((number.length() & 0xFF) | 0x100).substring(1));
		pdu.append("91");
		for (int i = 0; i < number.length(); i += 2) {
			try {
				pdu.append(number.charAt(i + 1));
			} catch (IndexOutOfBoundsException e) {
				pdu.append("F");
			}
			pdu.append(number.charAt(i));
		}
		pdu.append("0000");

		pdu.append(Integer.toHexString((length & 0xFF) | 0x100).substring(1));

		if (numOfParts > 1) {
			pdu.append("05000377");
			pdu.append(Integer.toHexString((numOfParts & 0xFF) | 0x100).substring(1));
			pdu.append(Integer.toHexString((partNum & 0xFF) | 0x100).substring(1));
		}

		for (int i = 0; i < userData.length; i++) {
			pdu.append(Integer.toHexString((userData[i] & 0xFF) | 0x100).substring(1));
		}

		return pdu.toString().toUpperCase();
	}

	/**
	 * 
	 * @param string
	 * @return
	 * @throws IOException
	 */
	private void write(String string) throws CommPortException {
		logger.debug(">> " + string);
		commPort.writeString(string, StandardCharsets.UTF_8);
	}

	/**
	 * 
	 */
	synchronized void quit() {
		try {
			write("" + SUB);
		} catch (Exception e) {
		}
	}
}
