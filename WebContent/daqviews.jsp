<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"
    %>
<%@ page import="utils.SetupManager"%>
<%@ page import="utils.DAQSetup"%>
    
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta http-equiv="refresh" content="7">
<title>DAQViews index</title>
</head>

<% 

 /*Data initialization*/

	SetupManager setupManager  = (SetupManager)getServletContext().getAttribute("setupManager");
	String baseLink = (String)getServletContext().getAttribute("linktodaqview");
%>

<body bgcolor="#DDDDAA">
<center><h1>Welcome to DAQView instances index</h1></center> 

<br><br>

<table width="75%">
<tr><th>Setup</th><th>Remark</th><th>DAQAggr. status</th><th>Last DAQAggr. pid</th><th>Action</th><th>Snapshot DU<th>Link</th></tr>

<% 

/*View preprocessing: for each setup, it preprocesses data and inserts a row into the table*/

//loop over all setups: sets values based on each DAQSetup and introduces a new row, where applicable (each row is linked to a daqview for a specific setup)
boolean evenRow = true; //first row of table is considered "index 0", therefore even
String rowColor = "#DDDDBB";
for (DAQSetup ds: setupManager.getAllSetups()){
	
	String statusMsg = "";
	String link_all = "";
	String link_fb = "";
	String link_fff = "";
	String links = ""; //concatenation of all links with line breakers
	String pidAsString = "";
	String buttonTag = "start";
	
	if (!ds.isSetupConfigFileDeclared()){
		//someone has removed DAQAggregator config file after putting it, breaking the pointer to any real-time or historical snapshots
		continue; //do not include DAQView entry for such setups
	}else{
		
		if (ds.isMasked()){
			//setup was found declared and there is path to snapshots (whether historical, realtime or non-existent), but marked as masked
			continue; //do not include DAQView entry for such setups
		}
		
		if (!ds.isSetupPIDRecorded()){
			statusMsg = "never ran";
			link_all = "N/A";
			links = link_all;
			pidAsString = "N/A";
		}else{
			pidAsString = String.valueOf(ds.getLastPid());
			if (ds.isSetupRunning()){
				statusMsg = "running";
				buttonTag = "stop";
				link_all = "<a href=\""+baseLink+"index.html?setup="+ds.getName()+"\">Realtime DAQView - all</a>";
				link_fb = "<a href=\""+baseLink+"index_fb.html?setup="+ds.getName()+"\">Realtime DAQView - fb</a>";
				link_fff = "<a href=\""+baseLink+"index_fff.html?setup="+ds.getName()+"\">Realtime DAQView - fff</a>";
				links = link_all+"<br/>"+link_fb+"<br/>"+link_fff;
			}else{
				statusMsg = "stopped";
				link_all = "<a href=\""+baseLink+"index.html?setup="+ds.getName()+"\">Stale DAQView - all</a>";
				link_fb = "<a href=\""+baseLink+"index_fb.html?setup="+ds.getName()+"\">Stale DAQView - fb</a>";
				link_fff = "<a href=\""+baseLink+"index_fff.html?setup="+ds.getName()+"\">Stale DAQView - fff</a>";
				links = link_all+"<br/>"+link_fb+"<br/>"+link_fff;
			}
		}
		if (evenRow){
			rowColor = "#F5F2F2";
		}else{
			rowColor = "#e6e6e6";
		}
		
		evenRow = !evenRow; //invert flag
		
	}
%>

<!--  <form method="post" action="" enctype="multipart/form-data"> -->
<tr align="center" bgcolor="<%=rowColor%>" height=30>
<td><%=ds.getName()%></td>
<td><%=ds.getRemark()%></td>
<td><%=statusMsg%></td>
<td><%=pidAsString%></td>
<td><input type="hidden" name="cdaq" value="1"  /><input type="submit" name="submit" value="<%=buttonTag%>" /></td>
<td><%=ds.getDiskUsage()%></td>
<td><%=links%></td>
</tr>
<!-- </form> -->

  <% 
        } //end loop over all setups
    %>

</table>

</body>
</html>