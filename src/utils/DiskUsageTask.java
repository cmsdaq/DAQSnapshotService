package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */

public class DiskUsageTask implements Runnable{

	SetupManager setupManager;
	
	private static final Logger logger = Logger.getLogger(DiskUsageTask.class);
	
	public DiskUsageTask(SetupManager setupManager) {
		this.setupManager = setupManager;
	}
	
	@Override
	public void run() {
		Date tic = new Date();
		
		//act upon a copy of setups and only call setup manager objects just to set values, once the DU jobs have finished
		
		List<DAQSetup> setups = setupManager.getAvailableSetups();
		
		Map<String, String> map = new HashMap<String, String>();
		
		for (DAQSetup setup : setups){
			String result = queryDiskUsage(setup.getSnapshotPath());
			map.put(setup.getName(), result);
		}
		
		setupManager.updateDiskUsage(map);
		
		Date toc = new Date();
		logger.info("Disk usage estimation task for "+map.size()+" setups took "+(toc.getTime()-tic.getTime())+" milliseconds");
	}
	
	private String queryDiskUsage(String setupSnapshotPath) {
		String ret;

		try {
			Process p = Runtime.getRuntime().exec("du -sh "+setupSnapshotPath);

			BufferedReader ips =  new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line;
			String pInfo ="";
			while ((line = ips.readLine()) != null) {
				pInfo+=line; 
			}

			ips.close();


			ret = pInfo.split("/")[0].trim();

			//a valid du response contains at least one char for the quantity and one char for the units
			if (ret.length()<2){
				ret = null;
			}


		}catch(IOException e){
			ret = "?";
			logger.warn("Could not find out disk usage of directory "+setupSnapshotPath);
		}

		return ret;
	}
}
