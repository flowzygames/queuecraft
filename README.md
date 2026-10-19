# QueueCraft

A Java 21 experiment in party-aware 5v5 matchmaking. Compare a fixed skill-gap
threshold with a threshold that expands as the oldest party waits, using exactly
the same seeded arrivals for both policies. No external runtime dependencies.

## Run

Install a **JDK 21** (a JRE alone does not include the compiler), then:

```sh
./run.sh
./run.sh 42 600 results.csv
./run.sh test
```

Arguments are random seed, simulation duration in seconds (1–86400), and an
optional CSV destination. Output reports matched players, remaining players,
median and 95th-percentile player wait, and mean team skill gap. Existing CSV
output files are overwritten. `example-results.csv` contains a sample run.

## Model

Each simulated second has a 65% chance of one new party arriving. Parties have
1–3 players and normally distributed skill (mean 2000, standard deviation 450,
floored at zero). Everyone in a party shares its skill estimate. Parties remain
intact and both teams must have exactly five players.

The matcher enumerates possible teams from the oldest 12 queued parties, then
compares disjoint team pairs. A match must include the oldest party. Eligible
pairs minimize the absolute difference in player-weighted team skill plus 15
points per difference in number of parties on each team. Enumeration order
breaks ties deterministically.

- **Fixed:** allows a maximum 100-point difference between team means.
- **Adaptive:** allows 50 points plus 5 points per second the oldest party waits.

The simulator attempts matches after each arrival tick until no eligible pairing
remains. It stops at the requested horizon without draining the queue.

## What the experiment reveals

Seed 42 over 600 seconds produces 31 matches under each policy, with 525 players
still waiting. The fixed policy has a mean skill gap of about 35.3; adaptive is
about 47.0. These are synthetic observations, not evidence that either policy is
better for a live game.

The deliberately bounded pool and oldest-party requirement can cause
**head-of-line blocking**: the first 12 parties may have no valid partition even
though later arrivals could complete a match. Widening the skill threshold
cannot solve a party-size packing problem. This implementation makes that failure
visible; it does not claim to be a production matchmaker.

Wait percentiles describe **matched players only**. They exclude the unfinished
waits of players still queued, so always read them alongside the remaining-player
count. Skill gap measures balance between team averages, not fairness among
individual players. Empty-match summaries report zero for aggregate values.

## Verification and next experiments

The test runner checks threshold expansion, unsplittable parties, deterministic
replay, player conservation, team sizes, arrival ordering, and unique party
assignment across 40 seeded simulations. GitHub Actions runs tests and a CLI
smoke check on JDK 21.

Useful next experiments: expand the candidate pool on packing failures, compare
against a policy that can skip the oldest party, report unfinished waits, run
multiple seeds with uncertainty intervals, and model player roles or latency.
Those features are not implemented yet.

## Development

`src/QueueCraft.java` contains the CLI, arrival generator, matcher, and metrics.
`src/QueueCraftTest.java` is a dependency-free invariant test runner. Simulated
time is independent of wall-clock execution time. Exact subset search is bounded
by the 12-party candidate window; increasing that window substantially increases
computational cost.

Built with AI assistance. This repository is a simulation study and learning
project, with its assumptions and limitations recorded above.
