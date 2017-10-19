package utils;

import java.util.Date;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 */

public class DAQSetup {


	//*fields set from DAQAggregator config files*

	//setup *normalized* name
	private String name;

	//path where snapshots for this setup are stored (root directory path)
	private String snapshotPath;

	//description of this setup
	private String remark;

	//consumption on disk for this setup storage directories
	private String diskUsage;

	//*fields set from pid file with registered DAQAggregator processes*

	//last Aggregator process ID for this setup
	private long lastPid;

	//last Aggregator process status for this setup
	private String processStatus;


	//*fields set from daqview-react-server config file*

	//setup declared but generally not used, therefore masked
	private boolean masked;

	//*other fields*

	//latest snapshot parsed and deserialized as json consumable by clients
	private String latestSnapshot;
	
	//to prevent starting the same setup twice from the GUI (important as the <start> button will be updated some seconds after the actual setup launch)
	private Date lastStartCommandTimestamp;
	
	public DAQSetup(String name){
		this.name = name;
		this.snapshotPath = "";
		this.remark = "";
		this.lastPid = -1;
		this.processStatus = "unknown";
		this.masked = false;
	}


	public String getName() {
		return name;
	}


	public String getSnapshotPath() {
		return snapshotPath;
	}


	public void setSnapshotPath(String snapshotPath) {
		this.snapshotPath = snapshotPath;
	}


	public long getLastPid() {
		return lastPid;
	}


	public void setLastPid(long lastPid) {
		this.lastPid = lastPid;
	}


	public String getProcessStatus() {
		return processStatus;
	}


	public void setProcessStatus(String processStatus) {
		this.processStatus = processStatus;
	}


	public String getRemark() {
		return remark;
	}


	public void setRemark(String remark) {
		this.remark = remark;
	}


	public String getDiskUsage() {
		return diskUsage;
	}


	public void setDiskUsage(String diskUsage) {
		this.diskUsage = diskUsage;
	}


	public boolean isSetupConfigFileDeclared(){
		return !this.snapshotPath.equals("");
	}

	public boolean isSetupPIDRecorded(){
		return this.lastPid!=-1;
	}

	public boolean isSetupRunning(){
		return this.processStatus.equals("running");
	}


	public boolean isMasked() {
		return masked;
	}


	public void setMasked(boolean masked) {
		this.masked = masked;
	}


	public String getLatestSnapshot() {
		return latestSnapshot;
	}


	public void setLatestSnapshot(String latestSnapshot) {
		this.latestSnapshot = latestSnapshot;
	}


	public Date getLastStartCommandTimestamp() {
		return lastStartCommandTimestamp;
	}


	public void setLastStartCommandTimestamp(Date lastStartCommandTimestamp) {
		this.lastStartCommandTimestamp = lastStartCommandTimestamp;
	}

}
