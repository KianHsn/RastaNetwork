import { UNREACHABLE } from "./NetworkMap.js";
import { RoutingEntry } from "./RoutingEntry.js";

export const INF = 1_000_000;

export class ShortestPaths {
  static tableFrom(map, source) {
    const n = map.nodes().length;
    const dist = Array(n).fill(UNREACHABLE);
    const prev = Array(n).fill(-1);
    dist[source] = 0;
    const queue = [source];

    while (queue.length > 0) {
      const u = queue.shift();
      for (const v of map.neighborIds(u)) {
        const nd = dist[u] + 1;
        if (dist[v] === UNREACHABLE) {
          dist[v] = nd;
          prev[v] = u;
          queue.push(v);
        } else if (nd === dist[v] && u < prev[v]) {
          prev[v] = u;
        }
      }
    }

    return tableFromPrev(map, source, dist, prev, false);
  }

  static nodePath(map, source, dest) {
    const path = [source];
    let cur = source;
    let guard = map.nodes().length + 2;
    while (cur !== dest && guard-- > 0) {
      cur = ShortestPaths.tableFrom(map, cur).get(dest).nextHop();
      path.push(cur);
    }
    return path;
  }

  static isLegalNextHop(map, from, hop, dest) {
    if (!map.neighborIds(from).includes(hop) || hop === from) {
      return false;
    }
    if (hop === dest) {
      return true;
    }
    if (!ShortestPaths.canReachAvoiding(map, hop, dest, from)) {
      return false;
    }
    return hopsTo(map, hop, dest) < hopsTo(map, from, dest);
  }

  static canReachAvoiding(map, from, dest, avoid) {
    if (from === dest) {
      return true;
    }
    const n = map.nodes().length;
    const seen = Array(n).fill(false);
    seen[from] = true;
    if (avoid >= 0 && avoid < n) {
      seen[avoid] = true;
    }
    const q = [from];
    while (q.length > 0) {
      const u = q.shift();
      for (const v of map.neighborIds(u)) {
        if (v === dest) {
          return true;
        }
        if (!seen[v]) {
          seen[v] = true;
          q.push(v);
        }
      }
    }
    return false;
  }

  static bellmanFord(map, source) {
    const n = map.nodes().length;
    const dist = Array(n).fill(INF);
    const prev = Array(n).fill(-1);
    dist[source] = 0;
    for (let i = 0; i < n - 1; i++) {
      let changed = false;
      for (const edge of map.links()) {
        changed |= relax(edge.a(), edge.b(), edge.weight(), dist, prev);
        changed |= relax(edge.b(), edge.a(), edge.weight(), dist, prev);
      }
      if (!changed) {
        break;
      }
    }
    return { dist, prev };
  }

  static weightedTableFrom(map, source) {
    const result = ShortestPaths.bellmanFord(map, source);
    return tableFromPrev(map, source, result.dist, result.prev, true);
  }

  static weightedNodePath(map, source, dest) {
    if (source === dest) {
      return [source];
    }
    const tree = ShortestPaths.bellmanFord(map, source);
    if (tree.dist[dest] >= INF) {
      return [];
    }
    const reverse = [];
    let cur = dest;
    let guard = map.nodes().length + 2;
    while (cur !== source && guard-- > 0) {
      reverse.push(cur);
      cur = tree.prev[cur];
      if (cur < 0) {
        return [];
      }
    }
    reverse.push(source);
    return reverse.reverse();
  }

  static weightedDistance(map, from, dest) {
    if (from === dest) {
      return 0;
    }
    return ShortestPaths.bellmanFord(map, from).dist[dest];
  }

  static pathUsesLink(map, from, dest, link) {
    const path = ShortestPaths.weightedNodePath(map, from, dest);
    for (let i = 0; i < path.length - 1; i++) {
      if (link.connects(path[i]) && link.connects(path[i + 1])) {
        return true;
      }
    }
    return false;
  }

  static isLegalWeightedNextHop(map, from, hop, dest) {
    if (!map.neighborIds(from).includes(hop) || hop === from) {
      return false;
    }
    const toDest = ShortestPaths.bellmanFord(map, dest);
    if (toDest.dist[from] >= INF || toDest.dist[hop] >= INF) {
      return false;
    }
    return map.weightBetween(from, hop) + toDest.dist[hop] === toDest.dist[from];
  }
}

function hopsTo(map, node, dest) {
  if (dest === map.destId()) {
    return map.distanceToDest(node);
  }
  return map.levelFromSource(node);
}

function relax(u, v, w, dist, prev) {
  if (dist[u] >= INF) {
    return false;
  }
  const nd = dist[u] + w;
  if (nd < dist[v]) {
    dist[v] = nd;
    prev[v] = u;
    return true;
  }
  if (nd === dist[v] && (prev[v] < 0 || u < prev[v])) {
    prev[v] = u;
    return prev[v] === u;
  }
  return false;
}

function tableFromPrev(map, source, dist, prev, withCost) {
  const n = map.nodes().length;
  const table = new Map();
  for (let dest = 0; dest < n; dest++) {
    if (dest === source) {
      continue;
    }
    if (dist[dest] >= INF || dist[dest] === UNREACHABLE) {
      throw new Error("Graph is not connected");
    }
    let hop = dest;
    let guard = n + 2;
    while (prev[hop] !== source && guard-- > 0) {
      if (prev[hop] < 0) {
        throw new Error("Graph is not connected");
      }
      hop = prev[hop];
    }
    const cost = withCost ? dist[dest] : 0;
    table.set(dest, new RoutingEntry(dest, hop, cost));
  }
  return table;
}
