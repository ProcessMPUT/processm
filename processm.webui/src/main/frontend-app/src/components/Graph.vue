<template>
  <div ref="graph" class="graph"></div>
</template>

<style scoped>
.graph {
  width: 100%;
  height: 100%;
}
</style>

<style>
.graph .g6-component-tooltip {
  max-width: 50em;
}

.graph .g6-component-tooltip table {
  margin: 0;
  padding: 0;
  border-collapse: collapse;
  border-spacing: 0;
}

.graph .g6-component-tooltip td {
  padding: 0;
}

.graph .g6-component-tooltip td:first-child {
  padding-right: 0.1em;
}
</style>

<script lang="ts">
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import G6, { GraphData, IGroup } from "@antv/g6";
import { EdgeConfig, NodeConfig } from "@antv/g6-core/lib/types";
import { waitForRepaint } from "@/utils/waitForRepaint";
import { AlignmentKPIReport, Distribution } from "@/openapi";

@Component({})
export default class Graph extends Vue {
  @Prop()
  readonly data!: (GraphData | CNetGraphData) & AlignmentKPIHolder;

  @Prop({ default: (edge: EdgeConfig) => true })
  readonly filterEdge!: (edge: EdgeConfig) => boolean;

  /**
   * Change the value of this property to refresh the component.
   */
  @Prop({ default: 0 })
  refresh!: number;

  graph: any;

  readonly edgeColor = "#424242";

