import { JavaRandom } from "../rng.js";
import { NetworkMap } from "../model/NetworkMap.js";
import { RoutingEntry } from "../model/RoutingEntry.js";
import { ShortestPaths } from "../model/ShortestPaths.js";
import { CheckResult, SelectResult } from "./GameSession.js";

export const Phase = {
  PICK_LINK: "PICK_LINK",
  REPAIR: "REPAIR",
  DONE: "DONE",
};

export const FailResult = {
  OK: "OK",
  ALREADY_FAILED: "ALREADY_FAILED",
  DISCONNECTS: "DISCONNECTS",
  NOT_USED: "NOT_USED",
};

export class BellmanFordSession {
  constructor(nodeCount, seed) {
    const random = new JavaRandom(seed);
    let chosen = null;
    let tables = null;
    for (let attempt = 0; attempt < 20; attempt++) {
      const candidate = NetworkMap.random(nodeCount, random);
      const built = hostTables(candidate);
      if (hasInterestingFailure(candidate, built)) {
        chosen = candidate;
        tables = built;
        break;
      }
      if (chosen == null) {
        chosen = candidate;
        tables = built;
      }
    }
    this._original = chosen;
    this._live = chosen;
    this._failed = null;
    this._originalTables = tables;
    this._accepted = new Map();
    this._affected = new Map();
    this._completed = new Set();
    this._selectedNode = null;
    this._phase = Phase.PICK_LINK;
  }

  phase() {
    return this._phase;
  }

  remainingRepairs() {
    let n = 0;
    for (const id of this._affected.keys()) {
      if (!this._completed.has(id)) {
        n++;
      }
    }
    return n;
  }

  affectedRouterCount() {
    return this._affected.size;
  }

  failLink(link) {
    if (this._failed != null) {
      return FailResult.ALREADY_FAILED;
    }
    const canonical = this._original.linkBetween(link.a(), link.b());
    if (canonical == null) {
      return FailResult.NOT_USED;
    }
    const next = this._original.withoutLink(canonical);
    if (!next.isConnected()) {
      return FailResult.DISCONNECTS;
    }
    const broken = new Map();
    for (const router of this._original.routers()) {
      const dests = [];
      for (const host of this._original.hosts()) {
        if (walkUsesLink(this._original, this._originalTables, router.id(), host.id(), canonical)) {
          dests.push(host.id());
        }
      }
      if (dests.length > 0) {
        broken.set(router.id(), new Set(dests));
      }
    }
    if (broken.size === 0) {
      return FailResult.NOT_USED;
    }
    this._failed = canonical;
    this._live = next;
    this._affected = broken;
    this._phase = Phase.REPAIR;
    for (const router of this._original.routers()) {
      if (!this._affected.has(router.id())) {
        this._completed.add(router.id());
        this._accepted.set(router.id(), copyTable(this._originalTables.get(router.id())));
      }
    }
    return FailResult.OK;
  }

  map() {
    return this._original;
  }

  selectedNode() {
    return this._selectedNode;
  }

  selectNode(nodeId) {
    if (this._original.node(nodeId).isHost()) {
      return SelectResult.HOST;
    }
    this._selectedNode = nodeId;
    return SelectResult.OK;
  }

  check(nodeId, playerTable) {
    if (this._failed == null) {
      return CheckResult.INCOMPLETE;
    }
    if (this._original.node(nodeId).isHost()) {
      return CheckResult.INCOMPLETE;
    }
    if (this._completed.has(nodeId)) {
      return CheckResult.CORRECT;
    }
    const dests = this._affected.get(nodeId);
    if (dests == null || dests.size === 0) {
      return CheckResult.CORRECT;
    }
    for (const dest of dests) {
      if (playerTable.get(dest) == null) {
        return CheckResult.INCOMPLETE;
      }
    }
    for (const dest of dests) {
      const player = playerTable.get(dest);
      if (!ShortestPaths.isLegalWeightedNextHop(this._live, nodeId, player.nextHop(), dest)) {
        return CheckResult.WRONG_HOP;
      }
    }
    const table = copyTable(this._originalTables.get(nodeId));
    for (const dest of dests) {
      const hop = playerTable.get(dest).nextHop();
      const cost = ShortestPaths.weightedDistance(this._live, nodeId, dest);
      table.set(dest, new RoutingEntry(dest, hop, cost));
    }
    this._accepted.set(nodeId, table);
    this._completed.add(nodeId);
    if (this.remainingRepairs() === 0) {
      this._phase = Phase.DONE;
    }
    return CheckResult.CORRECT;
  }

