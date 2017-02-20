package utils;

import java.util.Date;

import org.apache.log4j.Logger;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */

public class SetupDetectionTask implements Runnable{

	SetupManager setupManager;
	
	private static final Logger logger = Logger.getLogger(SetupDetectionTask.class);
	
	public SetupDetectionTask(SetupManager setupManager) {
		this.setupManager = setupManager;
	}
	
	@Override
	public void run() {
		Date tic = new Date();
		//acts entirely upon the setup manager object and the contained setup objects themselves
		setupManager.detectSetups();
		Date toc = new Date();

		logger.info("Setup detection task took "+(toc.getTime()-tic.getTime())+" milliseconds");
	}
}
