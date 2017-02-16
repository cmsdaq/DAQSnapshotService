package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */

public class SetupManager{

	/**holds setups by *normalized* name
	 */
	private Map<String, DAQSetup> setups; //it only makes sense to flush this map if we want to flush historical data for a setup

	private String configFilesDirPath;

	private String pidLogFile;

	private Set<String> maskedSetups; //setups with missing DAQAggr config files, or without valid ".properties" suffix

	private Set<String> revisitedSetups; //contains only setups encountered in current iteration (flushed at each iteration, unlike the map of setups)

	private static final Logger logger = Logger.getLogger(SetupManager.class);


	public SetupManager (String configFilesDirPath, String pidLogFile, String serverPropertiesFile){
		this.configFilesDirPath = configFilesDirPath;
		this.pidLogFile = pidLogFile;
		setups = new HashMap<String, DAQSetup>();
		maskedSetups = new HashSet<String>();
		revisitedSetups = new HashSet<String>();
	}

	/**Scans for setups which are masked. These setups will be ignored.
	 * Once a DAQAggregator config file is removed from directory or its name suffix changes to anything other than ".properties", the setup is marked as masked*/
	private void setMaskedSetups(){

		//the "anything than .properties suffix" condition is examined upon reading the DAQAggregator config files in method scanDeclaredConfigurations()

		//examining setups which used to exist but not anymore (not revisited in this iteration)
		for (String sName : this.setups.keySet()){
			if (!this.revisitedSetups.contains(sName)){
				this.maskedSetups.add(sName);
			}
		}
		
		for (String sName : this.setups.keySet()){
			if (this.maskedSetups.contains(sName)){
				this.setups.get(sName).setMasked(true);
			}else{
				this.setups.get(sName).setMasked(false);
			}
		}
	}

	/**Flushes setup masked value*/
	private void flushSetupMasking(){

		for (String sName : this.setups.keySet()){
			this.setups.get(sName).setMasked(false);
		}
	}

	/**Scans for DAQ setups whose DAQAggregator configuration files have been declared in the config file directory*/
	private void scanDeclaredConfigurations(){

		try{

			File dir = new File(this.configFilesDirPath);

			if (dir.isDirectory()){
				File [] configs = dir.listFiles();

				for (File f : configs){
					//skipping duplicate files that text editors may put in the directory as backups
					if (f.getName().endsWith("~")){
						continue;
					}
					if (!f.getName().endsWith(".properties")){
						String setupName = f.getName().substring(0, f.getName().indexOf(".")); //takes setup name from config file name
						setupName = setupName.toLowerCase().trim(); //normalization to be used with map/set
						this.maskedSetups.add(setupName);
						continue;
					}

					String setupName = f.getName().substring(0, f.getName().indexOf(".")); //takes setup name from config file name
					setupName = setupName.toLowerCase().trim(); //normalization to be used with map/set
					String setupSnapshotPath = Helpers.loadProps(f.getAbsolutePath()).getProperty("persistence.snapshot.dir");
					String setupRemark = Helpers.loadProps(f.getAbsolutePath()).getProperty("remark");

					//still valid setup revisited
					this.revisitedSetups.add(setupName);

					if (this.setups.containsKey(setupName)){
						//do not add a new DAQSetup object, if already existing, only update, if applicable

						//update values
						if (this.setups.get(setupName).getSnapshotPath().equals(setupSnapshotPath) && this.setups.get(setupName).getRemark().equals(setupRemark)){
							continue;
						}else{
							this.setups.get(setupName).setSnapshotPath(setupSnapshotPath);
							this.setups.get(setupName).setRemark(setupRemark);
						}

					}else{
						//adding a new setup, because DAQAggregator config file was found (this does not mean an aggregator for this setup is running or has ever run)
						DAQSetup daqSetup = new DAQSetup(setupName);
						daqSetup.setSnapshotPath(setupSnapshotPath);
						daqSetup.setRemark(setupRemark);
						daqSetup.setDiskUsage(queryDiskUsage(setupSnapshotPath));

						this.setups.put(setupName, daqSetup);
					}
				}

			}else{
				throw new RuntimeException();
			}

		}catch(RuntimeException e){
			logger.error("Failed to scan config files directory");
			e.printStackTrace();
		}
	}

