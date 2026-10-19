import java.util.*;

public class QueueCraftTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var pool = List.of(new QueueCraft.Party(0, 5, 1000, 0),
            new QueueCraft.Party(1, 5, 1300, 0));
        check(QueueCraft.choose(pool, 0, false) == null, "Fixed policy should reject a 300-point gap");
        check(QueueCraft.choose(pool, 60, true) != null, "Waiting should widen adaptive tolerance");
        check(QueueCraft.teams(List.of(new QueueCraft.Party(1, 3, 1000, 0)), 5).isEmpty(), "Cannot split parties");
        for (int seed = 0; seed < 20; seed++) for (boolean adaptive : new boolean[]{false, true}) {
            var r = QueueCraft.simulate(seed, 300, adaptive);
            check(r.equals(QueueCraft.simulate(seed, 300, adaptive)), "Seed must reproduce results");
            check(r.waits().size() + r.remaining() == r.arrived(), "Player conservation");
            Set<Integer> used = new HashSet<>();
            for (var m : r.matches()) {
                check(m.a().stream().mapToInt(QueueCraft.Party::size).sum() == 5, "Team A capacity");
                check(m.b().stream().mapToInt(QueueCraft.Party::size).sum() == 5, "Team B capacity");
                for (var team : List.of(m.a(), m.b())) for (var p : team) {
                    check(used.add(p.id()), "Party matched twice");
                    check(p.joined() <= m.time(), "Matched before arrival");
                }
            }
        }
        System.out.println("Passed: policy thresholds, party integrity, deterministic replay, conservation, team capacity, unique assignment (40 simulations).");
    }
}
