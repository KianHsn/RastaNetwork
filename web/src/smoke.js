import { JavaRandom } from "./rng.js";
import { NetworkMap } from "./model/NetworkMap.js";
import { ShortestPaths } from "./model/ShortestPaths.js";
import { GameSession, CheckResult } from "./controller/GameSession.js";
import { BellmanFordSession, FailResult } from "./controller/BellmanFordSession.js";

const seeds = [1n, 42n, 123456789n, -7n, 99n];
for (const seed of seeds) {
  const map = NetworkMap.random(8, new JavaRandom(seed));
  if (!map.isConnected()) {
    throw new Error(`Map not connected for seed ${seed}`);
  }
  const table = ShortestPaths.tableFrom(map, map.sourceId());
  if (!table.has(map.destId())) {
    throw new Error("Missing dest row");
  }
  const path = ShortestPaths.nodePath(map, map.sourceId(), map.destId());
  if (path[0] !== map.sourceId() || path[path.length - 1] !== map.destId()) {
    throw new Error("Bad hop path");
  }
  const wpath = ShortestPaths.weightedNodePath(map, map.sourceId(), map.destId());
  if (wpath[0] !== map.sourceId() || wpath[wpath.length - 1] !== map.destId()) {
    throw new Error("Bad weighted path");
  }

  const hop = new GameSession(6, seed);
  const router = hop.map().routers().find((r) => hop.isUnlocked(r.id()));
  const solution = hop.solutionFor(router.id());
  const result = hop.check(router.id(), new Map(solution));
  if (result !== CheckResult.CORRECT) {
    throw new Error(`Hop check failed: ${result}`);
  }

  const bf = new BellmanFordSession(6, seed);
  let failed = false;
  for (const link of bf.map().links()) {
    const fail = bf.failLink(link);
    if (fail === FailResult.OK) {
      failed = true;
      break;
    }
  }
  if (!failed) {
    console.log(`note: no failable link for seed ${seed}`);
  }
}

console.log("smoke ok");
