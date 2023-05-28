package com.myproject.game.network.blockchain;


import com.google.common.eventbus.EventBus;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.myproject.game.ebus.EventType;
import com.myproject.game.network.kademlia.KademliaDHT;
import com.myproject.game.network.kademlia.Node;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;


public class BlockchainMessageHandler implements Runnable {
    private final EventBus eventBus;
    private final VDFService vdfService;
    private final KademliaDHT dht;
    private final BlockchainInbox inbox;
    private final BlockchainOutbox outbox;
    private final int maxRetries;
    private final int port;
    private final int connectionTimeout;
    private final Blockchain blockchain;
    private final InclusionRequestsList inclusionRequestsList;
    private final MatchRequestList matchRequestList;


    public BlockchainMessageHandler(EventBus eventBus,MatchRequestList matchRequestList, InclusionRequestsList inclusionRequestsList, VDFService vdfService, KademliaDHT dht, BlockchainInbox inbox, BlockchainOutbox outbox, ArrayList<Block> chain, int maxRetries, int port, int connectionTimeout, Blockchain blockchain) {
        this.eventBus = eventBus;

        this.dht = dht;
        this.vdfService = vdfService;
        this.inbox = inbox;
        this.outbox = outbox;
        this.maxRetries = maxRetries;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.blockchain = blockchain;
        this.inclusionRequestsList = inclusionRequestsList;
        this.matchRequestList = matchRequestList;
    }


    @Override
    public void run() {
        BlockchainMessage message = null;
        while (true) {
            try {
                message = inbox.getNextMessage();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            switch (message.getType()) {
                case SYNC:
                    // when node receives SYNC request it splices its chain and sends the rest of the chan
                    // in order to sync node that initiated SYNC
                    System.out.println("received sync message");


                    try {
                        outbox.addMessage(new BlockchainMessage(
                                BlockchainMessageType.SYNC_REPLY,
                                new Gson().toJson(blockchain.getChain()))
                        );
                    } catch (InterruptedException e) {
                        System.out.println("unable to reply on sync");
                        throw new RuntimeException(e);
                    }


                    //handleSync(message);
                    break;
                case SYNC_REPLY:
                    blockchain.syncChain(new Gson().fromJson(message.getPayload(), new TypeToken<ArrayList<Block>>() {}.getType()));
                    System.out.println("synced the chain");
                    break;
                case NEW_BLOCK:
                    Block block = new Gson().fromJson(message.getPayload(), Block.class);
                    if (!(block.getBlockNumber() == blockchain.getLastBlock().getBlockNumber())) {
                        broadcastNewBlock(message);
                        addNewBlockToChain(message);
                    }

                    checkIfImMatched(message);
                    // eventBus.post(EventType.PTP_ESTABLISHED);
                    break;
                case INCLUSION_REQUEST:
                    System.out.println("inclusion request sent");
                    inclusionRequestsList.cacheNewInclusionRequest(message);
                    break;
                case TTT_MATCHMAKING_REQUEST:
                    matchRequestList.cacheRequest(message);
                    System.out.println("I got a message: "+ message.getPayload());


                    break;
                default:
                    // for the rest doing nothing
                    break;
            }
        }
    }


    private void checkIfImMatched(BlockchainMessage message) {
        Block block = new Gson().fromJson(message.getPayload(), Block.class);

        String[] p;
        for (String[] pair : block.getMatchedNodes()) {
            if (pair[0].equals(dht.getNodeId()) || pair[1].equals(dht.getNodeId())) {
                // I am mathched
                System.out.println("IM MATCHED");
                p = pair;
                break;
            }
        }


    }





    private void handleSync(BlockchainMessage message) {
        Gson gson = new Gson();
        String lastNodesBlock = message.getPayload();
        if (lastNodesBlock.equals("null")) {
            // send entire blockchain to the node that requested sync
            BlockchainMessage syncReply = new BlockchainMessage(
                    BlockchainMessageType.SYNC_REPLY,
                    gson.toJson(blockchain.getChain())
            );
            try {
                outbox.addMessage(syncReply);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        } else {
            // splice the chain and send only the part that node who requested sync does not have
            int nodesLastBlockId = Integer.parseInt(message.getPayload());

            // get the chain
            ArrayList<Block> splicedChain = blockchain.getRequestedPartOfChain(nodesLastBlockId);

            // serialize it and put the message in the outbox
            BlockchainMessage syncReply = new BlockchainMessage(
                    BlockchainMessageType.SYNC_REPLY,
                    gson.toJson(splicedChain)
            );
            try {
                outbox.addMessage(syncReply);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }




    private void addNewBlockToChain(BlockchainMessage message) {
        Gson gson = new Gson();
        Block block = gson.fromJson(message.getPayload(), Block.class);
        blockchain.addNewBlock(block);
    }



    private void broadcastNewBlock(BlockchainMessage message) {
        Gson gson = new Gson();
        List<Node> knownPeers = dht.getKnowPeers(); // Use List instead of specific implementation

        Block block = gson.fromJson(message.getPayload(), Block.class);
        System.out.println("Block sequence number: " + block.getBlockNumber());

        // Check if the received block is the same as the last block in the chain
        if (!blockchain.getChain().isEmpty() && blockchain.getChain().get(blockchain.getChain().size() - 1).getBlockNumber() == block.getBlockNumber()) {
            return; // Avoid broadcasting the same block again
        }

        blockchain.addNewBlock(block); // Add the received block to the chain
        System.out.println(blockchain.getChain().size());
        blockchain.setNewBlock(true);
        System.out.println(blockchain.isNewBlock());




        for (Node peer : knownPeers) {
            int retries = 0;
            boolean messageSent = false;

            while (retries < maxRetries && !messageSent) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(peer.getAddress().getAddress(), port), connectionTimeout);
                    socket.getOutputStream().write(gson.toJson(message).getBytes());
                    socket.getOutputStream().flush();
                    messageSent = true; // Message sent successfully
                } catch (SocketTimeoutException e) {
                    System.out.println("Connection timeout to: " + peer.getNodeId() + ". Retrying...");
                    retries++;
                } catch (IOException e) {
                    System.out.println("Failed to connect to: " + peer.getNodeId() + ". Retrying...");
                    retries++;
                }
            }

            if (!messageSent) {
                System.out.println("Unable to send message to: " + peer.getNodeId());
            }
        }
    }




}