  mounted() {
    // eslint-disable-next-line @typescript-eslint/no-this-alias
    const self = this;

    const numberFormat = Intl.NumberFormat(self.$i18n.locale, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
    const intFormat = Intl.NumberFormat(self.$i18n.locale, {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    });
    const dateFormat = Intl.DateTimeFormat(self.$i18n.locale);

    // wait until height of the component is calculated
    waitForRepaint(() => {
      // get the container size
      const container = this.$refs.graph as HTMLElement;
      if (container === undefined || self.data === undefined) return;
      const tooltip = new G6.Tooltip({
        offsetX: 10,
        offsetY: 10,
        itemTypes: ["node", "edge"],
        getContent: (e) => {
          const outDiv = document.createElement("div");
          const type = e?.item?.getType();
          const model = e?.item?.getModel();
          if (model === undefined || model.id === undefined) return outDiv;

          const format = (kpi: string, d: Distribution): string => {
            if (kpi == "time:timestamp") {
              const f = (d: number) => dateFormat.format(new Date(d));
              const days = d.standardDeviation! / 86400000;
              return `${f(d.average!)} &pm; ${numberFormat.format(days)} ${self.$t("statistics.days")} [${f(d.min!)}, ${f(d.max!)}]`;
            } else if (kpi == "urn:processm:statistics/count") {
              return intFormat.format(d.median!);
            } else {
              const f = (d: number) => numberFormat.format(d);
              const days = self.$t("statistics.days");
              return `${f(d.average!)} ${days} &pm; ${f(d.standardDeviation!)} ${days} [${f(d.min!)} ${days}, ${f(d.max!)} ${days}]`;
            }
          };

          switch (type) {
            case "node":
              outDiv.innerHTML = `<h4>${model.label}</h4>`;
              if (self.data.alignmentKPIReport === undefined) break;

              outDiv.innerHTML +=
                "<table>" +
                Object.entries(self.data.alignmentKPIReport.eventKPI)
                  .sort((kpi1, kpi2) => kpi1[0].localeCompare(kpi2[0]))
                  .map((kpi) => {
                    const label = self.$te(`statistics.${kpi[0]}`) ? self.$t(`statistics.${kpi[0]}`) : kpi[0];
                    const val = kpi[1][model.id!];
                    if (val !== undefined) return `<tr><td>${label}:</td><td>${format(kpi[0], val)}</td></tr>`;
                    else return "";
                  })
                  .join("") +
                "</table>";
              break;
            case "edge":
              outDiv.innerHTML = `<h4>${self.data?.nodes?.find((n) => n.id == model.source)?.label} → ${
                self.data?.nodes?.find((n) => n.id == model.target)?.label
              }</h4>`;
              if (self.data.alignmentKPIReport === undefined) break;

              outDiv.innerHTML +=
                "<table>" +
                Object.entries(self.data.alignmentKPIReport.arcKPI)
                  .sort((kpi1, kpi2) => kpi1[0].localeCompare(kpi2[0]))
                  .map((kpi) => {
                    const label = self.$te(`statistics.${kpi[0]}`) ? self.$t(`statistics.${kpi[0]}`) : kpi[0];
                    const val = kpi[1][model.id!];
                    if (val !== undefined) return `<tr><td>${label}</td><td>${format(kpi[0], val)}</td></tr>`;
                  })
                  .join("") +
                "</table>";
              break;
          }
          return outDiv;
        }
      });
      const graph = new G6.Graph({
        container: container, // String | HTMLElement, required, the id of DOM element or an HTML node
        width: container.offsetWidth, // Number, required, the width of the graph
        height: container.offsetHeight, // Number, required, the height of the graph
        fitView: true,
        fitCenter: true,
        plugins: [tooltip],
        modes: {
          default: ["drag-node"]
        },
        layout: {
          type: "comboCombined",
          preventOverlap: true,
          outerLayout: new G6.Layout["dagre"]({
            rankdir: "LR",
            nodesep: 20,
            ranksep: 50,
            controlPoints: true
          })
        },
        defaultNode: {
          type: "ellipse",
          size: [100, 25],
          logoIcon: {
            show: false
          },
          stateIcon: {
            show: false
          }
        },
        defaultEdge: {
          type: "quadraticWithBinding",
          style: {
            stroke: this.edgeColor,
            endArrow: {
              path: G6.Arrow.vee(10, 10, 0),
              //d: 0,
              fill: this.edgeColor,
              stroke: this.edgeColor
            }
          },
          labelCfg: {
            refY: 5
          }
        }
      });
      this.graph = graph;

      // define custom edge using G6.registerEdge
      const rainbow = [this.edgeColor, "#1976d2", "#363d32", "#9c7a84", "#b2543d", "#92a34c"];
      G6.registerEdge(
        "quadraticWithBinding",
        {
          afterDraw(cfg, group) {
            if (cfg === undefined || group === undefined) return;

            // to use coordinates of other edges we have to draw them first
            setTimeout(() => {
              const sourceNode = self.data.nodes?.find((node: NodeConfig) => node.id === cfg.source);
              const targetNode = self.data.nodes?.find((node: NodeConfig) => node.id === cfg.target);
              if (sourceNode === undefined || targetNode === undefined) return;

              const splits = (sourceNode as CNetNodeConfig).splits?.sort((b1, b2) => b1.length - b2.length);
              const joins = (targetNode as CNetNodeConfig).joins?.sort((b1, b2) => b1.length - b2.length);

              if (splits === undefined || joins === undefined) return;

              function drawArc(group: IGroup, pts: { x: number; y: number }[], color: string) {
                if (pts.length <= 1) return;

                pts.sort((p1, p2) => p1.x - p2.x);
                let candidates = pts.slice(1);
                const ltr = Array.of(pts[0]);
                let last = pts[0];
                while (candidates.length > 0) {
                  candidates.sort((p1, p2) => Math.hypot(last.x - p1.x, last.y - p1.y) - Math.hypot(last.x - p2.x, last.y - p2.y));
                  last = candidates.shift()!;
                  ltr.push(last);
                }

                pts.sort((p1, p2) => p1.y - p2.y);
                candidates = pts.slice(1);
                const ttb = Array.of(pts[0]);
                last = pts[0];
                while (candidates.length > 0) {
                  candidates.sort((p1, p2) => Math.hypot(last.x - p1.x, last.y - p1.y) - Math.hypot(last.x - p2.x, last.y - p2.y));
                  last = candidates.shift()!;
                  ttb.push(last);
                }

                const distanceLTR = ltr.reduce((distance, p, i) => distance + (i > 0 ? Math.hypot(ltr[i - 1].x - p.x, ltr[i - 1].y - p.y) : 0), 0);
                const distanceTTB = ttb.reduce((distance, p, i) => distance + (i > 0 ? Math.hypot(ttb[i - 1].x - p.x, ttb[i - 1].y - p.y) : 0), 0);

                const drawPts = distanceLTR < distanceTTB ? ltr : ttb;

                group!.addShape("path", {
                  attrs: {
                    path: drawPts.reduce((acc, p, i, a) => (i == 0 ? `M ${p.x},${p.y}` : `${acc} ${bezierCommand(p, i, a)}`), ""),
                    stroke: color,
                    lineWidth: 2,
                    lineAppendWidth: 2
                  }
                });
              }

              function drawPoints(group: IGroup, pts: { x: number; y: number }[], color: string) {
                for (const pt of pts) {
                  group.addShape("circle", {
                    attrs: {
                      r: 3,
                      fill: color,
                      x: pt.x,
                      y: pt.y
                    }
                  });
                }
              }

              // based on https://francoisromain.medium.com/smooth-a-svg-path-with-cubic-bezier-curves-e37b49d46c74
              function line(pointA: { x: number; y: number }, pointB: { x: number; y: number }) {
                const lengthX = pointB.x - pointA.x;
                const lengthY = pointB.y - pointA.y;
                return {
                  length: Math.sqrt(Math.pow(lengthX, 2) + Math.pow(lengthY, 2)),
                  angle: Math.atan2(lengthY, lengthX)
                };
              }

              function controlPoint(
                current: { x: number; y: number },
                previous?: { x: number; y: number },
                next?: {
                  x: number;
                  y: number;
                },
                reverse: boolean = false
              ) {
                // When 'current' is the first or last point of the array
                // 'previous' or 'next' don't exist.
                // Replace with 'current'
                const p = previous || current;
                const n = next || current;
                // The smoothing ratio
                const smoothing = 0.2;
                // Properties of the opposed-line
                const o = line(p, n);
                // If is end-control-point, add PI to the angle to go backward
                const angle = o.angle + (reverse ? Math.PI : 0);
                const length = o.length * smoothing;
                // The control point position is relative to the current point
                const x = current.x + Math.cos(angle) * length;
                const y = current.y + Math.sin(angle) * length;
                return { x: x, y: y };
              }

              function bezierCommand(point: { x: number; y: number }, i: number, a: { x: number; y: number }[]) {
                // start control point
                const cps = controlPoint(a[i - 1], a[i - 2], point);
                // end control point
                const cpe = controlPoint(point, a[i - 1], a[i + 1], true);
                return `C ${cps.x},${cps.y} ${cpe.x},${cpe.y} ${point.x},${point.y}`;
              }

              // draw binding points and arcs
              let splitPos = 0.025;
              for (const [index, split] of splits.entries()) {
                splitPos += Math.min(0.35 / (splits.length - 1), 0.05);

                if (targetNode.id != split[0]) continue; // we add binding arc to the first edge mentioned in the split only

                const allEdges = split.map((target) => graph.findById(`${sourceNode.id}->${target}`)).filter((edge) => !!edge);
                const visibleEdges = allEdges.filter((edge) => self.filterEdge(self.data.edges!.find((e) => e.id == edge.getID())!));

                if (visibleEdges.length != allEdges.length) continue;

                // draw split arc
                const allPts = visibleEdges.map((edge) => edge._cfg?.group?.get("children")[0].getPoint(splitPos));
                drawArc(group, allPts, rainbow[index % rainbow.length]);
                drawPoints(group, allPts, rainbow[index % rainbow.length]);
                group.setZIndex(index);
              }

              // we start further from the end of edge due to the arrow sign
              let joinPos = 0.95;
              for (const [index, join] of joins.entries()) {
                joinPos -= Math.min(0.35 / (joins.length - 1), 0.05);

                if (sourceNode.id != join[0]) continue; // we add binding arc to the first edge mentioned in the join only

                const allEdges = join.map((source) => graph.findById(`${source}->${targetNode.id}`)).filter((edge) => !!edge);
                const visibleEdges = allEdges.filter((edge) => self.filterEdge(self.data.edges!.find((e) => e.id == edge.getID())!));

                if (visibleEdges.length != allEdges.length) continue;

                // draw join arc
                const allPts = visibleEdges.map((edge) => edge._cfg?.group?.get("children")[0].getPoint(joinPos));
                drawArc(group, allPts, rainbow[index % rainbow.length]);
                drawPoints(group, allPts, rainbow[index % rainbow.length]);
                group.setZIndex(index);
              }
            }, 0);
          },
          update: undefined
        },
        "quadratic"
      );

      this.markSelfLoops(self.data);
      this.calcSize(self.data);
      this.calcLayers(self.data);

      this.graph.data(self.data); // Load the data
      this.graph.render(); // Render the graph

      this.updateEdges();
    });
  }

  markSelfLoops(data: GraphData) {
    if (data.edges === undefined) return;
    const selfLoops = data.edges!.filter((edge) => edge.source === edge.target);
    for (const edge of selfLoops) {
      edge.type = "loop";
    }
  }

  calcSize(data: GraphData) {
    if (data.nodes === undefined || data.nodes!.some((node) => node.size !== undefined)) return;

    for (const node of data.nodes!) {
      const chars = node.label!.toString().length;
      if (chars > 20) {
        node.size = [5 * chars + Math.log2(chars), 25];
      }
    }
  }

  calcLayers(data: GraphData) {
    if (data.nodes === undefined || data.nodes!.some((node) => node.layer !== undefined)) return;
    // begin with start nodes
    const queue = data.nodes!.filter((node) => !data.edges!.some((edge) => edge.source !== node.id && edge.target === node.id));
    for (let node of queue) {
      node.layer = 0;
    }

    while (queue.length > 0) {
      const node = queue.shift()!;
      const followers = data
        .edges!.filter((edge) => edge.source === node.id)
        .map((edge) => data.nodes!.find((node) => node.id === edge.target)!)
        .filter((node) => node.layer === undefined);
      for (const follower of followers) {
        follower.layer = (node.layer as number) + 1;
      }
      queue.push(...followers);
    }

    // move final nodes to the last layer
    const maxLayer = Math.max(...data.nodes!.map((node) => node.layer as number)) + 1;
    const final = data.nodes!.filter((node) => !data.edges!.some((edge) => edge.source === node.id && edge.target !== node.id));
    for (const node of final) {
      node.layer = maxLayer;
    }

    for (const node of data.nodes!) {
      node.comboId = "" + node.layer;
    }
  }

  updateEdges() {
    if (this.data.edges === undefined) return;
    for (const edge of this.data.edges!) {
      if (!edge.id) throw new Error("Missing edge id!");
      const edgeObj = this.graph.findById(edge.id);
      const visible = this.filterEdge(edge);
      if (visible) edgeObj.show();
      else edgeObj.hide();
    }
  }

  @Watch("refresh")
  update() {
    if (this.graph === undefined) return;
    this.updateEdges();
    this.graph.refresh();
  }
}

export interface CNetGraphData extends GraphData {
  nodes?: CNetNodeConfig[];
}

export interface CNetNodeConfig extends NodeConfig {
  joins: string[][];
  splits: string[][];
}

export interface AlignmentKPIHolder {
  alignmentKPIReport?: AlignmentKPIReport;
}
</script>
