package com.myproject.game.network.blockchain;

import com.google.gson.Gson;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;

public class Block {

    private final int blockNumber;
    private final BigInteger modulo;
    private final int vdfDifficulty;
    private final String previousBlockHash;
    private final String blockHash;
    private final ArrayList<String> previousConsensusNodeList;
    private final String blockProducer;
    private final long timestamp;
    private final int maxConsensusList;
    private final ArrayList<String[]> matchedNodes;



    public Block(int blockNumber, BigInteger modulo, String previousBlockHash, ArrayList<String> previousConsensusNodeList, String blockProducer, ArrayList<String[]> matchedNodes) {
        this.blockNumber = blockNumber;
        this.modulo = modulo;
        this.previousBlockHash = previousBlockHash;
        this.previousConsensusNodeList = previousConsensusNodeList;
        this.blockProducer = blockProducer;
        this.vdfDifficulty = 100000;  // for now hardcoded, but can be decided dynamically by the network
        // block hash is calculated last since it includes all the rest of the data of the block in order to be calculated
        this.blockHash = calculateBlockHash();
        this.timestamp = Instant.now().getEpochSecond();
        this.maxConsensusList = 3;
        this.matchedNodes = matchedNodes;

    }




    private String calculateBlockHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // this still needs to be adjusted later
            String blockData =
                            blockNumber +
                            modulo.toString() +
                            previousBlockHash +
                            previousConsensusNodeList.toString() +
                            blockProducer + timestamp +
                            vdfDifficulty;

            byte[] hashBytes = digest.digest(blockData.getBytes());
            BigInteger hashInt = new BigInteger(1, hashBytes);
            StringBuilder hashBuilder = new StringBuilder(hashInt.toString(16));

            while (hashBuilder.length() < 64) {
                hashBuilder.insert(0, "0");
            }

            return hashBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    public BigInteger getModulo() {
        return modulo;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public int getVdfDifficulty() {
        return vdfDifficulty;
    }

    public ArrayList<String> getPreviousConsensusNodeList() {
        return previousConsensusNodeList;
    }

    public int getMaxConsensusList() {
        return maxConsensusList;
    }

    public int getBlockNumber() {
        return blockNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ArrayList<String[]> getMatchedNodes() {
        return matchedNodes;
    }

    public String getPreviousBlockHash() {
        return previousBlockHash;
    }

    public String getBlockProducer() {
        return blockProducer;
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

}
