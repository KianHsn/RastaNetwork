export class RoutingEntry {
  constructor(destination, nextHop, cost = 0) {
    this._destination = destination;
    this._nextHop = nextHop;
    this._cost = cost;
  }

  destination() {
    return this._destination;
  }

  nextHop() {
    return this._nextHop;
  }

  cost() {
    return this._cost;
  }

  equals(other) {
    return other instanceof RoutingEntry
      && this._destination === other._destination
      && this._nextHop === other._nextHop;
  }
}
