/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.unicastdhcp;
//start
import com.google.common.collect.ImmutableSet;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.Ethernet;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;

import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;

import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;

import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;

import org.onosproject.net.host.HostService;

import org.onosproject.net.topology.TopologyService;

import java.util.Set;
import java.util.HashMap;

//end

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sample Network Configuration Service Application */
@Component(immediate = true)
public class AppComponent {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final NameConfigListener cfgListener = new NameConfigListener();
  private final ConfigFactory factory =
      new ConfigFactory<ApplicationId, NameConfig>(
          APP_SUBJECT_FACTORY, NameConfig.class, "UnicastDhcpConfig") {
        @Override
        public NameConfig createConfig() {
          return new NameConfig();
        }
      };

	private ApplicationId appId;

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected NetworkConfigRegistry cfgService;

	@Reference(cardinality = ReferenceCardinality.MANDATORY)
	protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    private DHCPRoutingProcessor processor = new DHCPRoutingProcessor();

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;
    protected FlowObjectiveService flowObjectiveService2;


    private static ConnectPoint deviceConnectPoint = null;

  @Activate
  protected void activate() {
    appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
    cfgService.addListener(cfgListener);
    cfgService.registerConfigFactory(factory);
	packetService.addProcessor(processor,PacketProcessor.director(2));
    log.info("Started");
	
//dhcp diiscoverr->ppacketiin
	TrafficSelector.Builder selector =  DefaultTrafficSelector.builder()
		.matchEthType(Ethernet.TYPE_IPV4)
		.matchIPProtocol(IPv4.PROTOCOL_UDP)
		.matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
		.matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
	packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);
//dhcp offer->ppacketin
	selector =  DefaultTrafficSelector.builder()
		.matchEthType(Ethernet.TYPE_IPV4)
		.matchIPProtocol(IPv4.PROTOCOL_UDP)
		.matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
		.matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
	packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);


  }
	private void removePacketRule(){
		//dhcp diiscoverr->ppacketiin
		TrafficSelector.Builder selector =  DefaultTrafficSelector.builder()
			.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPProtocol(IPv4.PROTOCOL_UDP)
			.matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
			.matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
		packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);
	//dhcp offer->ppacketin
		selector =  DefaultTrafficSelector.builder()
			.matchEthType(Ethernet.TYPE_IPV4)
			.matchIPProtocol(IPv4.PROTOCOL_UDP)
			.matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
			.matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));
		packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);

	
	}
  @Deactivate
  protected void deactivate() {
    cfgService.removeListener(cfgListener);
	packetService.removeProcessor(processor);
	processor = null;
    cfgService.unregisterConfigFactory(factory);
	removePacketRule();
    log.info("Stopped");
  }

  private class NameConfigListener implements NetworkConfigListener {
    @Override
    public void event(NetworkConfigEvent event) {
      if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
          && event.configClass().equals(NameConfig.class)) {
        NameConfig config = cfgService.getConfig(appId, NameConfig.class);
        if (config != null) {
          log.info("DHCP server is at {}", config.name());
			deviceConnectPoint = ConnectPoint.deviceConnectPoint(config.name());
//			log.info(deviceConnectPoint.deviceId().toString());
//			log.info(deviceConnectPoint.port().toString());
        }
		else{
			log.info("dafsdfaf");
		}
      }
    }
  }
	private class DHCPRoutingProcessor implements PacketProcessor{
		@Override
		public void process(PacketContext context){
			if(context.isHandled()){
				return;
			}

	//get the packet start from layer2
			InboundPacket pkt = context.inPacket();
			Ethernet ethPkt = pkt.parsed();

			HostId dstId = HostId.hostId(ethPkt.getDestinationMAC());
			//get the hoost with the specified identifier;
			Host dst = hostService.getHost(dstId);

			if(ethPkt.isBroadcast()){
				log.info("broadcast");
				findPathAndFlowmod(context,pkt,ethPkt,dst,true);
			}
			else{
				log.info("DHCP unicast");		
				findPathAndFlowmod(context,pkt,ethPkt,dst,false);
			}
		}


	}

	private void findPathAndFlowmod(PacketContext context, InboundPacket pkt, Ethernet ethPkt,Host dst,boolean isBroadcast){
		log.info("start find path and flowmod");
		if( isBroadcast == true){
			log.info("broadcast here");
			log.info(pkt.receivedFrom().deviceId().toString());
			log.info(deviceConnectPoint.deviceId().toString());
			if(pkt.receivedFrom().deviceId().equals(deviceConnectPoint.deviceId())){
				log.info("destination is on same switch");;
				installRule(context,deviceConnectPoint.port());
				return;
			}
			log.info("not at same swwwitch");
		}
		else{
			if( pkt.receivedFrom().deviceId().equals(dst.location().deviceId())){
				log.info("not  broadcast heere");
				//check although at same device,but  the inport and outport are  distinct
				if(!context.inPacket().receivedFrom().port().equals(dst.location().port())){
					log.info("UUnicast message is on same switch");
					installRule(context,dst.location().port());
				}
				return;
			}
		}

//find the path to destinationn
		Set<Path> paths;
		if(isBroadcast == true){
			//is brooadcast
			log.info("is broadcast and try to find pathh");
			paths  = topologyService.getPaths(topologyService.currentTopology(),
						pkt.receivedFrom().deviceId(),
						deviceConnectPoint.deviceId());
		}
		else{
			//is not broadcast
			log.info("is not broadcast and try to find path");
			paths  = topologyService.getPaths(topologyService.currentTopology(),
						pkt.receivedFrom().deviceId(),
						dst.location().deviceId());
		
		}

		//if no path?
		//pick a ppath
		log.info("before find  interest");
		Path intPath=null;//interest path
		for(Path path:paths){
			if(!path.src().port().equals(pkt.receivedFrom().port())){
				intPath = path;
				log.info("find the Path");
				break;
			}
//			log.info("fid int looop");
		}
		log.info("before install rule");
		installRule(context,intPath.src().port());
///		log.info("ater insstall  rule");
	}

	private void installRule(PacketContext context,PortNumber portNumber){
		//use to create matching field
		TrafficSelector.Builder  selectorBuilder = DefaultTrafficSelector.builder();
		InboundPacket pkt = context.inPacket();
		Ethernet ethPkt  = pkt.parsed();

		if(ethPkt.isBroadcast()){
			selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
				.matchIPProtocol(IPv4.PROTOCOL_UDP)
				.matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
				.matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));
		}
		else{
			selectorBuilder.matchEthType(Ethernet.TYPE_IPV4)
				.matchEthDst(ethPkt.getDestinationMAC())
				.matchEthSrc(ethPkt.getSourceMAC());

		}
		//use  to create action
		TrafficTreatment treatmentBuild = DefaultTrafficTreatment.builder()
			.setOutput(portNumber).build();

		//to install the flow mod
		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
			.withSelector(selectorBuilder.build())
			.withTreatment(treatmentBuild)
			.withPriority(50000)
			.withFlag(ForwardingObjective.Flag.VERSATILE)
			.fromApp(appId)
			.makeTemporary(20)
			.add();
		flowObjectiveService.forward(pkt.receivedFrom().deviceId(),forwardingObjective);
		//packetout
		context.treatmentBuilder().setOutput(portNumber);
		context.send();

	}
}
