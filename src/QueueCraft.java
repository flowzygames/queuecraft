import java.util.*;
import java.nio.file.*;

/** Deterministic party-aware matchmaking experiment. Java 21, no runtime dependencies. */
public class QueueCraft {
    record Party(int id, int size, double skill, int joined) {}
    record Match(int time, List<Party> a, List<Party> b, double gap) {}
    record Result(List<Match> matches, List<Integer> waits, int remaining, int arrived) {}

    static double mean(List<Party> team) {
        return team.stream().mapToDouble(p -> p.skill * p.size).sum()
            / team.stream().mapToInt(Party::size).sum();
    }
    // Exact subset enumeration for a bounded candidate pool. Parties never split.
    static List<List<Party>> teams(List<Party> pool, int size) {
        List<List<Party>> out = new ArrayList<>();
        enumerate(pool, 0, size, new ArrayList<>(), out);
        return out;
    }
    static void enumerate(List<Party> pool, int at, int left, List<Party> chosen,
                          List<List<Party>> out) {
        if (left == 0) { out.add(List.copyOf(chosen)); return; }
        for (int i = at; i < pool.size(); i++) {
            Party p = pool.get(i);
            if (p.size <= left) {
                chosen.add(p); enumerate(pool, i + 1, left - p.size, chosen, out);
                chosen.remove(chosen.size() - 1);
            }
        }
    }
    static Match choose(List<Party> queue, int now, boolean adaptive) {
        List<Party> pool = queue.subList(0, Math.min(12, queue.size()));
        if (pool.isEmpty()) return null;
        List<List<Party>> teams = teams(pool, 5);
        Match best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < teams.size(); i++) for (int j = i + 1; j < teams.size(); j++) {
            var a = teams.get(i); var b = teams.get(j);
            if (a.stream().anyMatch(b::contains)) continue;
            // Include the oldest party to prevent newer arrivals continually leapfrogging it.
            if (!a.contains(pool.get(0)) && !b.contains(pool.get(0))) continue;
            double gap = Math.abs(mean(a) - mean(b));
            double limit = adaptive ? 50 + 5.0 * (now - pool.get(0).joined) : 100;
            if (gap > limit) continue;
            double score = gap + 15 * Math.abs(a.size() - b.size());
            if (score < bestScore) {
                bestScore = score; best = new Match(now, a, b, gap);
            }
        }
        return best;
    }
    static Result simulate(long seed, int duration, boolean adaptive) {
        Random random = new Random(seed);
        List<Party> queue = new ArrayList<>();
        List<Match> matches = new ArrayList<>();
        List<Integer> waits = new ArrayList<>();
        int id = 0, arrived = 0;
        for (int time = 0; time < duration; time++) {
            if (random.nextDouble() < 0.65) {
                int size = 1 + random.nextInt(3);
                queue.add(new Party(id++, size, Math.max(0, 2000 + random.nextGaussian() * 450), time));
                arrived += size;
            }
            Match match;
            while ((match = choose(queue, time, adaptive)) != null) {
                matches.add(match);
                List<Party> selected = new ArrayList<>(match.a); selected.addAll(match.b);
                for (Party p : selected) for (int n = 0; n < p.size; n++) waits.add(time - p.joined);
                queue.removeAll(selected);
            }
        }
        return new Result(matches, waits, queue.stream().mapToInt(Party::size).sum(), arrived);
    }
    static int percentile(List<Integer> values, double quantile) {
        if (values.isEmpty()) return 0;
        var sorted = new ArrayList<>(values); Collections.sort(sorted);
        return sorted.get(Math.max(0, (int)Math.ceil(quantile * sorted.size()) - 1));
    }
    static void report(String policy, Result r) {
        System.out.printf(Locale.ROOT,
            "%s: matches=%d matched_players=%d queued=%d wait_p50=%ds wait_p95=%ds mean_skill_gap=%.1f%n",
            policy, r.matches.size(), r.waits.size(), r.remaining,
            percentile(r.waits, .5), percentile(r.waits, .95),
            r.matches.stream().mapToDouble(Match::gap).average().orElse(0));
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 3 || (args.length > 0 && args[0].equals("--help"))) {
            System.out.println("Usage: QueueCraft [seed=42] [duration_seconds=600] [output.csv]"); return;
        }
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
        int duration = args.length > 1 ? Integer.parseInt(args[1]) : 600;
        if (duration < 1 || duration > 86400) throw new IllegalArgumentException("Duration must be 1..86400");
        System.out.printf("QueueCraft | seed=%d duration=%ds | 5v5 | identical arrivals per policy%n", seed, duration);
        List<String> csv = new ArrayList<>(List.of("policy,time,skill_gap,team_a_party_ids,team_b_party_ids"));
        for (boolean adaptive : new boolean[]{false, true}) {
            String policy = adaptive ? "adaptive" : "fixed";
            Result result = simulate(seed, duration, adaptive); report(policy, result);
            for (Match m : result.matches) csv.add(String.format(Locale.ROOT, "%s,%d,%.3f,%s,%s",
                policy, m.time, m.gap, ids(m.a), ids(m.b)));
        }
        if (args.length > 2) Files.write(Path.of(args[2]), csv);
    }
    static String ids(List<Party> parties) {
        return String.join("|", parties.stream().map(p -> Integer.toString(p.id)).toList());
    }
}
