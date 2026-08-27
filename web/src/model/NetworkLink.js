export class NetworkLink {
  constructor(a, b, weight = 1) {
    this._a = Math.min(a, b);
    this._b = Math.max(a, b);
    this._weight = weight;
  }

  a() {
    return this._a;
  }

  b() {
    return this._b;
  }

  weight() {
    return this._weight;
  }

  other(nodeId) {
    if (nodeId === this._a) {
      return this._b;
    }
    if (nodeId === this._b) {
      return this._a;
    }
    throw new Error(`Node ${nodeId} is not on this link`);
  }

  connects(nodeId) {
    return nodeId === this._a || nodeId === this._b;
  }

  sameEndpoints(other) {
    return other != null && this._a === other._a && this._b === other._b;
  }

  equals(other) {
    return other instanceof NetworkLink && this._a === other._a && this._b === other._b;
  }
}