	private void updateDiskUsage(){
		for (Map.Entry<String, DAQSetup> setupEntry: this.setups.entrySet()){
			setupEntry.getValue().setDiskUsage(queryDiskUsage(setupEntry.getValue().getSnapshotPath()));
		}
	}

	private String queryDiskUsage(String setupSnapshotPath) {
		String ret = "";

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


		}catch(IOException e){
			ret = "?";
			logger.warn("Could not find out disk usage of directory "+setupSnapshotPath);
		}

		return ret;
	}

	/**Scans all indexed-by-setupName Aggregator processes and discovers their process state*/
	private void scanDataAggregationProcesses(){

		try{

			/**The pid file is appended from top to bottom and Properties class is based on hashmaps.
			 * Thanks to this, properties will always contain one entry per setup, the newest one.
			 * */
			Properties setupEntries = Helpers.loadProps(this.pidLogFile);

			for (String setupNameForNewestPid : setupEntries.stringPropertyNames()){
				//setupNameForNewestPid is the string as in the pid log file (do not normalize it, only use normalized copies when accessing map/set by setup name)

				/**if a setup mentioned in pid log is not contained in the map of config files (which should never normally happen), there is no reason to examine it*/
				if (this.setups.containsKey(setupNameForNewestPid.toLowerCase().trim())){

					long pid = Long.parseLong(setupEntries.getProperty(setupNameForNewestPid)); //do not normalize setup name, as it comes from pid log file

					this.setups.get(setupNameForNewestPid.toLowerCase().trim()).setLastPid(pid);

					//discover process status by this pid
					Process p = Runtime.getRuntime().exec("ps -p "+pid);

					BufferedReader ips =  new BufferedReader(new InputStreamReader(p.getInputStream()));

					String line;
					String pInfo ="";
					while ((line = ips.readLine()) != null) {
						pInfo+=line; 
					}

					ips.close();

					if (pInfo.contains(Long.toString(pid))&&pInfo.contains("java")){
						this.setups.get(setupNameForNewestPid.toLowerCase().trim()).setProcessStatus("running");
					}else{
						this.setups.get(setupNameForNewestPid.toLowerCase().trim()).setProcessStatus("not_running");
					}
				}
			}

		}catch(RuntimeException|IOException e){
			logger.error("Failed to scan Aggregator processes");
			e.printStackTrace();
		}
	}

	/**Method to ensure atomicity of the three-step setup discovery (background must call this)*/
	public void detectSetups(){
		maskedSetups = new HashSet<String>();
		revisitedSetups = new HashSet<String>();
		scanDeclaredConfigurations();
		scanDataAggregationProcesses();
		setMaskedSetups();
	}

	/**Method for listing of all setups, irrespectively of status*/
	public List<DAQSetup> getAllSetups(){
		List<DAQSetup> list = new ArrayList<DAQSetup>();
		for (String sName : this.setups.keySet()){
			list.add(this.setups.get(sName));
		}
		return list;
	}

	/**Method to get one setup, if existing, or null otherwise*/
	public DAQSetup getSetupByName(String term){
		term = term.toLowerCase().trim(); //normalization
		if (this.setups.containsKey(term)){
			return this.setups.get(term);
		}else{
			return null;
		}
	}

	public String getSetupInfoString(){
		String ret = "";

		ret+="*Setups discovered at "+(new Date()).toString()+"*\n";
		ret+="---------------------------------------\n";
		ret+="Setup name \t\t\t\t Last DAQAgg PID \t\t\t\t Last DAQAgg status \t\t\t\t Link to source\n\n";

		for (DAQSetup ds : getAllSetups()){
			ret+=ds.getName()+"\t\t\t\t"+ds.getLastPid()+"\t\t\t\t"+ds.getProcessStatus()+"\t\t\t\t"+ds.getSnapshotPath()+"\n";
		}

		ret+="---------------------------------------\n";

		return ret;
	}

	public boolean startSetup(String name){
		boolean success = false;
		//get daq, file and start it


		return success;
	}

	public boolean stopSetup(String name   ){
		boolean success = false;



		return success;
	}
}
