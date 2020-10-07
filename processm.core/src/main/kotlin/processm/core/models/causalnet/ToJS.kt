package processm.core.models.causalnet

import java.io.File
import java.io.OutputStream
import java.io.PrintWriter

fun CausalNet.toJS(path: String) {
    println("SERIALIZING TO $path")
    File(path).outputStream().use { toJS(it) }
}

fun CausalNet.toJS(out: OutputStream) {
    PrintWriter(out).use { it.print(this.toJS()) }
}

fun CausalNet.toJS(): String {
    val nodesList =
        ArrayList(instances.map { node -> "{id: \"${node.activity}\", label: \"${node.activity}\"}" })
    val nodesOnEdge = HashMap<Dependency, MutableList<String>>()
    var ctr = 0
    val bindingToNodes = HashMap<Binding, HashMap<Dependency, Int>>()
    for (binding in splits.values.flatten() + joins.values.flatten()) {
        val tmp = HashMap<Dependency, Int>()
        for (dep in binding.dependencies) {
            if (!nodesOnEdge.containsKey(dep))
                nodesOnEdge[dep] = ArrayList()
            val group = if (binding is Split) "split" else "join"
            nodesOnEdge.getValue(dep).add(ctr.toString())
            nodesList.add("{id: $ctr, group: '$group'}")
            tmp[dep] = ctr
            ctr += 1
        }
        bindingToNodes[binding] = tmp
    }
    val nodes = nodesList.joinToString(separator = ",\n")

    val edgesList = ArrayList<String>()
    for (dep in this.outgoing.values.flatten()) {
        var prev = dep.source.activity
        val i = nodesOnEdge.getOrDefault(dep, ArrayList()).iterator()
        while (i.hasNext()) {
            var curr = i.next()
            edgesList.add("{from: \"$prev\", to: \"$curr\"}")
            prev = curr
        }
        edgesList.add("{from: \"$prev\", to: \"${dep.target.activity}\", arrows: \"to\"}")
    }
    for (binding in splits.values.flatten() + joins.values.flatten()) {
        val tmp = bindingToNodes.getValue(binding)
        val i = binding.dependencies.iterator()
        var prev = i.next()
        while (i.hasNext()) {
            val curr = i.next()
            val from = tmp.getValue(prev)
            val to = tmp.getValue(curr)
            val color = if (binding is Split) "red" else "green"
            edgesList.add("{from: \"$from\", to: \"$to\", color: '$color', physics: false}")
            prev = curr
        }
    }

    val edges = edgesList.joinToString(separator = ",\n")

    return """
        <html>
        <body>
<script type="text/javascript" src="https://unpkg.com/vis-util@latest"></script>
<script type="text/javascript" src="https://unpkg.com/vis-data@latest"></script>
<script type="text/javascript" src="https://unpkg.com/vis-network@latest/peer/umd/vis-network.min.js"></script>
<link rel="stylesheet" type="text/css" href="https://unpkg.com/vis-network/styles/vis-network.min.css" />
<!-- You may include other packages like Vis Timeline or Vis Graph3D here. -->

<div id="mynetwork"></div>
<script type="text/javascript">
var nodes = new vis.DataSet([
$nodes
]);
      var edges = new vis.DataSet([
$edges
]);
        // create a network
          var container = document.getElementById("mynetwork");
          var data = {
            nodes: nodes,
            edges: edges
          };
          var options = {
          groups: {
          split: {color:{background:'red'}, size: 5, shape: 'dot'},
          join: {color:{background:'green'}, size: 5, shape: 'dot'}
          }
          };
          var network = new vis.Network(container, data, options); 
        </script> 
        </body>
        </html>
    """.trimIndent()
}