package cc.sferalabs.sfera.drivers.gsm;

class PartialSms {

	private final String[] parts;
	private final int numOfParts;
	private boolean error = false;

	/**
	 * 
	 * @param numOfParts
	 * @throws IllegalArgumentException
	 */
	public PartialSms(int numOfParts) throws IllegalArgumentException {
		if (numOfParts <= 0) {
			throw new IllegalArgumentException("number of parts = " + numOfParts);
		}

		this.numOfParts = numOfParts;
		parts = new String[numOfParts];
	}

	/**
	 * 
	 * @param partNum
	 * @param msg
	 * @param error
	 */
	public void addPart(int partNum, String msg, boolean error) {
		if (partNum < 1 || partNum > numOfParts) {
			error = true;
		}
		this.error = error;
		parts[partNum - 1] = msg;
	}

	/**
	 * 
	 * @return
	 */
	public String getCompleteMessage() {
		StringBuilder ret = new StringBuilder();
		for (String part : parts) {
			if (part == null) {
				return null;
			}
			ret.append(part);
		}

		return ret.toString();
	}

	public boolean hasErrors() {
		return error;
	}
}
