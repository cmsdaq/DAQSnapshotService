package utils;

import org.apache.log4j.Logger;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */

public class SetupDetectionTask implements Runnable{

	SetupManager setupDetector;
	
	private static final Logger logger = Logger.getLogger(SetupDetectionTask.class);
	
	public SetupDetectionTask(SetupManager setupDetector) {
		this.setupDetector = setupDetector;
	}
	
	@Override
	public void run() {
		setupDetector.detectSetups();
		logger.debug("Setup detection task finished");
	}
}
