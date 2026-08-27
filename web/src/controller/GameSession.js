import { JavaRandom } from "../rng.js";
import { NetworkMap } from "../model/NetworkMap.js";
import { ShortestPaths } from "../model/ShortestPaths.js";

export const CheckResult = {
  CORRECT: "CORRECT",
  INCOMPLETE: "INCOMPLETE",
  WRONG: "WRONG",
  WRONG_HOP: "WRONG_HOP",
};

export const SelectResult = {
  OK: "OK",
  HOST: "HOST",
  LOCKED: "LOCKED",
};

export class GameSession {
  constructor(nodeCount, seed) {
    const random = new JavaRandom(seed);
    this._map = NetworkMap.random(nodeCount, random);
    this._unlockedLevel = this._map.minRouterLevel();
    this._solutions = new Map();
    for (const node of this._map.routers()) {
      const full = ShortestPaths.tableFrom(this._map, node.id());
      const hostRows = new Map();
      for (const host of this._map.hosts()) {
        hostRows.set(host.id(), full.get(host.id()));
      }
      this._solutions.set(node.id(), hostRows);
    }
    this._accepted = new Map();
    this._completed = new Set();
    this._playerPath = [];
    this._pendingHops = [];
    this._selectedNode = null;
    this._arrived = false;
    this._packetNode = this._map.sourceId();
    this._playerPath.push(this._packetNode);
    this._optimalPath = [...ShortestPaths.nodePath(this._map, this._map.sourceId(), this._map.destId())];
  }

  map() {
    return this._map;
  }

  selectedNode() {
    return this._selectedNode;
  }

  unlockedLevel() {
    return this._unlockedLevel;
  }

  isUnlocked(nodeId) {
    if (!this._map.node(nodeId).isRouter()) {
      return false;
    }
    return this._map.levelFromSource(nodeId) <= this._unlockedLevel || nodeId === this._packetNode;
  }

  selectNode(nodeId) {
    if (this._map.node(nodeId).isHost()) {
      return SelectResult.HOST;
    }
    if (this._map.levelFromSource(nodeId) > this._unlockedLevel && nodeId !== this._packetNode) {
      return SelectResult.LOCKED;
    }
    this._selectedNode = nodeId;
    return SelectResult.OK;
  }

  isCompleted(nodeId) {
    return this._completed.has(nodeId);
  }

  completedNodes() {
    return this._completed;
  }

  allDone() {
    return this._completed.size === this._map.routers().length;
  }

  remaining() {
    return this._map.routers().length - this._completed.size;
  }

  remainingOnCurrentLevel() {
    let n = 0;
    for (const r of this._map.routers()) {
      if (this._map.levelFromSource(r.id()) === this._unlockedLevel && !this._completed.has(r.id())) {
        n++;
      }
    }
    return n;
  }

  solutionFor(nodeId) {
    return this._solutions.get(nodeId);
  }

  acceptedTable(nodeId) {
    return this._accepted.get(nodeId);
  }

  visibleTable(nodeId) {
    return this.acceptedTable(nodeId);
  }

  packetNode() {
    return this._packetNode;
  }

  packetArrived() {
    return this._arrived;
  }

  playerPath() {
    return this._playerPath;
  }

  optimalPath() {
    return this._optimalPath;
  }

  playerPathSuboptimal() {
    if (!this._arrived) {
      return false;
    }
    return this._playerPath.length > this._optimalPath.length
      || !samePath(this._playerPath, this._optimalPath);
  }

  toldNextHop(routerId) {
    const table = this._accepted.get(routerId);
    if (table == null) {
      return null;
    }
    const e = table.get(this._map.destId());
    return e == null ? null : e.nextHop();
  }

  takePendingHops() {
    const hops = [...this._pendingHops];
    this._pendingHops.length = 0;
    return hops;
  }

  arriveAt(nodeId) {
    this._packetNode = nodeId;
    if (this._playerPath[this._playerPath.length - 1] !== nodeId) {
      this._playerPath.push(nodeId);
    }
    if (nodeId === this._map.destId()) {
      this._arrived = true;
    }
  }

  formatPath(path) {
    return path.map((id) => this._map.node(id).label()).join(" → ");
  }

  check(nodeId, playerTable) {
    if (this._map.node(nodeId).isHost()
        || (this._map.levelFromSource(nodeId) > this._unlockedLevel && nodeId !== this._packetNode)) {
      return CheckResult.INCOMPLETE;
    }
    if (this._completed.has(nodeId)) {
      return CheckResult.CORRECT;
    }
    const expected = this._solutions.get(nodeId);
    const packetDest = this._map.destId();
    for (const dest of expected.keys()) {
      if (playerTable.get(dest) == null) {
        return CheckResult.INCOMPLETE;
      }
    }
    for (const dest of expected.keys()) {
      const p = playerTable.get(dest);
      if (!ShortestPaths.isLegalNextHop(this._map, nodeId, p.nextHop(), dest)) {
        return CheckResult.WRONG_HOP;
      }
    }
    this._completed.add(nodeId);
    this._accepted.set(nodeId, new Map(playerTable));
    const chosen = playerTable.get(packetDest);
    this._unlockIfLevelComplete();
    this._queuePacketHops(nodeId, chosen.nextHop());
    return CheckResult.CORRECT;
  }

  isRowBroken() {
    return false;
  }

  canEditRow(routerId, _destId) {
    return !this.isCompleted(routerId);
  }

  showCost() {
    return false;
  }

  showWeights() {
    return false;
  }

  showPacket() {
    return true;
  }

  failedLink() {
    return null;
  }

  neighborChoices(nodeId) {
    return this._map.neighborIds(nodeId);
  }

  _queuePacketHops(routerId, nextHop) {
    if (this._arrived || nextHop === this._packetNode) {
      return;
    }
    if (this._packetNode === routerId) {
      this._pendingHops.push([routerId, nextHop]);
      return;
    }
    if (this._packetNode === this._map.sourceId() && this._map.neighborIds(this._packetNode).includes(routerId)) {
      this._pendingHops.push([this._packetNode, routerId]);
      if (nextHop !== routerId) {
        this._pendingHops.push([routerId, nextHop]);
      }
    }
  }

  _unlockIfLevelComplete() {
    for (const r of this._map.routers()) {
      if (this._map.levelFromSource(r.id()) === this._unlockedLevel && !this._completed.has(r.id())) {
        return;
      }
    }
    if (this._unlockedLevel < this._map.maxRouterLevel()) {
      this._unlockedLevel++;
    }
  }
}

function samePath(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return false;
    }
  }
  return true;
}
