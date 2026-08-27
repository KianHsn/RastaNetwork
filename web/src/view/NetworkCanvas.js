import { BLACK, Color, mix, Theme, WHITE } from "../theme.js";
import { JavaRandom } from "../rng.js";

const RADIUS = 28;
const PACKET_R = 11;
const VIEW_W = 900;
const VIEW_H = 640;
const OPTIMAL = new Color(70, 230, 150);
const PLAYER = new Color(255, 148, 56);
const PREVIEW = new Color(140, 196, 255);
const PACKET = new Color(255, 230, 96);
const FAILED = new Color(255, 82, 108);

export class NetworkCanvas {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.session = null;
    this.onNodeClicked = () => {};
    this.onLinkClicked = () => {};
    this.hopQueue = [];
    this.onHopArrived = () => {};
    this.onAllHopsDone = () => {};
    this.animFrom = -1;
    this.animTo = -1;
    this.t = 0;
    this.preview = false;
    this.scale = 1;
    this.ox = 0;
    this.oy = 0;
    this.hoveredNode = null;
    this.hoveredLink = null;
    this.mouse = { x: -1, y: -1 };
    this.nodeHover = new Map();
    this.pulse = 0;
    this.dashPhase = 0;
    this.clickPulse = 0;
    this.clickNode = null;
    this.bannerTitle = "";
    this.bannerSub = "";
    this.toast = "";
    this.toastAccent = Theme.CYAN;
    this.toastLife = 0;
    this.stars = [];
    this.starW = 0;
    this.starH = 0;
    this.running = false;
    this._viewW = 0;
    this._viewH = 0;
    this._bindEvents();
    this._observeSize();
  }

  start() {
    if (this.running) {
      return;
    }
    this.running = true;
    const loop = () => {
      if (!this.running) {
        return;
      }
      try {
        this.tick();
      } catch (err) {
        console.error(err);
      }
      requestAnimationFrame(loop);
    };
    requestAnimationFrame(loop);
  }

  stop() {
    this.running = false;
  }

  setSession(session) {
    this.stopAnimation();
    this.session = session;
    this.nodeHover.clear();
    this.hoveredNode = null;
    this.hoveredLink = null;
    this.clickPulse = 0;
    this.toastLife = 0;
  }

  setBanner(title, subtitle) {
    this.bannerTitle = title ?? "";
    this.bannerSub = subtitle ?? "";
  }

  showToast(message, accent) {
    this.toast = message ?? "";
    this.toastAccent = accent ?? Theme.CYAN;
    this.toastLife = 1;
  }

  isAnimating() {
    return this.animFrom >= 0;
  }

  animateHops(hops, onHopArrived, onAllHopsDone) {
    this.stopAnimation();
    this.preview = false;
    this.onHopArrived = onHopArrived;
    this.onAllHopsDone = onAllHopsDone ?? (() => {});
    this.hopQueue = [...hops];
    this.startNextHop();
  }

  animatePreview(from, to) {
    this.stopAnimation();
    this.preview = true;
    this.onHopArrived = () => {};
    this.onAllHopsDone = () => {};
    this.hopQueue = [[from, to]];
    this.startNextHop();
  }

  _bindEvents() {
    const canvas = this.canvas;
    canvas.addEventListener("click", (e) => {
      if (this.session == null || this.isAnimating()) {
        return;
      }
      const p = this._eventPoint(e);
      const hit = this.hitTest(p);
      if (hit != null) {
        this.clickNode = hit;
        this.clickPulse = 1;
        this.onNodeClicked(hit);
        return;
      }
      const link = this.hitTestLink(p);
      if (link != null) {
        this.onLinkClicked(link);
      }
    });
    canvas.addEventListener("mousemove", (e) => {
      const p = this._eventPoint(e);
      this.mouse = p;
      this.updateHover(p);
    });
    canvas.addEventListener("mouseleave", () => {
      this.hoveredNode = null;
      this.hoveredLink = null;
      canvas.style.cursor = "default";
    });
  }

  _eventPoint(e) {
    const rect = this.canvas.getBoundingClientRect();
    const w = this._viewW || rect.width || 1;
    const h = this._viewH || rect.height || 1;
    return {
      x: (e.clientX - rect.left) * (w / Math.max(1, rect.width)),
      y: (e.clientY - rect.top) * (h / Math.max(1, rect.height)),
    };
  }

  _observeSize() {
    const wrap = this.canvas.parentElement;
    if (wrap == null || typeof ResizeObserver === "undefined") {
      return;
    }
    this._ro = new ResizeObserver(() => {
      if (this.running) {
        this.paint();
      }
    });
    this._ro.observe(wrap);
  }

  tick() {
    this.pulse += 0.07;
    this.dashPhase += 0.55;
    if (this.clickPulse > 0.01) {
      this.clickPulse *= 0.88;
    } else {
      this.clickPulse = 0;
    }
    if (this.toastLife > 0) {
      this.toastLife -= 0.0085;
      if (this.toastLife < 0) {
        this.toastLife = 0;
      }
    }
    if (this.session != null) {
      for (const node of this.session.map().nodes()) {
        const target = this.hoveredNode != null && this.hoveredNode === node.id() ? 1 : 0;
        let cur = this.nodeHover.get(node.id()) ?? 0;
        cur += (target - cur) * 0.28;
        this.nodeHover.set(node.id(), cur);
      }
    }
    if (this.animFrom >= 0) {
      this.t += 0.032;
      if (this.t >= 1) {
        this.t = 1;
        const arrivedId = this.animTo;
        if (!this.preview) {
          this.onHopArrived(arrivedId);
        }
        this.startNextHop();
        return;
      }
    }
    this.paint();
  }

  startNextHop() {
    if (this.hopQueue.length === 0) {
      this.animFrom = this.animTo = -1;
      this.onAllHopsDone();
      this.paint();
      return;
    }
    const hop = this.hopQueue.shift();
    this.animFrom = hop[0];
    this.animTo = hop[1];
    this.t = 0;
    this.paint();
  }

  stopAnimation() {
    this.hopQueue = [];
    this.animFrom = this.animTo = -1;
    this.t = 0;
    this.preview = false;
    this.onHopArrived = () => {};
    this.onAllHopsDone = () => {};
  }

  updateHover(p) {
    if (this.session == null || this.isAnimating()) {
      this.hoveredNode = null;
      this.hoveredLink = null;
      return;
    }
    this.hoveredNode = this.hitTest(p);
    this.hoveredLink = this.hoveredNode == null ? this.hitTestLink(p) : null;
    const hot = this.hoveredNode != null || this.hoveredLink != null;
    this.canvas.style.cursor = hot ? "pointer" : "default";
  }

  updateTransform(map) {
    const box = layoutBounds(map);
    const legend = 52;
    const pad = 28;
    const w = this._cssWidth();
    const h = this._cssHeight();
    const availW = Math.max(1, w - pad * 2);
    const availH = Math.max(1, h - pad * 2 - legend - 36);
    this.scale = Math.min(availW / box.width, availH / box.height);
    this.scale = Math.max(0.55, Math.min(this.scale, 1.45));
    const drawnW = Math.round(box.width * this.scale);
    const drawnH = Math.round(box.height * this.scale);
    this.ox = (w - drawnW) / 2 - Math.round(box.x * this.scale);
    this.oy = (h - legend - drawnH) / 2 - Math.round(box.y * this.scale) + 10;
  }

  viewPoint(layout) {
    return {
      x: this.ox + Math.round(layout.x * this.scale),
      y: this.oy + Math.round(layout.y * this.scale),
    };
  }

  viewRadius() {
    return Math.max(20, Math.round(RADIUS * this.scale));
  }

  hitTest(p) {
    const r = this.viewRadius();
    for (const node of this.session.map().nodes()) {
      const vp = this.viewPoint(node.position());
      const h = this.nodeHover.get(node.id()) ?? 0;
      if (node.isHost()) {
        const w = Math.max(48, Math.round(56 * this.scale * (1 + 0.08 * h)));
        const ht = Math.max(16, Math.round(20 * this.scale * (1 + 0.08 * h)));
        if (Math.abs(p.x - vp.x) <= w && Math.abs(p.y - vp.y) <= ht) {
          return node.id();
        }
      } else if (Math.hypot(p.x - vp.x, p.y - vp.y) <= r * (1.08 + 0.12 * h)) {
        return node.id();
      }
    }
    return null;
  }

  hitTestLink(p) {
    let best = null;
    let bestDist = 12;
    for (const link of this.session.map().links()) {
      const a = this.viewPoint(this.session.map().node(link.a()).position());
      const b = this.viewPoint(this.session.map().node(link.b()).position());
      const d = distanceToSegment(p, a, b);
      if (d < bestDist) {
        bestDist = d;
        best = link;
      }
    }
    return best;
  }

  _cssWidth() {
    return VIEW_W;
  }

  _cssHeight() {
    return VIEW_H;
  }

  _resize() {
    const wrap = this.canvas.parentElement;
    const dpr = window.devicePixelRatio || 1;
    const ww = Math.max(1, wrap && wrap.clientWidth > 0 ? wrap.clientWidth : VIEW_W);
    const wh = Math.max(1, wrap && wrap.clientHeight > 0 ? wrap.clientHeight : VIEW_H);
    const fit = Math.min(ww / VIEW_W, wh / VIEW_H);
    const dw = Math.max(1, Math.round(VIEW_W * fit));
    const dh = Math.max(1, Math.round(VIEW_H * fit));
    this._viewW = VIEW_W;
    this._viewH = VIEW_H;
    if (this.canvas.style.width !== `${dw}px`) {
      this.canvas.style.width = `${dw}px`;
    }
    if (this.canvas.style.height !== `${dh}px`) {
      this.canvas.style.height = `${dh}px`;
    }
    const pw = Math.max(1, Math.round(VIEW_W * dpr));
    const ph = Math.max(1, Math.round(VIEW_H * dpr));
    if (this.canvas.width !== pw || this.canvas.height !== ph) {
      this.canvas.width = pw;
      this.canvas.height = ph;
    }
    const g = this.ctx;
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.imageSmoothingEnabled = true;
  }

  paint() {
    this._resize();
    const g = this.ctx;
    const w = this._cssWidth();
    const h = this._cssHeight();
    g.setLineDash([]);
    g.lineDashOffset = 0;
    g.globalAlpha = 1;
    g.lineCap = "butt";
    g.lineJoin = "round";
    g.miterLimit = 10;
    this.paintBackdrop(g, w, h);
    if (this.session == null) {
      g.fillStyle = Theme.TEXT_MUTED.css();
      g.font = "16px 'Segoe UI', sans-serif";
      g.fillText("Start a game to generate a random network.", 40, 56);
      return;
    }
    const map = this.session.map();
    this.updateTransform(map);
    const arrived = this.session.packetArrived();
    const suboptimal = arrived && this.session.playerPathSuboptimal();
    const r = this.viewRadius();
    const failed = this.session.failedLink();
    const accent = this.session.showWeights() ? Theme.VIOLET : Theme.CYAN;

    this.paintLinks(g, map, failed, arrived, suboptimal, accent);
    this.paintNeighborHints(g, map);
    this.paintNodes(g, map, r, accent);
    if (this.session.showPacket()) {
      this.paintPacket(g, map);
    }
    this.paintBanner(g, accent, w);
    this.paintLegend(g, suboptimal, h);
    this.paintHoverTip(g, map, w);
    this.paintToast(g, w, h);
  }

  paintBackdrop(g, w, h) {
    const grad = g.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, "rgb(8, 14, 32)");
    grad.addColorStop(1, "rgb(4, 8, 18)");
    g.fillStyle = grad;
    g.fillRect(0, 0, w, h);
    this.ensureStars(w, h);
    for (const star of this.stars) {
      let a = 40 + star[2] + Math.round(18 * Math.sin(this.pulse * 0.6 + star[0]));
      a = Math.max(20, Math.min(160, a));
      g.fillStyle = WHITE.alpha(a).css();
      g.beginPath();
      g.arc(star[0], star[1], star[3] / 2, 0, Math.PI * 2);
      g.fill();
    }
    g.strokeStyle = Theme.CYAN.alpha(18).css();
    g.lineWidth = 1;
    const step = 36;
    g.beginPath();
    for (let x = 0; x < w; x += step) {
      g.moveTo(x, 0);
      g.lineTo(x, h);
    }
    for (let y = 0; y < h; y += step) {
      g.moveTo(0, y);
      g.lineTo(w, y);
    }
    g.stroke();
    const vignette = g.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, Math.max(w, h) * 0.72);
    vignette.addColorStop(0.55, BLACK.alpha(0).css());
    vignette.addColorStop(1, BLACK.alpha(140).css());
    g.fillStyle = vignette;
    g.fillRect(0, 0, w, h);
  }

  ensureStars(w, h) {
    if (this.stars.length > 0 && this.starW === w && this.starH === h) {
      return;
    }
    this.starW = w;
    this.starH = h;
    const rng = new JavaRandom(17);
    this.stars = [];
    for (let i = 0; i < 90; i++) {
      this.stars.push([
        rng.nextInt(Math.max(1, Math.floor(w))),
        rng.nextInt(Math.max(1, Math.floor(h))),
        rng.nextInt(80),
        rng.nextBoolean() ? 2 : 1,
      ]);
    }
  }

  paintLinks(g, map, failed, arrived, suboptimal, accent) {
    let i = 0;
    for (const link of map.links()) {
      const a = this.viewPoint(map.node(link.a()).position());
      const b = this.viewPoint(map.node(link.b()).position());
      const isFailed = failed != null && failed.sameEndpoints(link);
      const hovered = this.hoveredLink != null && this.hoveredLink.sameEndpoints(link);
      if (isFailed) {
        this.drawGlowLine(g, a, b, FAILED, 5.5, 14);
        g.setLineDash([12, 9]);
        g.lineDashOffset = -this.dashPhase;
        g.lineWidth = 4.8;
        g.lineCap = "round";
        g.strokeStyle = FAILED.css();
        g.beginPath();
        g.moveTo(a.x, a.y);
        g.lineTo(b.x, b.y);
        g.stroke();
        g.setLineDash([]);
        g.lineDashOffset = 0;
      } else {
        const onPlayer = containsEdge(this.session.playerPath(), link);
        const onOptimal = arrived && containsEdge(this.session.optimalPath(), link);
        const onPreview = this.preview && this.animFrom >= 0
          && link.connects(this.animFrom) && link.connects(this.animTo);
        const onAnim = !this.preview && this.animFrom >= 0
          && link.connects(this.animFrom) && link.connects(this.animTo);
        const base = Theme.LINK;
        const width = hovered ? 5.2 : 3.4;
        if (suboptimal && onOptimal) {
          g.setLineDash([14, 10]);
          g.lineDashOffset = -this.dashPhase;
          g.lineWidth = 8.5;
          g.lineCap = "round";
          g.strokeStyle = OPTIMAL.alpha(220).css();
          g.beginPath();
          g.moveTo(a.x, a.y);
          g.lineTo(b.x, b.y);
          g.stroke();
          g.setLineDash([]);
          g.lineDashOffset = 0;
        }
        if (onPlayer && this.session.playerPath().length > 1) {
          const path = arrived && !suboptimal ? OPTIMAL : PLAYER;
          this.drawGlowLine(g, a, b, path, suboptimal && onOptimal ? 5 : 7, 12);
        }
        if (onAnim || onPreview) {
          this.drawGlowLine(g, a, b, onPreview ? PREVIEW : PACKET, 6.5, 16);
        }
        if (!onPlayer && !onOptimal && !onAnim && !onPreview) {
          const line = hovered ? mix(base, accent, 0.55) : base;
          this.drawGlowLine(g, a, b, line, width, hovered ? 10 : 5);
          this.paintFlowDot(g, a, b, i, hovered ? accent : accent.alpha(120));
        }
      }
      if (this.session.showWeights()) {
        this.drawWeight(g, a, b, link.weight(), isFailed, hovered);
      }
      i++;
    }
  }

  paintFlowDot(g, a, b, idx, color) {
    const u = (Math.sin(this.pulse * 0.85 + idx * 0.9) + 1) * 0.5;
    const x = Math.round(a.x + (b.x - a.x) * u);
    const y = Math.round(a.y + (b.y - a.y) * u);
    g.fillStyle = color.alpha(160).css();
    g.beginPath();
    g.arc(x, y, 3, 0, Math.PI * 2);
    g.fill();
  }

  paintNeighborHints(g, map) {
    const selected = this.session.selectedNode();
    if (selected == null) {
      return;
    }
    const told = this.session.toldNextHop(selected);
    const s = this.viewPoint(map.node(selected).position());
    for (const n of this.session.neighborChoices(selected)) {
      const d = this.viewPoint(map.node(n).position());
      const chosen = told != null && told === n;
      const c = chosen ? PACKET : PREVIEW;
      if (chosen) {
        g.setLineDash([]);
      } else {
        g.setLineDash([7, 6]);
        g.lineDashOffset = -this.dashPhase * 0.4;
      }
      g.lineWidth = chosen ? 5 : 2.6;
      g.lineCap = "round";
      g.strokeStyle = c.alpha(chosen ? 230 : 160).css();
      g.beginPath();
      g.moveTo(s.x, s.y);
      g.lineTo(d.x, d.y);
      g.stroke();
      g.setLineDash([]);
      g.lineDashOffset = 0;
    }
  }

  paintNodes(g, map, r, accent) {
    const selected = this.session.selectedNode();
    for (const node of map.nodes()) {
      const done = this.session.isCompleted(node.id());
      const sel = selected != null && selected === node.id();
      const locked = node.isRouter() && !this.session.isUnlocked(node.id());
      const p = this.viewPoint(node.position());
      const h = this.nodeHover.get(node.id()) ?? 0;
      if (node.isHost()) {
        this.paintHost(g, map, node, p, sel, h);
      } else {
        this.paintRouter(g, node, p, r, done, sel, locked, h, accent);
      }
    }
  }

  paintRouter(g, node, p, r, done, sel, locked, h, accent) {
    const rr = Math.round(r * (1 + 0.14 * h));
    let fill;
    let glow;
    if (locked) {
      fill = new Color(26, 32, 48);
      glow = new Color(70, 80, 100);
    } else if (done) {
      fill = new Color(28, 150, 108);
      glow = Theme.LIME;
    } else if (sel) {
      fill = new Color(64, 118, 255);
      glow = accent;
    } else {
      fill = new Color(46, 68, 122);
      glow = Theme.LINK;
    }
    const breathe = locked ? 0 : 0.5 + 0.5 * Math.sin(this.pulse + node.id());
    const ga = 50 + Math.round(50 * h) + Math.round(30 * breathe);
    g.fillStyle = glow.alpha(ga).css();
    g.beginPath();
    g.arc(p.x, p.y, rr + 10, 0, Math.PI * 2);
    g.fill();
    if (sel) {
      g.setLineDash([8, 7]);
      g.lineDashOffset = -this.dashPhase;
      g.lineWidth = 2.4;
      g.strokeStyle = WHITE.alpha(220).css();
      const ring = rr + 8 + Math.round(3 * Math.sin(this.pulse * 1.4));
      g.beginPath();
      g.arc(p.x, p.y, ring, 0, Math.PI * 2);
      g.stroke();
      g.setLineDash([]);
      g.lineDashOffset = 0;
    }
    if (this.clickNode != null && this.clickNode === node.id() && this.clickPulse > 0.05) {
      const cr = rr + Math.round(18 * (1 - this.clickPulse));
      g.strokeStyle = WHITE.alpha(140 * this.clickPulse).css();
      g.lineWidth = 2.5;
      g.beginPath();
      g.arc(p.x, p.y, cr, 0, Math.PI * 2);
      g.stroke();
    }
    const ball = g.createRadialGradient(
      p.x - rr * 0.28, p.y - rr * 0.32, 0,
      p.x - rr * 0.28, p.y - rr * 0.32, rr * 1.35,
    );
    ball.addColorStop(0, mix(fill, WHITE, 0.45).css());
    ball.addColorStop(0.55, fill.css());
    ball.addColorStop(1, mix(fill, BLACK, 0.28).css());
    g.fillStyle = ball;
    g.beginPath();
    g.arc(p.x, p.y, rr, 0, Math.PI * 2);
    g.fill();
    g.lineWidth = sel ? 3.2 : 2;
    g.strokeStyle = (locked ? new Color(90, 100, 122) : sel ? WHITE : mix(fill, WHITE, 0.55)).css();
    g.stroke();
    g.fillStyle = WHITE.alpha(locked ? 40 : 70).css();
    g.beginPath();
    g.ellipse(p.x, p.y - rr * 0.32, rr * 0.45, rr * 0.225, 0, 0, Math.PI * 2);
    g.fill();
    if (locked) {
      g.lineWidth = 2.2;
      g.strokeStyle = "rgb(150, 160, 180)";
      g.strokeRect(p.x - 6, p.y - 2, 12, 9);
      g.beginPath();
      g.arc(p.x, p.y - 4, 4, Math.PI, 0);
      g.stroke();
    }
    g.fillStyle = (locked ? new Color(140, 150, 168) : WHITE).css();
    g.font = "bold 12.5px 'Segoe UI', sans-serif";
    const tw = g.measureText(node.label()).width;
    g.fillText(node.label(), p.x - tw / 2, p.y + rr + 16);
  }

  paintHost(g, map, node, p, sel, h) {
    const w = Math.max(100, Math.round(116 * this.scale * (1 + 0.08 * h)));
    const ht = Math.max(32, Math.round(38 * this.scale * (1 + 0.06 * h)));
    const isSrc = node.id() === map.sourceId();
    const fill = isSrc ? new Color(28, 122, 168) : new Color(196, 102, 36);
    const rim = isSrc ? Theme.CYAN : Theme.HOST;
    g.fillStyle = rim.alpha(70 + Math.round(50 * h)).css();
    g.fillRect(p.x - w / 2 - 4, p.y - ht / 2 - 4, w + 8, ht + 8);
    const grad = g.createLinearGradient(p.x, p.y - ht / 2, p.x, p.y + ht / 2);
    grad.addColorStop(0, mix(fill, WHITE, 0.22).css());
    grad.addColorStop(1, fill.css());
    g.fillStyle = grad;
    g.fillRect(p.x - w / 2, p.y - ht / 2, w, ht);
    g.lineWidth = sel ? 3.2 : 2.1;
    g.strokeStyle = (sel ? WHITE : rim).css();
    g.strokeRect(p.x - w / 2, p.y - ht / 2, w, ht);
    g.fillStyle = rim.css();
    g.beginPath();
    g.arc(p.x - w / 2 + 15, p.y, 5, 0, Math.PI * 2);
    g.fill();
    g.fillStyle = WHITE.css();
    g.font = "bold 12px 'Segoe UI', sans-serif";
    const tw = g.measureText(node.label()).width;
    g.fillText(node.label(), p.x - tw / 2 + 6, p.y + 4);
  }

  paintPacket(g, map) {
    const pkt = this.packetPosition(map);
    if (pkt == null) {
      return;
    }
    const pr = Math.max(9, Math.round(PACKET_R * this.scale));
    const beat = 1 + 0.12 * Math.sin(this.pulse * 2.2);
    const r = Math.round(pr * beat);
    if (this.animFrom >= 0) {
      const a = this.viewPoint(map.node(this.animFrom).position());
      const b = this.viewPoint(map.node(this.animTo).position());
      for (let i = 1; i <= 6; i++) {
        const tt = Math.max(0, ease(this.t) - i * 0.055);
        const x = Math.round(a.x + (b.x - a.x) * tt);
        const y = Math.round(a.y + (b.y - a.y) * tt);
        g.fillStyle = PACKET.alpha(90 - i * 12).css();
        const tr = Math.max(3, r - i * 2);
        g.beginPath();
        g.arc(x, y, tr, 0, Math.PI * 2);
        g.fill();
      }
    }
    const glow = g.createRadialGradient(pkt.x, pkt.y, 0, pkt.x, pkt.y, r * 3.2);
    glow.addColorStop(0, PACKET.alpha(150).css());
    glow.addColorStop(1, PACKET.alpha(0).css());
    g.fillStyle = glow;
    g.beginPath();
    g.arc(pkt.x, pkt.y, r * 3.2, 0, Math.PI * 2);
    g.fill();
    const core = g.createRadialGradient(pkt.x - 3, pkt.y - 4, 0, pkt.x - 3, pkt.y - 4, r * 1.2);
    core.addColorStop(0, WHITE.css());
    core.addColorStop(1, PACKET.css());
    g.fillStyle = core;
    g.beginPath();
    g.arc(pkt.x, pkt.y, r, 0, Math.PI * 2);
    g.fill();
    g.lineWidth = 2;
    g.strokeStyle = WHITE.css();
    g.stroke();
    g.font = "bold 9px 'Segoe UI', sans-serif";
    g.fillStyle = "rgb(30, 24, 8)";
    g.fillText("PKT", pkt.x - 10, pkt.y + 3);
  }

  paintBanner(g, accent, width) {
    if (!this.bannerTitle.trim()) {
      return;
    }
    g.font = "bold 14px 'Segoe UI', sans-serif";
    const line = !this.bannerSub.trim() ? this.bannerTitle : `${this.bannerTitle}   ·   ${this.bannerSub}`;
    const tw = g.measureText(line).width;
    const x = (width - tw - 28) / 2;
    const y = 10;
    g.fillStyle = Theme.BG_CARD.alpha(220).css();
    g.fillRect(x, y, tw + 28, 26);
    g.strokeStyle = accent.css();
    g.lineWidth = 1;
    g.strokeRect(x, y, tw + 28, 26);
    g.fillStyle = Theme.TEXT.css();
    g.fillText(line, x + 14, y + 18);
  }

  paintLegend(g, suboptimal, height) {
    const y = height - 18;
    let x = 16;
    x = this.legendChip(g, x, y, Theme.CYAN, "Source");
    x = this.legendChip(g, x, y, Theme.HOST, "Destination");
    if (this.session.showWeights()) {
      x = this.legendChip(g, x, y, Theme.TEXT_MUTED, "Weights");
      this.legendChip(g, x, y, FAILED, "Failed link");
    } else {
      x = this.legendChip(g, x, y, Theme.LIME, "Table done");
      if (suboptimal) {
        x = this.legendChip(g, x, y, PLAYER, "Your path");
        this.legendChip(g, x, y, OPTIMAL, "Shortest");
      }
    }
  }

  legendChip(g, x, y, color, text) {
    g.font = "bold 11.5px 'Segoe UI', sans-serif";
    const tw = g.measureText(text).width;
    g.fillStyle = Theme.BG_CARD.alpha(210).css();
    g.fillRect(x, y - 14, tw + 26, 20);
    g.strokeStyle = Theme.STROKE.css();
    g.lineWidth = 1;
    g.strokeRect(x, y - 14, tw + 26, 20);
    g.fillStyle = color.css();
    g.fillRect(x + 6, y - 8, 8, 8);
    g.fillStyle = Theme.TEXT.css();
    g.fillText(text, x + 18, y + 1);
    return x + tw + 34;
  }

  paintHoverTip(g, map, width) {
    let tip = null;
    if (this.hoveredNode != null) {
      const node = map.node(this.hoveredNode);
      if (node.isHost()) {
        tip = `${node.label()}  ·  ${node.id() === map.sourceId() ? "source host" : "destination host"}`;
      } else {
        const state = !this.session.isUnlocked(node.id()) ? "locked"
          : this.session.isCompleted(node.id()) ? "accepted"
            : "ready";
        tip = `${node.label()}  ·  router  ·  ${state}  ·  hop ${map.levelFromSource(node.id())}`;
      }
    } else if (this.hoveredLink != null) {
      const ends = `${map.node(this.hoveredLink.a()).label()} — ${map.node(this.hoveredLink.b()).label()}`;
      if (this.session.showWeights()) {
        tip = `${ends}   w=${this.hoveredLink.weight()}${this.session.failedLink() == null ? "   ·  click to fail" : ""}`;
      } else {
        tip = ends;
      }
    }
    if (tip == null || this.mouse.x < 0) {
      return;
    }
    g.font = "bold 12px 'Segoe UI', sans-serif";
    const tw = g.measureText(tip).width;
    const x = Math.min(width - tw - 24, this.mouse.x + 16);
    const y = Math.max(40, this.mouse.y - 28);
    g.fillStyle = new Color(12, 18, 34).alpha(235).css();
    g.fillRect(x, y, tw + 18, 24);
    g.strokeStyle = Theme.STROKE.css();
    g.lineWidth = 1;
    g.strokeRect(x, y, tw + 18, 24);
    g.fillStyle = Theme.TEXT.css();
    g.fillText(tip, x + 9, y + 16);
  }

  paintToast(g, width, height) {
    if (this.toastLife <= 0 || !this.toast.trim()) {
      return;
    }
    const fade = this.toastLife > 0.2 ? 1 : this.toastLife / 0.2;
    g.save();
    g.globalAlpha = fade;
    g.font = "bold 14px 'Segoe UI', sans-serif";
    const tw = g.measureText(this.toast).width;
    const w = tw + 36;
    const x = (width - w) / 2;
    const y = height - 58;
    g.fillStyle = Theme.BG_ELEVATED.alpha(240).css();
    g.fillRect(x, y, w, 28);
    g.strokeStyle = this.toastAccent.css();
    g.lineWidth = 1.5;
    g.strokeRect(x, y, w, 28);
    g.fillStyle = Theme.TEXT.css();
    g.fillText(this.toast, x + 18, y + 19);
    g.restore();
  }

  drawGlowLine(g, a, b, c, width, glow) {
    g.lineCap = "round";
    g.lineJoin = "round";
    g.lineWidth = width + glow;
    g.strokeStyle = c.alpha(40).css();
    g.beginPath();
    g.moveTo(a.x, a.y);
    g.lineTo(b.x, b.y);
    g.stroke();
    g.lineWidth = width;
    g.strokeStyle = c.css();
    g.beginPath();
    g.moveTo(a.x, a.y);
    g.lineTo(b.x, b.y);
    g.stroke();
  }

  drawWeight(g, a, b, weight, failed, hovered) {
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const len = Math.hypot(dx, dy);
    let px = 0;
    let py = 0;
    if (len > 1) {
      px = -dy / len * 14;
      py = dx / len * 14;
    }
    const x = Math.round((a.x + b.x) / 2 + px);
    const y = Math.round((a.y + b.y) / 2 + py);
    const text = String(weight);
    g.font = `bold ${hovered ? 13 : 11.5}px 'Segoe UI', sans-serif`;
    const tw = g.measureText(text).width;
    g.fillStyle = Theme.BG_DEEP.alpha(230).css();
    g.fillRect(x - tw / 2 - 5, y - 9, tw + 10, 16);
    g.strokeStyle = (failed ? FAILED : hovered ? Theme.GOLD : new Color(230, 236, 250)).css();
    g.lineWidth = 1;
    g.strokeRect(x - tw / 2 - 5, y - 9, tw + 10, 16);
    g.fillStyle = g.strokeStyle;
    g.fillText(text, x - tw / 2, y + 4);
  }

  packetPosition(map) {
    if (this.animFrom >= 0 && this.animTo >= 0) {
      const a = this.viewPoint(map.node(this.animFrom).position());
      const b = this.viewPoint(map.node(this.animTo).position());
      const e = ease(this.t);
      return {
        x: Math.round(a.x + (b.x - a.x) * e),
        y: Math.round(a.y + (b.y - a.y) * e),
      };
    }
    return this.viewPoint(map.node(this.session.packetNode()).position());
  }
}

function layoutBounds(map) {
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const node of map.nodes()) {
    const p = node.position();
    minX = Math.min(minX, p.x);
    minY = Math.min(minY, p.y);
    maxX = Math.max(maxX, p.x);
    maxY = Math.max(maxY, p.y);
  }
  return { x: minX - 70, y: minY - 50, width: maxX - minX + 140, height: maxY - minY + 100 };
}

function distanceToSegment(p, a, b) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  if (dx === 0 && dy === 0) {
    return Math.hypot(p.x - a.x, p.y - a.y);
  }
  let u = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy);
  u = Math.max(0, Math.min(1, u));
  return Math.hypot(p.x - (a.x + u * dx), p.y - (a.y + u * dy));
}

function ease(t) {
  return t * t * (3 - 2 * t);
}

function containsEdge(path, link) {
  for (let i = 0; i < path.length - 1; i++) {
    const u = path[i];
    const v = path[i + 1];
    if (link.connects(u) && link.connects(v)) {
      return true;
    }
  }
  return false;
}
