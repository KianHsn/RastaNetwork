import { JavaRandom } from "../rng.js";
import { Theme } from "../theme.js";
import { BellmanFordSession, FailResult, Phase } from "../controller/BellmanFordSession.js";
import { CheckResult, GameSession, SelectResult } from "../controller/GameSession.js";
import { NetworkCanvas } from "./NetworkCanvas.js";
import { RoutingTablePanel } from "./RoutingTablePanel.js";

export class App {
  constructor(root) {
    this.root = root;
    this.canvas = new NetworkCanvas(root.querySelector("#network-canvas"));
    this.tablePanel = new RoutingTablePanel(root.querySelector("#table-panel"));
    this.progressLabel = root.querySelector("#progress-label");
    this.statusLabel = root.querySelector("#status-label");
    this.helpLabel = root.querySelector("#help-label");
    this.wordmark = root.querySelector("#wordmark");
    this.sizeBox = root.querySelector("#size-box");
    this.routingMode = root.querySelector("#routing-mode");
    this.bfMode = root.querySelector("#bf-mode");
    this.newGameButton = root.querySelector("#new-game");
    this.checkButton = root.querySelector("#check-table");
    this.hintButton = root.querySelector("#show-neighbors");
    this.statusAccent = root.querySelector("#status-accent");
    this.modal = document.querySelector("#modal");
    this.modalText = document.querySelector("#modal-text");
    this.modalOk = document.querySelector("#modal-ok");
    this.titleEl = document.querySelector("title");
    this.bellmanFord = true;
    this.session = null;
    this.hopSession = null;
    this.bfSession = null;

    this.canvas.onNodeClicked = (id) => this.onNodeClicked(id);
    this.canvas.onLinkClicked = (link) => this.onLinkClicked(link);
    this.routingMode.addEventListener("click", () => this.setMode(false));
    this.bfMode.addEventListener("click", () => this.setMode(true));
    this.newGameButton.addEventListener("click", () => this.newGame());
    this.checkButton.addEventListener("click", () => this.checkTable());
    this.hintButton.addEventListener("click", () => this.showNeighbors());
    this.modalOk.addEventListener("click", () => this.hideModal());
    this.modal.addEventListener("click", (e) => {
      if (e.target === this.modal) {
        this.hideModal();
      }
    });

    this.canvas.start();
    try {
      this.newGame();
    } catch (err) {
      console.error(err);
      if (this.statusLabel) {
        this.statusLabel.textContent = String(err && err.message ? err.message : err);
      }
    }
  }

  modeAccent() {
    return this.bellmanFord ? Theme.VIOLET : Theme.CYAN;
  }

  setMode(bf) {
    if (this.bellmanFord === bf && this.session != null) {
      return;
    }
    this.bellmanFord = bf;
    this.newGame();
  }

  applyModeChrome() {
    const accent = this.modeAccent();
    this.routingMode.classList.toggle("selected", !this.bellmanFord);
    this.bfMode.classList.toggle("selected", this.bellmanFord);
    this.routingMode.style.setProperty("--accent", Theme.CYAN.css());
    this.bfMode.style.setProperty("--accent", Theme.VIOLET.css());
    this.newGameButton.style.setProperty("--accent", accent.css());
    this.checkButton.style.setProperty("--accent", Theme.LIME.css());
    this.hintButton.style.setProperty("--accent", accent.css());
    this.wordmark.style.color = accent.css();
    this.statusAccent.style.background = accent.css();
    this.root.style.setProperty("--mode-accent", accent.css());
  }

  setStatus(text, accent) {
    this.statusLabel.textContent = text;
    this.statusAccent.style.background = accent.css();
  }

  showModal(text) {
    this.modalText.textContent = text;
    this.modal.classList.remove("hidden");
  }

  hideModal() {
    this.modal.classList.add("hidden");
  }

  newGame() {
    try {
      this._newGame();
    } catch (err) {
      console.error(err);
      if (this.statusLabel) {
        this.statusLabel.textContent = String(err && err.message ? err.message : err);
      }
    }
  }

