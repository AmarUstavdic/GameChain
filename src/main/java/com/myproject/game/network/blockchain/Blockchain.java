package com.myproject.game.network.blockchain;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.myproject.game.ebus.EventType;
import com.myproject.game.network.kademlia.KademliaDHT;
import com.myproject.game.network.vdf.WesolowskiVDF;
import com.myproject.game.ui.GameScene2;
import com.myproject.game.ui.MatchmakingScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Blockchain {

    private final EventBus eventBus;
    private final boolean isBootstrap;
    private final int port;
    private final KademliaDHT dht;
    private final WesolowskiVDF vdf;
    private final BlockchainOutbox outbox;
    private final BlockchainInbox inbox;
    private final InclusionRequestsList inclusionRequestsList;
    private final MatchRequestList matchRequestList;
    private final ArrayList<Block> chain;
    private boolean newBlock;
    private final BlockchainMessageSender sender;
    private final BlockchainMessageReceiver receiver;
    private final BlockchainMessageHandler messageHandler;
    private final VDFService VDFService;


    public Blockchain(KademliaDHT dht, int port, EventBus eventBus) {
        this.eventBus = eventBus;

        eventBus.register(this);

        this.isBootstrap = Objects.equals(dht.getBootstrapId(), dht.getNodeId());
        this.port = port;
        this.dht = dht;
        this.vdf = new WesolowskiVDF();
        this.chain = new ArrayList<>();
        this.newBlock = true;
        this.inbox = new BlockchainInbox();
        this.outbox = new BlockchainOutbox();
        this.matchRequestList = new MatchRequestList();
        this.inclusionRequestsList = new InclusionRequestsList();
        this.VDFService = new VDFService(vdf, this,dht,outbox, inclusionRequestsList,matchRequestList);
        this.messageHandler = new BlockchainMessageHandler(eventBus,matchRequestList,inclusionRequestsList, VDFService,dht,inbox, outbox, chain,4, port, 1000, this);
        this.sender = new BlockchainMessageSender(dht, outbox, port,1000, 4);
        this.receiver = new BlockchainMessageReceiver(port, inbox);



        initBlockchain();





        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(VDFService);
        executorService.submit(sender);
        executorService.submit(receiver);
        executorService.submit(messageHandler);

    }


    @Subscribe
    public void handleMessage(EventType eventType) {

        if (eventType == EventType.ONLINE_GAME) {
            System.out.println("im from blockchain");
            this.makeMatchmakingRequest(BlockchainMessageType.TTT_MATCHMAKING_REQUEST, dht.getNodeId());
        }
    }



    // NECESSARY FOR SYNCING
    public ArrayList<Block> getRequestedPartOfChain(int lastBlockId) {
        // Splice the ArrayList from index 1 (inclusive) to index 4 (exclusive)
        List<Block> splicedChain = chain.subList(lastBlockId+1, chain.size()-1);
        return (ArrayList<Block>) splicedChain;
    }

    // incase we need to send entire chain we do so
    public ArrayList<Block> getChain() {
        return chain;
    }





    private void initBlockchain() {
        if (isBootstrap) {
            vdf.setup(2048, "SHA-256");
            // genesis block
            ArrayList<String> consensusList = new ArrayList<>();
            consensusList.add(dht.getNodeId());
            chain.add(new Block(
                    0,
                    vdf.getN(),
                    "0",
                    consensusList,  // list of consensus nodes
                    dht.getNodeId(),
                    null,
                    null
            ));
        } else {
            // send a SYNC request to the closest node, in order to synchronize the chain
            vdf.setup(2048, "SHA-256");
            // SYNC NEEDS TO BE DONE FIRST
            String payload;
            if (isChainEmpty()) payload = "0";
            else payload = String.valueOf(getLastBlock().getBlockNumber());

            BlockchainMessage syncMessage = new BlockchainMessage(
                    BlockchainMessageType.SYNC,
                    payload
            );

            try {
                outbox.addMessage(syncMessage);
            } catch (InterruptedException e) {
                System.out.println("unable to add sync message to outbox");
            }

            // INCLUSION REQUEST OK
            BlockchainMessage inclusionRequest = new BlockchainMessage(
                    BlockchainMessageType.INCLUSION_REQUEST,
                    dht.getNodeId()
            );
            try {
                outbox.addMessage(inclusionRequest);
            } catch (InterruptedException e) {
                System.out.println("inclusion request was not added to the outbox");
            }
        }

    }


    public void makeMatchmakingRequest(BlockchainMessageType requestType, String myID) {
        BlockchainMessage request = new BlockchainMessage(
                requestType,
                myID
        );
        try {
            outbox.addMessage(request);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }




    public boolean isNewBlock() {
        return newBlock;
    }

    public void setNewBlock(boolean newBlock) {
        this.newBlock = newBlock;
    }

    public void syncChain(ArrayList<Block> blockArrayList) {
        chain.addAll(blockArrayList);
    }

    public boolean isChainEmpty() {
        return chain.isEmpty();
    }

    public void addNewBlock(Block block) {
        chain.add(block);
    }

    public Block getLastBlock() {
        return chain.get(chain.size()-1);
    }
}
