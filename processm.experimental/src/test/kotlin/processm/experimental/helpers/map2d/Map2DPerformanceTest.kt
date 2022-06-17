package processm.experimental.helpers.map2d

import com.google.common.collect.HashBasedTable
import org.junit.jupiter.api.Tag
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.map2d.Map2D
import java.io.File
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Ignore
import kotlin.test.Test

/**
 * By default Ignored - this is a performance comparison, not a real test. Waste of resources running it by default.
 *
 * To obtain a chart with performance comparison, set [statsDir] to a (possibly nonexisting) directory,
 * and then use the following Python code to draw the actual charts (setting the same `statsDir` as well):
 *
#!/usr/bin/env python3

import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path

statsDir = "charts"
dfs = []
order = []
for fn in Path(statsDir).glob("*.csv"):
df = pd.read_csv(fn, header=0)
test = fn.with_suffix('').name
df['test'] = test
df = df[df['n'] > df['n'].min()]    # The smallest values are prone to high variation. I suppose this is because they are first and some JVM magic is happening (gc? JIT?).
dfs.append(df)
order.append(test)
df = pd.concat(dfs, ignore_index=True)
sns.catplot(x="n", y="time", hue="name", kind="point", col="test", col_wrap=4, sharey=False, palette="bright", data=df)
plt.show()
 */
@Ignore
@Tag("performance")
class Map2DPerformanceTest {

    fun createMaps(nRows: Int, nCols: Int) = listOf(
        "Guava hash" to GuavaWrappingMap2D(HashBasedTable.create<String, String, String>()),
        "Doubling one update" to DoublingMap2DWithOneUpdate<String, String, String>(),
        "Doubling two updates" to DoublingMap2D<String, String, String>(),
        "Dense" to DenseMap2D<String, String, String>(nRows, nCols),
        "Reinventing" to WheelReinventingMap2D<String, String, String>(nRows * nCols / 3)
    )

    private val statsDir: String?

    init {
        statsDir = null
    }

    private fun measure(id: String, block: (Map2D<String, String, String>, List<Pair<String, String>>) -> Double) {
        val nReps = 10
        val ns = IntProgression.fromClosedRange(100, 2000, 100)
        // name -> n -> reps
        val times = HashMap<String, ArrayList<ArrayList<Double>>>()
        for ((nidx, n) in ns.withIndex()) {
            val m = n * n / 3
            val rnd = Random(42)
            val keys = (0 until m).map { rnd.nextInt(n).toString() to rnd.nextInt(n).toString() }
            for (rep in 0 until nReps) {
                for ((name, map) in createMaps(n, n)) {
                    val tmp = block(map, keys)
                    val target = times.getOrPut(name, { ArrayList() })
                    while (target.size <= nidx)
                        target.add(ArrayList())
                    target[nidx].add(tmp)
                }
            }
        }
        for ((name, mapTimes) in times) {
            val avgs = mapTimes.map {
                val avg = it.average()
                val avg2 = it.map { x -> x * x }.average()
                avg to sqrt(avg2 - avg * avg)
            }
            println("$name $avgs")
        }
        if (statsDir != null) {
            val dir = File(statsDir)
            dir.mkdirs()
            if (dir.isDirectory) {   //We don't really care whether the directory was created, only if it exists
                File(dir, "$id.csv").printWriter().use { writer ->
                    writer.println("name,n,repetition,time")
                    for ((name, mapTimes) in times)
                        for ((n, repTimes) in ns zip mapTimes) {
                            for ((idx, t) in repTimes.withIndex())
                                writer.println("\"$name\",$n,$idx,$t")
                        }
                }
            }
        }
    }

    @Test
    fun get() {
        measure("get") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            measureNanoTime {
                for ((x, y) in keys)
                    check(map[x, y] != null)
            } / keys.size.toDouble()
        }
    }

    @Test
    fun `dense read - sequential`() {
        measure("dense-sequential") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            val rows = keys.map { it.first }.toSet()
            val cols = keys.map { it.second }.toSet()
            val m = rows.size * cols.size
            measureNanoTime {
                for (x in rows)
                    for (y in cols)
                        map.get(x, y)
            } / m.toDouble()
        }
    }

    @Test
    fun `dense read - by row`() {
        measure("dense-byrow") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            val rows = keys.map { it.first }.toSet()
            val cols = keys.map { it.second }.toSet()
            val m = rows.size * cols.size
            measureNanoTime {
                for (x in rows) {
                    val row = map.getRow(x)
                    for (y in cols)
                        row.get(y)
                }
            } / m.toDouble()
        }
    }

    @Test
    fun `dense read - by column`() {
        measure("dense-bycol") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            val rows = keys.map { it.first }.toSet()
            val cols = keys.map { it.second }.toSet()
            val m = rows.size * cols.size
            measureNanoTime {
                for (y in cols) {
                    val col = map.getColumn(y)
                    for (x in rows)
                        col.get(x)
                }
            } / m.toDouble()
        }
    }

    @Test
    fun insert() {
        measure("insert") { map, keys ->
            measureNanoTime {
                for ((x, y) in keys)
                    map[x, y] = x + y
            } / keys.size.toDouble()
        }
    }

    @Test
    fun update() {
        measure("update") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            measureNanoTime {
                for ((x, y) in keys)
                    map[x, y] = x + x + y
            } / keys.size.toDouble()
        }
    }

    @Test
    fun getColumn() {
        measure("getcolumn") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            var time = 0.0
            keys.groupBy { it.second }.forEach { (colidx, idx) ->
                val row = idx.map { it.first }
                time += measureNanoTime {
                    val col = map.getColumn(colidx)
                    for (i in row)
                        check(col[i] != null)
                }
            }
            time / keys.size.toDouble()
        }
    }

    @Test
    fun getRow() {
        measure("getrow") { map, keys ->
            for ((x, y) in keys)
                map[x, y] = x + y
            var time = 0.0
            keys.groupBy { it.first }.forEach { (rowidx, idx) ->
                val col = idx.map { it.second }
                time += measureNanoTime {
                    val row = map.getRow(rowidx)
                    for (i in col)
                        check(row[i] != null)
                }
            }
            time / keys.size.toDouble()
        }
    }
}
