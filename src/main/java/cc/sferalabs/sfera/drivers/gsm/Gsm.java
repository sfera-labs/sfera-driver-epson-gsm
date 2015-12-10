package cc.sferalabs.sfera.drivers.gsm;

import cc.sferalabs.sfera.core.Configuration;
import cc.sferalabs.sfera.drivers.Driver;
import cc.sferalabs.sfera.io.comm.CommPort;
import cc.sferalabs.sfera.io.comm.CommPortException;

public class Gsm extends Driver {

	private CommPort commPort;
	private int stateErrorCount;
	private String countryCode;
	private CommunicationHandler commHandler;
	private boolean ready = false;

	public Gsm(String id) {
		super(id);
	}

	@Override
	protected boolean onInit(Configuration config) throws InterruptedException {
		ready = false;
		stateErrorCount = 0;

		String portName = config.get("serial_port", null);
		if (portName == null) {
			log.error("Serial port not specified in configuration");
			return false;
		}

		try {
			commPort = CommPort.open(portName);
			int baudRate = config.get("baud_rate", 115200);
			int dataBits = config.get("data_bits", 8);
			int stopBits = config.get("stop_bits", 1);
			int parity = config.get("parity", CommPort.PARITY_NONE);
			commPort.setParams(baudRate, dataBits, stopBits, parity,
					CommPort.FLOWCONTROL_RTSCTS);

			countryCode = config.get("country_code", null);
			commHandler = new CommunicationHandler(this, commPort, countryCode, log);
			commPort.setListener(commHandler);

			while (commPort.getAvailableBytesCount() > 0) {
				// let the listener consume the bytes
				Thread.sleep(500);
			}
			commHandler.clearMessages();

		} catch (CommPortException e) {
			log.error("Error initializing serial port", e);
			return false;
		}

		try {
			String pin = config.get("pin", null);
			String serviceCenterAddr = config.get("service_center", null);
			commHandler.initGsm(pin, serviceCenterAddr);
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error initializing GSM modem", e);
			return false;
		}

		ready = true;
		return true;
	}

	@Override
	protected boolean loop() throws InterruptedException {
		try {
			if (!commHandler.checkState()) {
				stateErrorCount++;
			} else {
				stateErrorCount = 0;
			}

			if (stateErrorCount > 5) {
				throw new Exception("too many errors");
			}

		} catch (InterruptedException e) {
			throw e;

		} catch (Exception e) {
			log.error("Exception in loop", e);
			return false;
		}

		Thread.sleep(10000);
		return true;
	}

	/**
	 * 
	 * @param to
	 * @param body
	 * @return
	 */
	public boolean send(long to, String body) {
		return send(Long.toString(to), body);
	}

	/**
	 * 
	 * @param to
	 * @param body
	 * @return
	 */
	public boolean send(String to, String body) {
		if (!ready) {
			return false;
		}
		return commHandler.send(to, body);
	}

	@Override
	protected void onQuit() {
		ready = false;
		if (commHandler != null) {
			commHandler.quit();
		}
		try {
			commPort.close();
		} catch (Exception e) {
		}
	}
}
