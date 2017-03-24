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
	private Map<String, DAQSetup> setups; //never flush this map

	private String configFilesDirPath; //this is the DAQAggregator config file

	private String pidLogFile;

	private Set<String> maskedSetups; //setups with missing DAQAggr config files, or without valid ".properties" suffix

	private Set<String> revisitedSetups; //contains only setups encountered in current iteration (flushed at each iteration, unlike the map of setups)

	private String startScript; //script to start DAQAggregator processes and catch their pids
	
	private static final Logger logger = Logger.getLogger(SetupManager.class);


	public SetupManager (Properties props){
		this.configFilesDirPath = props.getProperty("daqAggregatorConfigFilesDirPath");
		this.pidLogFile = props.getProperty("daqAggregatorPidLogFile");
		this.startScript = props.getProperty("startScript");
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

		//setting masking property for all setups
		for (String sName : this.setups.keySet()){
			if (this.maskedSetups.contains(sName)){
				this.setups.get(sName).setMasked(true);
			}else{
				this.setups.get(sName).setMasked(false);
			}
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

					//DAQAggregator properties file for this setup
					Properties DaqAggregatorInfo = Helpers.loadProps(f.getAbsolutePath());

					String setupName = f.getName().substring(0, f.getName().indexOf(".")); //takes setup name from config file name
					setupName = setupName.toLowerCase().trim(); //normalization to be used with map/set
					String setupSnapshotPath = DaqAggregatorInfo.getProperty("persistence.snapshot.dir");
					String setupRemark = DaqAggregatorInfo.getProperty("remark");

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

	/**Sets disk usage field at all setup objects (it should not include the actual computation)*/
	public synchronized void updateDiskUsage(Map <String, String> results){ //thread safety!
		//key: setup name, value: DU descriptive message

		for (Map.Entry<String, String> e: results.entrySet()){
			this.setups.get(e.getKey()).setDiskUsage(e.getValue());
		}
	}

	/**Sets path to latest snapshot at all setup objects (it should not include the actual computation)*/
	public synchronized void updateLatestSnapshot(Map <String, String> results){ //thread safety!
		//key: setup name, value: path to file smile

		for (Map.Entry<String, String> e: results.entrySet()){
			if (e.getValue() != null){
				this.setups.get(e.getKey()).setLatestSnapshot(e.getValue());
			}
		}
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

	/**Method to ensure atomicity of the three-step setup discovery (background must call this method only)*/
	public synchronized void detectSetups(){ //thread safety!
		maskedSetups = new HashSet<String>();
		revisitedSetups = new HashSet<String>();
		scanDeclaredConfigurations();
		scanDataAggregationProcesses();
		setMaskedSetups();
	}

	/**Method for listing of all setups, irrespectively of status*/
	public List<DAQSetup> getAvailableSetups(){
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

	private String getSetupInfoString(){
		String ret = "";

		ret+="*Setups discovered at "+(new Date()).toString()+"*\n";
		ret+="---------------------------------------\n";
		ret+="Setup name \t\t\t\t Last DAQAgg PID \t\t\t\t Last DAQAgg status \t\t\t\t Link to source\n\n";

		for (DAQSetup ds : getAvailableSetups()){
			ret+=ds.getName()+"\t\t\t\t"+ds.getLastPid()+"\t\t\t\t"+ds.getProcessStatus()+"\t\t\t\t"+ds.getSnapshotPath()+"\n";
		}

		ret+="---------------------------------------\n";

		return ret;
	}

	public synchronized boolean startSetupByName(String name, Date callerTimestamp){ //thread safety!
		logger.info("Starting setup: "+name);
		
		boolean success = false;
		
		boolean acceptLaunch = true;
		
		String previousStartCall;
		
		if (this.setups.get(name).getLastStartCommandTimestamp() != null){
			previousStartCall = this.setups.get(name).getLastStartCommandTimestamp().toString();
			
			//aborting if less than 12 seconds (daq setup detection time + GUI refresh rate + epsilon)
			if ((callerTimestamp.getTime() - this.setups.get(name).getLastStartCommandTimestamp().getTime()) < 12000){
				acceptLaunch = false;
			}
			
		}else{
			previousStartCall = "never";
		}
		
		logger.info("-previous start method call at: "+previousStartCall);
		
		if (!acceptLaunch){
			logger.info("Attempt to start a setup after a very recent previous start command: aborting...please let the system take its time before you click the start button twice!");
			return success;
		}else{
			this.setups.get(name).setLastStartCommandTimestamp(callerTimestamp);
		}

		String DAQAggregatorConfigFile = this.configFilesDirPath+"/"+name+".DAQAggregator.properties";

		String DAQAggregatorBinary = Helpers.loadProps(DAQAggregatorConfigFile).getProperty("daqaggregator");
		
		String DAQAggregatorLogfile = Helpers.loadProps(DAQAggregatorConfigFile).getProperty("logfile");

		try{
			//start an aggregator process with a given binary and a configuration file
			
			//wrap in process builder
			ProcessBuilder builder = new ProcessBuilder("sh", this.startScript, DAQAggregatorBinary, DAQAggregatorConfigFile, name, DAQAggregatorLogfile);
			builder.start();
			
			logger.info("Started setup: "+name+" with executable: "+DAQAggregatorBinary+" (should be picked up by front-end in a while)");
			success = true;
		}catch(RuntimeException|IOException e){
			logger.error("Failed to start a setup");
			e.printStackTrace();
		}
		
		return success;
	}

	public synchronized boolean stopSetupByName(String name){ //thread safety!
		logger.info("Stopping setup: "+name);
		boolean success = false;

		try{
			
			//wrap in process builder
			ProcessBuilder builder = new ProcessBuilder("kill", String.valueOf(this.setups.get(name).getLastPid()));
			builder.start();
			
			logger.info("Stopped setup: "+name+" (should be picked up by front-end in a while)");
			success = true;
		}catch(RuntimeException|IOException e){
			logger.error("Failed to stop a setup");
			e.printStackTrace();
		}

		return success;
	}
}
