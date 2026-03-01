package fr.mrjuju0319.pvoteparty.vote;

import java.util.List;
import java.util.Map;

public interface VoteStorage {

    int incrementVotes(String playerName, int amount);

    int incrementPartyProgress(int amount);

    int getPartyProgress();

    void setPartyProgress(int value);

    int getVotes(String playerName);

    VotePlayerStats getStats(String playerName);

    void enqueuePendingReward(String playerName, String command);

    List<String> popPendingRewards(String playerName);

    void setOnlineServer(String playerName, String serverName);

    void clearOnlineServer(String playerName, String serverName);

    String getOnlineServer(String playerName);

    Map<String, Integer> topVotes(int limit);

    void setPallier(String pallier, boolean value);

    boolean getPallier(String pallier);

    void resetPallier(String pallier);

    void resetAllPalliers();

    void upsertSharedConfig(VoteConfig config);

    SharedConfig loadSharedConfig();

    void flush();

    void close();

    record SharedConfig(Integer goal, List<String> voteRewards, List<String> partyRewards) {
    }
}
