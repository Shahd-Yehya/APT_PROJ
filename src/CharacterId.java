package src;
import java.util.Objects;

public final class CharacterId {

    private final int siteId;   
    private final int clock;    

    public CharacterId(int siteId, int clock) {
        this.siteId = siteId;
        this.clock  = clock;
    }

    

    public int getSiteId() { return siteId; }
    public int getClock()  { return clock;  }

    

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CharacterId)) return false;
        CharacterId other = (CharacterId) o;
        return siteId == other.siteId && clock == other.clock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteId, clock);
    }

    @Override
    public String toString() {
        return "[site=" + siteId + ", clk=" + clock + "]";
    }
}
