export const Kind = {
  HOST: "HOST",
  ROUTER: "ROUTER",
};

export class NetworkNode {
  constructor(id, label, kind, position) {
    this._id = id;
    this._label = label;
    this._kind = kind;
    this._position = position;
  }

  id() {
    return this._id;
  }

  label() {
    return this._label;
  }

  kind() {
    return this._kind;
  }

  isHost() {
    return this._kind === Kind.HOST;
  }

  isRouter() {
    return this._kind === Kind.ROUTER;
  }

  position() {
    return this._position;
  }

  contains(p, routerRadius) {
    if (this.isHost()) {
      return Math.abs(p.x - this._position.x) <= 52 && Math.abs(p.y - this._position.y) <= 20;
    }
    const dx = p.x - this._position.x;
    const dy = p.y - this._position.y;
    return Math.hypot(dx, dy) <= routerRadius;
  }

  toString() {
    return this._label;
  }
}
