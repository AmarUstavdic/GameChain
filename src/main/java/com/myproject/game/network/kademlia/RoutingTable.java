package com.myproject.game.network.kademlia;

import com.myproject.game.utils.Constants;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class RoutingTable {

    // alpha determines the maximum number of nodes that can be contacted in parallel
    // when performing iterative routing or lookups in the distributed hash table.
    // B / 2^K - used to calculate prefix length for a given bucket
    private int B;       // ID size in number of bits
    private int K;       // size of k-bucket (number of nodes it can store)
    private int alpha;   // number of parallel queries
    private List<List<Node>> buckets;
    private Node localNode;
    private Node bootstrapNode;


    public RoutingTable(InetAddress ip, int port, boolean isBootstrapNode, int B, int K, int alpha) {
        this.B = B;
        this.K = K;
        this.alpha = alpha;
        Node node = new Node(
                KademliaIDGenerator.generateID(ip.getHostAddress()+port),
                new InetSocketAddress(ip.getHostAddress(), port),
                isBootstrapNode
        );
        if (isBootstrapNode) {
            localNode = bootstrapNode = node;
        } else {
            localNode = node;
            bootstrapNode = new Node(
                    KademliaIDGenerator.generateID("172.18.0.2"+port),
                    new InetSocketAddress( "172.18.0.2", port),
                    true
            );
        }

        buckets = new ArrayList<>((int)Math.pow(2, K));
        for (int i = 0; i < (int)Math.pow(2, K); i++) {
            buckets.add(new ArrayList<>(K));
        }

        // not really necessary
        insertNode(bootstrapNode);
    }





    public boolean insertNode(Node node) {
        if (node.getNodeId().getID().equals(localNode.getNodeId().getID())) return false;
        int index = getIndex(localNode.getNodeId(), node.getNodeId());
        ArrayList<Node> bucket = (ArrayList<Node>) buckets.get(index);
        if (bucket.size() < K) {
            removeNode(node);
        } else {
            if (!removeNode(node)) {
                removeOldestNode(bucket);
            }
        }
        bucket.add(node);
        return true;
    }

    public boolean removeNode(Node node) {
        int index = getIndex(localNode.getNodeId(), node.getNodeId());
        ArrayList<Node> bucket = (ArrayList<Node>) buckets.get(index);
        for (int i = 0; i < bucket.size(); i++) {
            if (bucket.get(i).getNodeId().equals(node.getNodeId())) {
                bucket.remove(i);
                return true;
            }
        }
        return false;
    }

    public Node getNode(KademliaID nodeId) {
        int index = getIndex(localNode.getNodeId(), nodeId);
        List<Node> bucket = buckets.get(index);
        for (Node node : bucket) {
            if (node.getNodeId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    public ArrayList<Node> getClosestNodes(KademliaID target, int count) {
        ArrayList<Node> closestNodes = new ArrayList<>();

        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingInt(node -> node.getDistance(target)));

        for (List<Node> bucket : buckets) {
            for (Node node : bucket) {
                if (!Objects.equals(node.getNodeId().getID(), localNode.getNodeId().getID())) {
                    pq.offer(node);
                }
            }
        }
        while (!pq.isEmpty() && closestNodes.size() < count) {
            closestNodes.add(pq.poll());
        }
        return closestNodes;
    }






    // helper methods are bellow
    private int getIndex(KademliaID localNodeId, KademliaID nodeId) {
        BigInteger distance = localNodeId.getNumericID().xor(nodeId.getNumericID());
        int prefixLength = (int)(B / Math.pow(2, K));
        int offset = B - prefixLength;
        return distance.intValue() >> offset;
    }



    private static void removeOldestNode(List<Node> nodes) {
        Instant oldestTimestamp = Instant.MAX;
        Node oldestNode = null;
        for (Node node : nodes) {
            if (node.getLastSeen().isBefore(oldestTimestamp)) {
                oldestTimestamp = node.getLastSeen();
                oldestNode = node;
            }
        }
        if (oldestNode != null) {
            nodes.remove(oldestNode);
        }
    }




    // getters and setters

    public List<Node> getAllNodes() {
        return buckets.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public int getAlpha() {
        return alpha;
    }

    public Node getBootstrapNode() {
        return bootstrapNode;
    }

    public Node getLocalNode() {
        return localNode;
    }






    // for testing purposes only
    public void print() {
        System.out.println(Constants.BLUE);
        for (int i = 0; i < buckets.size(); i++) {
            System.out.print("[BUCKET " + i + "]:  ");
            List<Node> bucket = buckets.get(i);
            for (Node node : bucket) {
                KademliaID nodeId = node.getNodeId();
                System.out.print(nodeId.toString() + "  ");
            }
            System.out.println();
        }
        System.out.println(Constants.RESET);
    }

}