  _newGame() {
    const n = Number(this.sizeBox.value);
    const seed = new JavaRandom().nextLong();
    this.applyModeChrome();
    if (this.bellmanFord) {
      this.hopSession = null;
      this.bfSession = new BellmanFordSession(n, seed);
      this.session = this.bfSession;
      if (this.titleEl) {
        this.titleEl.textContent = "Bellman-Ford Routing Game";
      }
      this.checkButton.textContent = "Check update";
      this.helpLabel.innerHTML = "<b class='help-bf'>Bellman-Ford recovery</b><br>"
        + "1. Click a router to inspect its table.<br>"
        + "2. Click a <b>weighted link</b> to fail it.<br>"
        + "3. Red rows used that link — pick neighbor <b>n</b> with the lowest "
        + "<span class='help-gold'>weight + remaining cost</span>.";
      this.setStatus("Hover a link to see its weight, then click one to fail it.", Theme.VIOLET);
      this.canvas.setBanner("Fail a link", "then repair every red row");
    } else {
      this.bfSession = null;
      this.hopSession = new GameSession(n, seed);
      this.session = this.hopSession;
      if (this.titleEl) {
        this.titleEl.textContent = "Network Routing Table Game";
      }
      this.checkButton.textContent = "Check table";
      this.helpLabel.innerHTML = "<b class='help-rt'>Hop-by-hop routing</b><br>"
        + "Click a router. For each host, pick the <b>next router</b> that is closer "
        + "to that destination, then press <span class='help-lime'>Check table</span>.";
      this.setStatus(`Packet at ${this.session.map().node(this.session.map().sourceId()).label()}. Configure level ${this.hopSession.unlockedLevel()} routers.`, Theme.CYAN);
      this.canvas.setBanner("Route the packet", "fill tables from the source outward");
    }
    this.canvas.setSession(this.session);
    this.tablePanel.setSession(this.session);
    this.refreshProgress();
    this.canvas.showToast(this.bellmanFord ? "New Bellman-Ford network" : "New routing network", this.modeAccent());
  }

  onNodeClicked(nodeId) {
    if (this.canvas.isAnimating()) {
      return;
    }
    const sel = this.session.selectNode(nodeId);
    const node = this.session.map().node(nodeId);
    if (sel === SelectResult.HOST) {
      this.setStatus(`${node.label()} is a host. Fill tables on routers.`, Theme.AMBER);
    } else if (sel === SelectResult.LOCKED) {
      this.setStatus(`Locked. Complete level ${this.hopSession == null ? 1 : this.hopSession.unlockedLevel()} first.`, Theme.ROSE);
    } else if (sel === SelectResult.OK) {
      this.tablePanel.showNode(nodeId);
      if (this.session.showPacket()) {
        const told = this.session.toldNextHop(nodeId);
        if (told != null) {
          this.canvas.animatePreview(nodeId, told);
          this.setStatus(`Router ${node.label()} forwards to ${this.session.map().node(told).label()}.`, Theme.CYAN);
          return;
        }
      }
      if (this.bfSession != null && this.bfSession.phase() === Phase.PICK_LINK) {
        this.setStatus(`Inspecting ${node.label()}. Click a glowing link when you are ready to fail it.`, Theme.VIOLET);
        this.canvas.showToast(`Inspecting ${node.label()}`, Theme.VIOLET);
        return;
      }
      const done = this.session.isCompleted(nodeId);
      this.setStatus(
        done ? `Router ${node.label()} is already complete.` : `Editing ${node.label()}.`,
        done ? Theme.LIME : this.modeAccent(),
      );
      this.canvas.showToast(done ? `${node.label()} complete` : `Editing ${node.label()}`, done ? Theme.LIME : this.modeAccent());
    }
  }

