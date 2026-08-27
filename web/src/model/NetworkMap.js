import { shuffle } from "../rng.js";
import { NetworkLink } from "./NetworkLink.js";
import { Kind, NetworkNode } from "./NetworkNode.js";

export const UNREACHABLE = 2147483647;

const HOST_IPS = ["192.168.1.10", "192.168.9.25"];
const ROUTER_IPS = [
  "10.0.0.1", "10.0.0.2", "10.0.1.1", "10.0.1.2",
  "10.0.2.1", "10.0.2.2", "10.0.3.1", "10.0.3.2",
  "10.255.255.1", "10.255.255.2",
];

export class NetworkMap {
  constructor(nodes, links, sourceId, destId) {
    this._nodes = [...nodes];
    this._links = [...links];
    this._sourceId = sourceId;
    this._destId = destId;
    const adj = new Map();
    for (const node of nodes) {
      adj.set(node.id(), []);
    }
    for (const link of links) {
      adj.get(link.a()).push(link);
      adj.get(link.b()).push(link);
    }
    this._adjacency = adj;
    this._levelFromSource = this._bfs(sourceId);
    this._distToDest = this._bfs(destId);
  }

  nodes() {
    return this._nodes;
  }

  links() {
    return this._links;
  }

  sourceId() {
    return this._sourceId;
  }

  destId() {
    return this._destId;
  }

  node(id) {
    return this._nodes[id];
  }

  hosts() {
    return this._nodes.filter((n) => n.isHost());
  }

  routers() {
    return this._nodes.filter((n) => n.isRouter());
  }

  levelFromSource(nodeId) {
    return this._levelFromSource[nodeId];
  }

  distanceToDest(nodeId) {
    return this._distToDest[nodeId];
  }

  minRouterLevel() {
    const levels = this.routers().map((n) => this._levelFromSource[n.id()]);
    return levels.length === 0 ? 1 : Math.min(...levels);
  }

  maxRouterLevel() {
    const levels = this.routers().map((n) => this._levelFromSource[n.id()]);
    return levels.length === 0 ? 1 : Math.max(...levels);
  }

  isConnected() {
    for (let i = 0; i < this._nodes.length; i++) {
      if (this._levelFromSource[i] === UNREACHABLE) {
        return false;
      }
    }
    return true;
  }

  linksOf(nodeId) {
    return [...this._adjacency.get(nodeId)];
  }

  linkBetween(a, b) {
    for (const link of this._links) {
      if (link.connects(a) && link.connects(b)) {
        return link;
      }
    }
    return null;
  }

  weightBetween(a, b) {
    const link = this.linkBetween(a, b);
    if (link == null) {
      throw new Error(`No link between ${a} and ${b}`);
    }
    return link.weight();
  }

  withoutLink(link) {
    const rest = this._links.filter((l) => !l.connects(link.a()) || !l.connects(link.b()));
    return new NetworkMap(this._nodes, rest, this._sourceId, this._destId);
  }

  neighborIds(nodeId) {
    const neighbors = this._adjacency.get(nodeId).map((link) => link.other(nodeId));
    neighbors.sort((a, b) => a - b);
    return neighbors;
  }

  static random(nodeCount, random) {
    if (nodeCount < 6 || nodeCount > 12) {
      throw new Error("Use 6–12 nodes for a playable map");
    }
    for (let attempt = 0; attempt < 60; attempt++) {
      const map = buildRandom(nodeCount, random);
      if (playable(map)) {
        return map;
      }
    }
    return buildRandom(nodeCount, random);
  }

  _bfs(start) {
    const n = this._nodes.length;
    const dist = Array(n).fill(UNREACHABLE);
    const q = [start];
    dist[start] = 0;
    while (q.length > 0) {
      const u = q.shift();
      for (const link of this._adjacency.get(u)) {
        const v = link.other(u);
        if (dist[v] === UNREACHABLE) {
          dist[v] = dist[u] + 1;
          q.push(v);
        }
      }
    }
    return dist;
  }
}