  isCompleted(nodeId) {
    return this._completed.has(nodeId);
  }

  isUnlocked(nodeId) {
    return this._original.node(nodeId).isRouter();
  }

  toldNextHop(routerId) {
    const table = this.visibleTable(routerId);
    if (table == null) {
      return null;
    }
    const entry = table.get(this._original.destId());
    return entry == null ? null : entry.nextHop();
  }

  packetArrived() {
    return false;
  }

  playerPathSuboptimal() {
    return false;
  }

  playerPath() {
    return [];
  }

  optimalPath() {
    return [];
  }

  packetNode() {
    return this._original.sourceId();
  }

  acceptedTable(nodeId) {
    return this._accepted.get(nodeId);
  }

  visibleTable(nodeId) {
    const repaired = this._accepted.get(nodeId);
    if (repaired != null) {
      return repaired;
    }
    return this._originalTables.get(nodeId);
  }

  isRowBroken(routerId, destId) {
    if (this._failed == null || this._completed.has(routerId)) {
      return false;
    }
    const dests = this._affected.get(routerId);
    return dests != null && dests.has(destId);
  }

  canEditRow(routerId, destId) {
    return this.isRowBroken(routerId, destId);
  }

  showCost() {
    return true;
  }

  showWeights() {
    return true;
  }

  showPacket() {
    return false;
  }

  failedLink() {
    return this._failed;
  }

  neighborChoices(nodeId) {
    return this._live.neighborIds(nodeId);
  }

  completedNodes() {
    return this._completed;
  }

  formatLink(link) {
    return `${this._original.node(link.a()).label()} — ${this._original.node(link.b()).label()}  (w=${link.weight()})`;
  }

  neighborHint(nodeId) {
    const lines = [];
    lines.push(`Neighbors of ${this._original.node(nodeId).label()}:`);
    lines.push("Bellman-Ford: new cost = link weight + neighbor's cost to dest.");
    lines.push("Pick the neighbor with the smallest sum.");
    lines.push("");
    for (const hop of this._live.neighborIds(nodeId)) {
      const weight = this._live.weightBetween(nodeId, hop);
      lines.push(`  ${this._original.node(hop).label()}   weight ${weight}`);
    }
    return lines.join("\n");
  }
}

function copyTable(table) {
  return new Map(table);
}

function hostTables(map) {
  const tables = new Map();
  for (const node of map.routers()) {
    const full = ShortestPaths.weightedTableFrom(map, node.id());
    const hostRows = new Map();
    for (const host of map.hosts()) {
      hostRows.set(host.id(), full.get(host.id()));
    }
    tables.set(node.id(), hostRows);
  }
  return tables;
}

function hasInterestingFailure(map, tables) {
  for (const link of map.links()) {
    if (!map.withoutLink(link).isConnected()) {
      continue;
    }
    for (const router of map.routers()) {
      for (const host of map.hosts()) {
        if (walkUsesLink(map, tables, router.id(), host.id(), link)) {
          return true;
        }
      }
    }
  }
  return false;
}

function walkUsesLink(map, tables, from, dest, link) {
  let cur = from;
  let guard = map.nodes().length + 2;
  while (cur !== dest && guard-- > 0) {
    const table = tables.get(cur);
    if (table == null) {
      return false;
    }
    const entry = table.get(dest);
    if (entry == null) {
      return false;
    }
    const next = entry.nextHop();
    if (link.connects(cur) && link.connects(next)) {
      return true;
    }
    cur = next;
  }
  return false;
}