  onLinkClicked(link) {
    if (this.bfSession == null || this.canvas.isAnimating()) {
      return;
    }
    const result = this.bfSession.failLink(link);
    if (result === FailResult.ALREADY_FAILED) {
      this.setStatus("A link already failed. Update the red rows, or start a new network.", Theme.AMBER);
    } else if (result === FailResult.DISCONNECTS) {
      this.showModal("That link is a bridge — removing it disconnects the network.\nPick another link.");
    } else if (result === FailResult.NOT_USED) {
      this.showModal("No current shortest-path table uses that link.\nPick a link that some router actually forwards on.");
    } else if (result === FailResult.OK) {
      this.setStatus(`Failed ${this.bfSession.formatLink(link)}. Red rows must be updated with Bellman-Ford.`, Theme.ROSE);
      this.canvas.setBanner("Link failed", this.bfSession.formatLink(link));
      this.canvas.showToast("Link failed — repair red rows", Theme.ROSE);
      let nodeId = this.tablePanel.editingNode();
      if (nodeId == null || this.bfSession.isCompleted(nodeId)) {
        for (const router of this.bfSession.map().routers()) {
          if (!this.bfSession.isCompleted(router.id())) {
            nodeId = router.id();
            this.bfSession.selectNode(nodeId);
            break;
          }
        }
      }
      if (nodeId != null) {
        this.tablePanel.showNode(nodeId);
      }
      this.refreshProgress();
      this.showModal(
        `Link failed: ${this.bfSession.formatLink(link)}\n\n`
        + `${this.bfSession.affectedRouterCount()} router table(s) used that link.\n`
        + "Red rows need a new next hop: choose neighbor n that minimizes\n"
        + "weight(you, n) + cost(n → destination).",
      );
    }
  }

  checkTable() {
    const nodeId = this.tablePanel.editingNode();
    if (this.session == null || nodeId == null) {
      this.showModal("Click a router first.");
      return;
    }
    if (this.bfSession != null && this.bfSession.phase() === Phase.PICK_LINK) {
      this.showModal("Click a link on the map to fail it first.");
      return;
    }
    if (this.session.isCompleted(nodeId)) {
      this.showModal("That table is already accepted.");
      return;
    }
    if (this.canvas.isAnimating()) {
      return;
    }
    const player = this.tablePanel.readPlayerTable();
    const result = this.session.check(nodeId, player);
    this.refreshProgress();
    if (result === CheckResult.INCOMPLETE) {
      this.setStatus("Pick a next router for every destination that needs an update.", Theme.AMBER);
      this.canvas.showToast("Table incomplete", Theme.AMBER);
      this.showModal("The table is incomplete.");
    } else if (result === CheckResult.WRONG_HOP || result === CheckResult.WRONG) {
      if (this.bfSession != null) {
        this.setStatus("Pick the neighbor with the lowest weight + remaining cost.", Theme.ROSE);
        this.canvas.showToast("Not the Bellman-Ford hop", Theme.ROSE);
        this.showModal("Not the Bellman-Ford next hop.\nChoose neighbor n that minimizes weight(you, n) + cost(n → dest).");
      } else {
        this.setStatus("Next router must be a neighbor closer to that host.", Theme.ROSE);
        this.canvas.showToast("Illegal next router", Theme.ROSE);
        this.showModal("Illegal next router.\nPick a neighbor that is closer to the destination host.");
      }
    } else if (result === CheckResult.CORRECT) {
      this.tablePanel.showNode(nodeId);
      this.canvas.showToast("Table accepted", Theme.LIME);
      if (this.hopSession != null) {
        const hops = this.hopSession.takePendingHops();
        if (hops.length > 0) {
          this.setStatus("Packet forwarding…", Theme.GOLD);
          this.canvas.animateHops(hops, (id) => this.hopSession.arriveAt(id), () => this.afterPacketMoved());
        } else {
          const told = this.session.toldNextHop(nodeId);
          if (told != null) {
            this.canvas.animatePreview(nodeId, told);
          }
          this.afterPacketMoved();
        }
      } else if (this.bfSession != null && this.bfSession.phase() === Phase.DONE) {
        this.setStatus("All broken rows updated.", Theme.LIME);
        this.canvas.setBanner("Network recovered", "every red row follows the new shortest paths");
        this.showModal("Every red row now follows the new Bellman-Ford shortest paths.");
      } else if (this.bfSession != null) {
        this.setStatus(`Table updated. ${this.bfSession.remainingRepairs()} router(s) still have red rows.`, Theme.VIOLET);
        this.canvas.setBanner("Keep repairing", `${this.bfSession.remainingRepairs()} router(s) left`);
      }
    }
  }

