import { RoutingEntry } from "../model/RoutingEntry.js";

export class RoutingTablePanel {
  constructor(root) {
    this.root = root;
    this.session = null;
    this._editingNode = null;
    this.titleEl = root.querySelector("[data-table-title]");
    this.badgeEl = root.querySelector("[data-table-badge]");
    this.hintEl = root.querySelector("[data-table-hint]");
    this.headEl = root.querySelector("[data-table-head]");
    this.bodyEl = root.querySelector("[data-table-body]");
  }

  setSession(session) {
    this.session = session;
    this._editingNode = null;
    this.bodyEl.innerHTML = "";
    this.renderHead();
    this.titleEl.textContent = "Select a router";
    this.badgeEl.textContent = "idle";
    this.hintEl.textContent = "Click a glowing router on the map";
  }

  editingNode() {
    return this._editingNode;
  }

  showNode(nodeId) {
    this._editingNode = nodeId;
    const map = this.session.map();
    const node = map.node(nodeId);
    this.titleEl.textContent = node.label();
    this.badgeEl.textContent = this.badgeFor(node);
    this.hintEl.textContent = this.hintFor(node);
    this.renderHead();
    const hops = this.session.neighborChoices(nodeId);
    const shown = this.session.visibleTable(nodeId);
    const costCol = this.session.showCost();
    this.bodyEl.innerHTML = "";
    for (const dest of map.hosts()) {
      let hop = "";
      let cost = "";
      if (shown != null) {
        const e = shown.get(dest.id());
        if (e != null) {
          hop = map.node(e.nextHop()).label();
          cost = String(e.cost());
        }
      }
      const editable = this.session.canEditRow(nodeId, dest.id());
      const broken = this.session.isRowBroken(nodeId, dest.id());
      const tr = document.createElement("tr");
      if (broken) {
        tr.classList.add("broken");
      }
      const destTd = document.createElement("td");
      destTd.textContent = dest.label();
      tr.appendChild(destTd);

      const hopTd = document.createElement("td");
      if (editable) {
        const select = document.createElement("select");
        select.className = "hop-select";
        const blank = document.createElement("option");
        blank.value = "";
        blank.textContent = "";
        select.appendChild(blank);
        for (const hopId of hops) {
          const opt = document.createElement("option");
          opt.value = map.node(hopId).label();
          opt.textContent = map.node(hopId).label();
          if (hop && opt.value === hop) {
            opt.selected = true;
          }
          select.appendChild(opt);
        }
        hopTd.appendChild(select);
      } else {
        hopTd.textContent = hop;
      }
      tr.appendChild(hopTd);

      if (costCol) {
        const costTd = document.createElement("td");
        costTd.textContent = cost;
        tr.appendChild(costTd);
      }
      this.bodyEl.appendChild(tr);
    }
  }

  readPlayerTable() {
    if (this._editingNode == null) {
      return new Map();
    }
    const map = this.session.map();
    const tableMap = new Map();
    const rows = [...this.bodyEl.querySelectorAll("tr")];
    for (const row of rows) {
      const destLabel = row.children[0].textContent;
      const hopCell = row.children[1];
      const select = hopCell.querySelector("select");
      const hopLabel = select ? select.value : hopCell.textContent.trim();
      if (!hopLabel) {
        continue;
      }
      try {
        const dest = labelToId(map, destLabel);
        const hop = labelToId(map, hopLabel);
        tableMap.set(dest, new RoutingEntry(dest, hop));
      } catch {
        // ignore unknown labels
      }
    }
    return tableMap;
  }

  renderHead() {
    const cost = this.session != null && this.session.showCost();
    this.headEl.innerHTML = cost
      ? "<tr><th>Destination</th><th>Next router</th><th>Cost</th></tr>"
      : "<tr><th>Destination</th><th>Next router</th></tr>";
  }

  badgeFor(node) {
    if (this.session.showCost()) {
      if (this.session.failedLink() == null) {
        return "inspect";
      }
      if (this.session.isCompleted(node.id())) {
        return "updated";
      }
      for (const dest of this.session.map().hosts()) {
        if (this.session.isRowBroken(node.id(), dest.id())) {
          return "repair";
        }
      }
      return "stable";
    }
    return this.session.isCompleted(node.id()) ? "accepted" : "edit";
  }

  hintFor(node) {
    if (this.session.showCost()) {
      if (this.session.failedLink() == null) {
        return "Inspect the table, then click a weighted link to fail it";
      }
      if (this.session.isCompleted(node.id())) {
        return "This router already uses the new shortest paths";
      }
      return "Red rows are broken — pick the cheapest neighbor";
    }
    if (this.session.isCompleted(node.id())) {
      return "Table accepted. The packet will follow this next hop";
    }
    return "Pick the next router for every destination, then Check";
  }
}

function labelToId(map, label) {
  for (const node of map.nodes()) {
    if (node.label() === label) {
      return node.id();
    }
  }
  throw new Error(`Unknown node ${label}`);
}
