package com.myproject.game.network.blockchain;

import com.google.gson.Gson;
import com.myproject.game.network.kademlia.KademliaDHT;
import com.myproject.game.network.vdf.VDFResult;
import com.myproject.game.network.vdf.WesolowskiVDF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;


public class VDFService implements Runnable {
    private final Gson gson;
    private final WesolowskiVDF vdf;
    private final Blockchain blockchain;
    private final BlockchainOutbox outbox;
    private final KademliaDHT dht;
    private final InclusionRequestsList inclusionRequestsList;
    private final MatchRequestList requestList;

    public VDFService(WesolowskiVDF vdf, Blockchain blockchain, KademliaDHT dht,
                         BlockchainOutbox outbox, InclusionRequestsList inclusionRequestsList,
                         MatchRequestList requestList) {
        this.gson = new Gson();
        this.vdf = vdf;
        this.blockchain = blockchain;
        this.dht = dht;
        this.outbox = outbox;
        this.inclusionRequestsList = inclusionRequestsList;
        this.requestList = requestList;
    }

    @Override
    public void run() {
        while (true) {
            if (!blockchain.isChainEmpty()) {
                Block lastBlock = blockchain.getLastBlock();
                VDFResult vdfResult = vdf.eval(lastBlock.getBlockHash().getBytes(),
                        lastBlock.getVdfDifficulty(), lastBlock.getModulo());

                String nextProducerID = getBlockProducerID(
                        lastBlock.getPreviousConsensusNodeList(),
                        vdfResult.getLPrime().intValue()
                );

                if (dht.getNodeId().equals(nextProducerID)) {
                    System.out.println("I am the next block producer");

                    Block newBlock = createNewBlock(lastBlock, nextProducerID);
                    processNewlyCreatedBlock(newBlock);
                }
            }

            try {
                // Adjust the sleep duration as per your requirements
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Block createNewBlock(Block lastBlock, String nextProducerID) {
        ArrayList<String> consensusNodes = new ArrayList<>(lastBlock.getPreviousConsensusNodeList());
        consensusNodes.add(nextProducerID);

        ArrayList<String> includedNodes = inclusionRequestsList.getInclusionRequestIDs();
        consensusNodes.addAll(includedNodes);

        Collections.shuffle(consensusNodes);
        consensusNodes.subList(0, lastBlock.getMaxConsensusList());


        // MATCHED NODES FROM THE REQUEST LIST
        ArrayList<String[]> matched = new ArrayList<>();
        int lnt = requestList.getMatchRequests().size();
        if (lnt % 2 != 0) {
            lnt--; // Reduce lnt by 1 if it's an odd number
        }
        for (int i = 0; i < lnt; i += 2) {
            String p1 = requestList.getMatchRequests().get(i).getPayload();
            String p2 = requestList.getMatchRequests().get(i + 1).getPayload();
            matched.add(new String[]{p1, p2});
        }



        // Create the new block
        return new Block(lastBlock.getBlockNumber() + 1, lastBlock.getModulo(), lastBlock.getBlockHash(),
                consensusNodes, dht.getNodeId(), matched);
    }

    private void processNewlyCreatedBlock(Block block) {
        System.out.println("block sequence number: " + block.getBlockNumber());
        blockchain.addNewBlock(block);

        BlockchainMessage message = new BlockchainMessage(BlockchainMessageType.NEW_BLOCK,
                block.toJson());

        try {
            outbox.addMessage(message);
        } catch (InterruptedException e) {
            System.out.println("Failed to add newly built block to the outbox!");
        }
    }

    private String getBlockProducerID(ArrayList<String> consensusList, int seed) {
        ArrayList<String> shuffledList = new ArrayList<>(consensusList);
        Collections.shuffle(shuffledList, new Random(seed));
        return shuffledList.get(0);
    }
}
