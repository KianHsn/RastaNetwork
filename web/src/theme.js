export class Color {
  constructor(r, g, b, a = 255) {
    this.r = r;
    this.g = g;
    this.b = b;
    this.a = a;
  }

  alpha(a) {
    return new Color(this.r, this.g, this.b, Math.max(0, Math.min(255, Math.round(a))));
  }

  mix(other, t) {
    t = Math.max(0, Math.min(1, t));
    return new Color(
      Math.round(this.r + (other.r - this.r) * t),
      Math.round(this.g + (other.g - this.g) * t),
      Math.round(this.b + (other.b - this.b) * t),
    );
  }

  css() {
    return `rgba(${this.r}, ${this.g}, ${this.b}, ${this.a / 255})`;
  }
}

export const WHITE = new Color(255, 255, 255);
export const BLACK = new Color(0, 0, 0);

export const Theme = {
  BG_DEEP: new Color(6, 10, 22),
  BG_APP: new Color(10, 14, 28),
  BG_PANEL: new Color(14, 20, 38),
  BG_CARD: new Color(20, 28, 50),
  BG_ELEVATED: new Color(28, 38, 64),
  STROKE: new Color(70, 92, 140),
  TEXT: new Color(236, 242, 255),
  TEXT_MUTED: new Color(164, 180, 210),
  CYAN: new Color(64, 214, 255),
  VIOLET: new Color(168, 132, 255),
  LIME: new Color(72, 226, 156),
  AMBER: new Color(255, 176, 64),
  ROSE: new Color(255, 92, 118),
  GOLD: new Color(255, 224, 96),
  HOST: new Color(255, 138, 64),
  LINK: new Color(86, 118, 178),
};

export function mix(a, b, t) {
  return a.mix(b, t);
}
