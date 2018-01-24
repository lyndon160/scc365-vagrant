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
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.IPv6;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.ICMP;

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

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.ICMPv4Type;
import org.projectfloodlight.openflow.types.ICMPv4Code;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U16;

import org.projectfloodlight.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Practical implements IFloodlightModule, IOFMessageListener {

	protected static Logger log = LoggerFactory.getLogger(Practical.class);
	protected IFloodlightProviderService floodlightProvider;
	protected HashMap<MacAddress,Integer> macToPort;

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
		macToPort = new HashMap<MacAddress,Integer>();
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

		//Flood Packet to all ports
		writePacketToPort(sw, pi, OFPort.FLOOD.getPortNumber());

		//Print debug message
		log.debug("Flooding packet to all ports");

		//Allow Floodlight to continue processing the packet
		return Command.CONTINUE;
	}

	/* Write a packet out to a specific port */
	public void writePacketToPort (IOFSwitch sw, OFPacketIn pi, int outPort) {
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
	private void installFlowMod(IOFSwitch sw, OFPacketIn pi, Match match, int outPort, int idleTimeout, int hardTimeout) {
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


	/**
	* Create a match containing Ethernet addresses and type; Vlan IDs; IPv4/IPv6 addresses and proto; TCP/UDP ports; ICMP type and codes.
	*
	* @param sw, the switch on which the packet was received
	* @param inPort, the ingress switch port on which the packet was received
	* @return a composed Match object based on the provided information
	*/
	protected Match createMatchFromPacket(IOFSwitch sw, OFPacketIn pi) {
		Ethernet eth = new Ethernet();
		eth.deserialize(pi.getData(), 0, pi.getTotalLen());

		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();

		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.IN_PORT, (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)));

		//mac
		mb.setExact(MatchField.ETH_SRC, srcMac).setExact(MatchField.ETH_DST, dstMac);

		//vlan
		if (!vlan.equals(VlanVid.ZERO)) {
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
		}

		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIp = ip.getSourceAddress();
			IPv4Address dstIp = ip.getDestinationAddress();

			//ipv4
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
			.setExact(MatchField.IPV4_SRC, srcIp)
			.setExact(MatchField.IPV4_DST, dstIp);

			/*
			* Take care of the ethertype if not included earlier,
			* since it's a prerequisite for transport ports.
			*/
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);

			//transport
			if (ip.getProtocol().equals(IpProtocol.TCP)) {
				TCP tcp = (TCP) ip.getPayload();
				mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.TCP_SRC, tcp.getSourcePort())
				.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
			} else if (ip.getProtocol().equals(IpProtocol.UDP)) {
				UDP udp = (UDP) ip.getPayload();
				mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
				.setExact(MatchField.UDP_SRC, udp.getSourcePort())
				.setExact(MatchField.UDP_DST, udp.getDestinationPort());
			} else if (ip.getProtocol().equals(IpProtocol.ICMP)){
				ICMP icmp = (ICMP) ip.getPayload();
				mb.setExact(MatchField.IP_PROTO, IpProtocol.ICMP)
				.setExact(MatchField.ICMPV4_TYPE, ICMPv4Type.of(icmp.getIcmpType()))
				.setExact(MatchField.ICMPV4_CODE, ICMPv4Code.of(icmp.getIcmpCode()));
			}
		} else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
		} else if (eth.getEtherType() == EthType.IPv6) {
			IPv6 ip = (IPv6) eth.getPayload();
			IPv6Address srcIp = ip.getSourceAddress();
			IPv6Address dstIp = ip.getDestinationAddress();

			//ipv6
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv6)
			.setExact(MatchField.IPV6_SRC, srcIp)
			.setExact(MatchField.IPV6_DST, dstIp);


			/*
			* Take care of the ethertype if not included earlier,
			* since it's a prerequisite for transport ports.
			*/
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);

			//transport
			if (ip.getNextHeader().equals(IpProtocol.TCP)) {
				TCP tcp = (TCP) ip.getPayload();
				mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
				.setExact(MatchField.TCP_SRC, tcp.getSourcePort())
				.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
			} else if (ip.getNextHeader().equals(IpProtocol.UDP)) {
				UDP udp = (UDP) ip.getPayload();
				mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
				.setExact(MatchField.UDP_SRC, udp.getSourcePort())
				.setExact(MatchField.UDP_DST, udp.getDestinationPort());
			}
		}
		return mb.build();
	}



	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// We don't provide any services, return null
		return null;
	}
}
