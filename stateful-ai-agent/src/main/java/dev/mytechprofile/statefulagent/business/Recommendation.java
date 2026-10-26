package dev.mytechprofile.statefulagent.business;

public enum Recommendation {
    STRONG_NO(1),
    NO(2),
    YES(3),
    STRONG_YES(4);

    private final int score;

    Recommendation(int score) {
        this.score = score;
    }

    public int score() {
        return this.score;
    }

    public boolean passing() {
        return this == YES || this == STRONG_YES;
    }
}