  afterPacketMoved() {
    this.refreshProgress();
    if (this.hopSession == null) {
      return;
    }
    if (this.hopSession.packetArrived()) {
      if (this.hopSession.playerPathSuboptimal()) {
        this.setStatus("Arrived. Orange = yours, dashed green = shortest.", Theme.AMBER);
        this.canvas.setBanner("Arrived", "your path vs shortest path");
        this.showModal(
          "Packet reached the destination on another path.\n\n"
          + `Your path: ${this.hopSession.formatPath(this.hopSession.playerPath())}\n`
          + `Shortest:  ${this.hopSession.formatPath(this.hopSession.optimalPath())}`,
        );
      } else {
        this.setStatus("Packet arrived.", Theme.LIME);
        this.canvas.setBanner("Packet arrived", this.hopSession.formatPath(this.hopSession.playerPath()));
        this.canvas.showToast("Packet arrived", Theme.LIME);
        this.showModal(`Packet arrived.\n${this.hopSession.formatPath(this.hopSession.playerPath())}`);
      }
      return;
    }
    if (this.hopSession.allDone()) {
      this.setStatus(`All tables done. Packet still at ${this.hopSession.map().node(this.hopSession.packetNode()).label()}.`, Theme.AMBER);
    } else {
      this.setStatus(
        `Packet at ${this.hopSession.map().node(this.hopSession.packetNode()).label()}. Level ${this.hopSession.unlockedLevel()} · ${this.hopSession.remainingOnCurrentLevel()} router(s) left.`,
        Theme.CYAN,
      );
      this.canvas.setBanner(
        `Packet at ${this.hopSession.map().node(this.hopSession.packetNode()).label()}`,
        `level ${this.hopSession.unlockedLevel()} · ${this.hopSession.remainingOnCurrentLevel()} left`,
      );
    }
  }

  showNeighbors() {
    const nodeId = this.tablePanel.editingNode();
    if (this.session == null || nodeId == null) {
      this.showModal("Click a router first.");
      return;
    }
    if (this.bfSession != null) {
      this.showModal(this.bfSession.neighborHint(nodeId));
      return;
    }
    const lines = [`Neighbors of ${this.session.map().node(nodeId).label()}:`];
    for (const hop of this.session.map().neighborIds(nodeId)) {
      lines.push(`  ${this.session.map().node(hop).label()}`);
    }
    this.showModal(lines.join("\n"));
  }

  refreshProgress() {
    if (this.bfSession != null) {
      if (this.bfSession.failedLink() == null) {
        this.progressLabel.textContent = "Click a link to fail it";
        this.canvas.setBanner("Fail a link", "hover a weighted edge, then click");
        return;
      }
      const done = this.bfSession.affectedRouterCount() - this.bfSession.remainingRepairs();
      this.progressLabel.textContent = `${this.bfSession.formatLink(this.bfSession.failedLink())}   ${done}/${this.bfSession.affectedRouterCount()}`;
      return;
    }
    this.progressLabel.textContent = `PKT ${this.hopSession.map().node(this.hopSession.packetNode()).label()}   Lv ${this.hopSession.unlockedLevel()}/${this.hopSession.map().maxRouterLevel()}   ${this.hopSession.completedNodes().size}/${this.hopSession.map().routers().length}`;
  }
}