function buildRandom(nodeCount, random) {
  const srcId = 0;
  const dstId = 1;
  const routerCount = nodeCount - 2;
  const layers = 2 + random.nextInt(Math.min(3, routerCount - 1));
  const layerSizes = randomParts(routerCount, layers, random);

  const routerIds = [];
  for (let id = 2; id < nodeCount; id++) {
    routerIds.push(id);
  }
  shuffle(routerIds, random);
  const ips = ROUTER_IPS.slice(0, routerCount);
  shuffle(ips, random);

  const layerIds = [];
  let next = 0;
  for (const size of layerSizes) {
    const layer = routerIds.slice(next, next + size);
    shuffle(layer, random);
    layerIds.push(layer);
    next += size;
  }

  const arr = Array(nodeCount);
  arr[srcId] = new NetworkNode(srcId, HOST_IPS[0], Kind.HOST, {
    x: 70,
    y: 160 + random.nextInt(280),
  });
  arr[dstId] = new NetworkNode(dstId, HOST_IPS[1], Kind.HOST, {
    x: 830,
    y: 160 + random.nextInt(280),
  });

  const left = 210;
  const right = 690;
  for (let l = 0; l < layers; l++) {
    const ids = layerIds[l];
    const baseX = layers === 1 ? 450 : left + Math.trunc(l * (right - left) / Math.max(1, layers - 1));
    for (let i = 0; i < ids.length; i++) {
      const x = baseX + random.nextInt(51) - 25;
      let y;
      if (ids.length === 1) {
        y = 120 + random.nextInt(320);
      } else {
        const span = Math.trunc(420 / Math.max(1, ids.length - 1));
        y = 80 + i * span + random.nextInt(41) - 20;
      }
      y = Math.max(70, Math.min(530, y));
      const id = ids[i];
      arr[id] = new NetworkNode(id, ips[id - 2], Kind.ROUTER, { x, y });
    }
  }
  separateOverlaps(arr, random);

  const used = Array.from({ length: nodeCount }, () => Array(nodeCount).fill(false));
  const links = [];

  attachHost(links, used, srcId, layerIds[0], random);
  attachHost(links, used, dstId, layerIds[layers - 1], random);

  for (let l = 0; l < layers - 1; l++) {
    connectLayers(links, used, layerIds[l], layerIds[l + 1], random);
  }
  const density = random.nextInt(3);
  if (density > 0) {
    for (const layer of layerIds) {
      weaveLayer(links, used, layer, random);
    }
  }
  if (layers >= 3 && density > 0 && random.nextBoolean()) {
    const skipTo = 1 + random.nextInt(layers - 2);
    const skips = 1 + random.nextInt(2);
    for (let i = 0; i < skips; i++) {
      addLink(links, used, pick(layerIds[0], random), pick(layerIds[skipTo], random), random);
    }
  }
  if (layers >= 3 && random.nextInt(4) === 0) {
    addLink(links, used, srcId, pick(layerIds[1], random), random);
  }
  if (layers >= 3 && random.nextInt(4) === 0) {
    addLink(links, used, dstId, pick(layerIds[layers - 2], random), random);
  }

  const extras = density === 0
    ? random.nextInt(2)
    : density === 1
      ? 1 + random.nextInt(3)
      : 2 + random.nextInt(1 + Math.trunc(routerCount / 2));
  const allRouters = [...routerIds];
  for (let n = 0; n < extras; n++) {
    addLink(links, used, pick(allRouters, random), pick(allRouters, random), random);
  }
  for (const r of routerIds) {
    if (degree(used, r) <= 1) {
      addLink(links, used, r, pick(allRouters, random), random);
    }
  }
  return new NetworkMap(arr, links, srcId, dstId);
}

function playable(map) {
  for (const node of map.nodes()) {
    if (map.levelFromSource(node.id()) === UNREACHABLE
        || map.distanceToDest(node.id()) === UNREACHABLE) {
      return false;
    }
  }
  return map.distanceToDest(map.sourceId()) >= 3;
}

function randomParts(total, parts, random) {
  const sizes = Array(parts).fill(1);
  let leftover = total - parts;
  while (leftover-- > 0) {
    sizes[random.nextInt(parts)]++;
  }
  return sizes;
}

function attachHost(links, used, hostId, layer, random) {
  const order = [...layer];
  shuffle(order, random);
  const n = 1 + random.nextInt(order.length);
  for (let i = 0; i < n; i++) {
    addLink(links, used, hostId, order[i], random);
  }
  for (let i = n; i < order.length; i++) {
    addLink(links, used, order[i], order[random.nextInt(n)], random);
  }
}

function connectLayers(links, used, a, b, random) {
  for (const u of a) {
    addLink(links, used, u, pick(b, random), random);
  }
  for (const v of b) {
    let linked = false;
    for (const u of a) {
      if (used[u][v]) {
        linked = true;
        break;
      }
    }
    if (!linked) {
      addLink(links, used, pick(a, random), v, random);
    }
  }
  const extra = random.nextInt(1 + Math.min(a.length, b.length));
  for (let i = 0; i < extra; i++) {
    addLink(links, used, pick(a, random), pick(b, random), random);
  }
}

function weaveLayer(links, used, layer, random) {
  if (layer.length < 2) {
    return;
  }
  const order = [...layer];
  shuffle(order, random);
  if (random.nextInt(4) !== 0) {
    for (let i = 0; i < order.length - 1; i++) {
      addLink(links, used, order[i], order[i + 1], random);
    }
  }
  if (layer.length >= 3 && random.nextBoolean()) {
    addLink(links, used, pick(layer, random), pick(layer, random), random);
  }
}

function separateOverlaps(arr, random) {
  for (let pass = 0; pass < 12; pass++) {
    let moved = false;
    for (let i = 2; i < arr.length; i++) {
      const a = arr[i].position();
      for (let j = 2; j < arr.length; j++) {
        if (i === j) {
          continue;
        }
        const b = arr[j].position();
        if (Math.hypot(a.x - b.x, a.y - b.y) < 78) {
          a.y = Math.max(70, Math.min(530, a.y + (random.nextBoolean() ? 28 : -28)));
          moved = true;
        }
      }
    }
    if (!moved) {
      return;
    }
  }
}

function pick(ids, random) {
  return ids[random.nextInt(ids.length)];
}

function degree(used, node) {
  let d = 0;
  for (let i = 0; i < used.length; i++) {
    if (used[node][i]) {
      d++;
    }
  }
  return d;
}

function addLink(links, used, a, b, random) {
  if (a === b || used[a][b]) {
    return false;
  }
  used[a][b] = true;
  used[b][a] = true;
  links.push(new NetworkLink(a, b, 1 + random.nextInt(9)));
  return true;
}
