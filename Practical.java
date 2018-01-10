package net.floodlightcontroller.practical;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.OFVersion;

import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U16;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Practical implements IFloodlightModule, IOFMessageListener {

	protected static Logger log = LoggerFactory.getLogger(Practical.class);
    	protected IFloodlightProviderService floodlightProvider;
    	protected HashMap<Long,Integer> macToPort;

	@Override
	public String getName() {
		return "practical";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
    		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
    		l.add(IFloodlightProviderService.class);
    		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
    		log = LoggerFactory.getLogger(Practical.class);
    		macToPort = new HashMap<Long,Integer>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
    		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

	@Override
	/* Handle a packet message - called every time a packet is received*/
   	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {

        	//Get Packet-In message
        	OFPacketIn pi = (OFPacketIn) msg;

        	Match match = pi.getMatch();

		MacAddress sourceMac = match.get(MatchField.ETH_SRC);
		//String readableSourceMac = sourceMac.toString();
		//log.debug("Packet with source MAC Address: {} came in port : {}", readableSourceMac, pi.getInPort());

		//Flood Packet to all ports
		writePacketToPort(sw, pi, OFPort.FLOOD.getShortPortNumber(), cntx);

		//Print debug message
		log.debug("Flooding packet to all ports");

		//Allow Floodlight to continue processing the packet
        	return Command.CONTINUE;
    	}

    /* Write a packet out to a specific port */
    public void writePacketToPort (IOFSwitch sw, OFPacketIn pi, int outPort,  FloodlightContext cntx) {
        //build packout
	OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
	pob.setBufferId(pi.getBufferId());
	pob.setInPort((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)));
       	pob.setXid(pi.getXid());

	//set actions
	OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
        actionBuilder.setPort(OFPort.ofInt(outPort));
	pob.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

	// set data if it is included in the packetin
        if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
            byte[] packetData = pi.getData();
            pob.setData(packetData);
        }

	sw.write(pob.build());
    }

    /* Install a flow-mod with given parameters */
    private void installFlowMod(IOFSwitch sw, OFPacketIn pi, Match match, int outPort, int idleTimeout, int hardTimeout, FloodlightContext cntx) {
	OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
	fmb.setBufferId(OFBufferId.of(-1));
        fmb.setOutPort(OFPort.ofInt(outPort));
        fmb.setMatch(match);
        fmb.setIdleTimeout(idleTimeout);
        fmb.setHardTimeout(hardTimeout);
        fmb.setPriority(100);

	//add action
	OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
	actionBuilder.setPort(OFPort.ofInt(outPort));
	fmb.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

        sw.write(fmb.build());
    }

    private OFMessage createHubFlowMod(IOFSwitch sw, OFMessage msg) {
        OFPacketIn pi = (OFPacketIn) msg;
        OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
        fmb.setBufferId(pi.getBufferId())
        .setXid(pi.getXid());

        // set actions
        OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
        actionBuilder.setPort(OFPort.FLOOD);
        fmb.setActions(Collections.singletonList((OFAction) actionBuilder.build()));

        return fmb.build();
    }



	@Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't provide any services, return null
        return null;
    }
}
