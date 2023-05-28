package com.myproject.game.network.kademlia;


import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *  THIS IS THE MAIN CLASS OF THE KADEMLIA DHT
 */

public class KademliaDHT {

    private final RoutingTable routingTable;
    private final InMessageQueue inMessageQueue;
    private final OutMessageQueue outMessageQueue;
    private final KademliaMessageReceiver messageReceiver;
    private final KademliaMessageSender messageSender;
    private final MessageHandler messageHandler;
    private final RoutingTableUpdater routingTableUpdater;
    private final PingHandler pingHandler;


    public KademliaDHT(InetAddress ip, int port, boolean isBootstrapNode, int B, int K, int alpha) throws SocketException {
        this.routingTable = new RoutingTable(ip, port, isBootstrapNode, B, K, alpha);
        this.inMessageQueue = new InMessageQueue();
        this.outMessageQueue = new OutMessageQueue();
        this.messageReceiver = new KademliaMessageReceiver(inMessageQueue, port);
        this.messageSender = new KademliaMessageSender(outMessageQueue);
        this.messageHandler = new MessageHandler(routingTable, inMessageQueue, outMessageQueue);
        this.routingTableUpdater = new RoutingTableUpdater(routingTable, inMessageQueue, outMessageQueue,60000);
        this.pingHandler = new PingHandler(routingTable, 30000);



        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(messageSender);
        executorService.submit(messageReceiver);
        executorService.submit(messageHandler);
        executorService.submit(routingTableUpdater);
        //executorService.submit(pingHandler);

        // only used for debugging purposes
        executorService.submit(new DebugCLI(routingTable));
    }




    public String getNodeId() {
        return routingTable.getLocalNode().getNodeId().toString();
    }

    public String getBootstrapId() {
        return routingTable.getBootstrapNode().getNodeId().toString();
    }

    public List<Node> getKnowPeers() {
        return routingTable.getAllNodes();
    }

    public Node getClosestPeer() {
        return routingTable.getClosestNodes(routingTable.getLocalNode().getNodeId(), 1).get(0);
    }

}
