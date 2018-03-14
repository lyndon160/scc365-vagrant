#!/usr/bin/python

"""
Create a network where different switches are connected to
different controllers, by creating a custom Switch() subclass.
"""

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.node import OVSSwitch, Controller, RemoteController, OVSKernelSwitch
from mininet.topolib import TreeTopo
from mininet.log import setLogLevel
from mininet.cli import CLI

setLogLevel( 'info' )

# Two local and one "external" controller (which is actually c0)
# Ignore the warning message that the remote isn't (yet) running
c0 = RemoteController( 'c0', ip='127.0.0.1', port=6633 )
c1 = Controller( 'c1', port=6666 )
cmap = { 's1': c1, 'r1': c0, 'r2': c0, 's2': c1 }

class MultiSwitch( OVSSwitch ):
    "Custom Switch() subclass that connects to different controllers"
    def start( self, controllers ):
        return OVSSwitch.start( self, [ cmap[ self.name ] ] )

class MyTopo( Topo ):

    def __init__( self ):

        # Initialize topology
        Topo.__init__( self )

        # Add hosts and switches
        host1 = self.addHost('h1', ip='10.0.1.10/24')
        host2 = self.addHost('h2', ip='10.0.1.20/24')
        host3 = self.addHost('h3', ip='10.0.2.10/24')
        host4 = self.addHost('h4', ip='10.0.2.20/24')
        router1 = self.addSwitch('r1', dpid='0000000000000001')
        router2 = self.addSwitch('r2', dpid='0000000000000002')
        switch1 = self.addSwitch('s1', dpid='0000000000000008')
        switch2 = self.addSwitch('s2', dpid='0000000000000009')

        # Add links
        self.addLink(host1, switch1)
        self.addLink(host2, switch1)
        self.addLink(host3, switch2)
        self.addLink(host4, switch2)
        self.addLink(router1, router2)
        self.addLink(router1, switch1)
        self.addLink(router2, switch2)

topo = MyTopo()

net = Mininet( topo=topo, switch=MultiSwitch, build=False )
for c in [ c0, c1 ]:
    net.addController(c)
net.build()
net.start()


net.get('h1').setMAC('00:00:00:00:00:01')
net.get('h2').setMAC('00:00:00:00:00:02')
net.get('h3').setMAC('00:00:00:00:00:03')
net.get('h4').setMAC('00:00:00:00:00:04')

net.get('h1').cmd('route add default gw 10.0.1.1 h1-eth0; arp -s 10.0.1.1 00:00:00:00:00:10')
net.get('h2').cmd('route add default gw 10.0.1.1 h2-eth0; arp -s 10.0.1.1 00:00:00:00:00:10')
net.get('h3').cmd('route add default gw 10.0.2.1 h3-eth0; arp -s 10.0.2.1 00:00:00:00:00:40')
net.get('h4').cmd('route add default gw 10.0.2.1 h4-eth0; arp -s 10.0.2.1 00:00:00:00:00:40')

CLI( net )
net.stop()
