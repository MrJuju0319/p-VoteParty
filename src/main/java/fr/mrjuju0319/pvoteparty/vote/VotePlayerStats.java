package fr.mrjuju0319.pvoteparty.vote;

public record VotePlayerStats(int day, int week, int month, int year, int total) {

    public static VotePlayerStats empty() {
        return new VotePlayerStats(0, 0, 0, 0, 0);
    }
}
