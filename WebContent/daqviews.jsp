<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"
    %>
<%@ page import="utils.SetupManager"%>
<%@ page import="utils.DAQSetup"%>
<%@ page import="javax.servlet.jsp.PageContext"%>
<%@ page import="javax.servlet.jsp.JspException"%>
<%@ page import="java.util.Properties"%>
    
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
	Properties props = (Properties)getServletContext().getAttribute("properties");
	
	String baseLink = props.getProperty("linkToDaqviewApache");


%>

<body bgcolor="#DDDDAA">
<center><h1>Welcome to DAQView instances index</h1></center> 

<br><br>

<table width="95%">
<tr><th>Setup</th><th>Remark</th><th>DAQAggr. status</th><th>Last DAQAggr. pid</th><th>Action</th><th>Snapshot DU<th>Link</th><th>Notes</th></tr>

<% 

/*View preprocessing: for each setup, it preprocesses data and inserts a row into the table*/

//loop over all setups: sets values based on each DAQSetup and introduces a new row, where applicable (each row is linked to a daqview for a specific setup)
boolean evenRow = true; //first row of table is considered "index 0", therefore even
String rowColor = "#DDDDBB";
for (DAQSetup ds: setupManager.getAvailableSetups()){
	
	String statusMsg = "";
	String link_all = "";
	String link_fb = "";
	String link_fff = "";
	String links = ""; //concatenation of all links with line breakers
	String pidAsString = "";
	String buttonTag = "start";
	String notes = "";
	
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
			
			if (ds.getDiskUsage() == null){
				notes = "No snapshot data found: expected, as this setup has never run";
			}

		}else{
			pidAsString = String.valueOf(ds.getLastPid());
			if (ds.isSetupRunning()){
				statusMsg = "running";
				buttonTag = "stop";
				link_all = "<a target=\"_blank\" href=\""+baseLink+"index.html?setup="+ds.getName()+"\">DAQView - all</a>";
				link_fb = "<a target=\"_blank\"href=\""+baseLink+"index_fb.html?setup="+ds.getName()+"\">DAQView - fb</a>";
				link_fff = "<a target=\"_blank\"href=\""+baseLink+"index_fff.html?setup="+ds.getName()+"\">DAQView - fff</a>";
				links = link_all+"<br/>"+link_fb+"<br/>"+link_fff;
				if (ds.getDiskUsage() == null){
					//in this case do not hide DAQView links, as it may be just the du command who took longer and data might exist (might happen at startup, when the snapshot dir is already huge)
					notes = "Running but no snapshot data seem to have been produced yet: be patient if you just started it, otherwise investigate DAQAggregator logs";
				}else{
					notes = "Realtime views";
				}
			}else{
				statusMsg = "stopped";
				link_all = "<a target=\"_blank\"href=\""+baseLink+"index.html?setup="+ds.getName()+"\">DAQView - all</a>";
				link_fb = "<a target=\"_blank\"href=\""+baseLink+"index_fb.html?setup="+ds.getName()+"\">DAQView - fb</a>";
				link_fff = "<a target=\"_blank\"href=\""+baseLink+"index_fff.html?setup="+ds.getName()+"\">DAQView - fff</a>";
				links = link_all+"<br/>"+link_fb+"<br/>"+link_fff;
				
				if (ds.getDiskUsage() == null){
					//in this case do not hide DAQView links, as it may be just the du command who took longer and data might exist (might happen at startup, when the snapshot dir is already huge)
					notes = "Has run in the past, but no snapshot data seem to have been produced";
				}else{
					notes = "Not running anymore, but DAQView can still be used to go back in time";
				}
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


<tr align="center" bgcolor="<%=rowColor%>" height=30>
<td><%=ds.getName()%></td>
<td><%=ds.getRemark()%></td>
<td><%=statusMsg%></td>
<td><%=pidAsString%></td>
<td><form method="post" action="${pageContext.request.contextPath}/managesetup"><input type="submit" name="click_<%=ds.getName()%>" value="<%=buttonTag%>" /></form></td>
<td><%=ds.getDiskUsage() != null ? ds.getDiskUsage() : "N/A"%></td>
<td><%=links%></td>
<td width = "15%"><%=notes%></td>
</tr>


  <% 
        } //end loop over all setups
    %>

</table>

</body>
</html>