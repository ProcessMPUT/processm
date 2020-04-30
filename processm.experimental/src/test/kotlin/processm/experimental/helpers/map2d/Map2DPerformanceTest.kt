package processm.experimental.helpers.map2d

import processm.core.helpers.map2d.Map2D
import java.io.File
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test

class Map2DPerformanceTest {

    fun createMaps(nRows: Int, nCols: Int) = listOf(
//            "Wrapping with hashmap" to WrappingMap2D<String, String, String>(),
//            "Doubling one update" to DoublingMap2DWithOneUpdate<String, String, String>(),
//            "Guava hash" to GuavaWrappingMap2D(HashBasedTable.create<String, String, String>()),
//            "Guava tree" to GuavaWrappingMap2D(TreeBasedTable.create<String, String, String>()),

//            "Doubling two updates" to DoublingMap2DWithTwoUpdates<String, String, String>(),
            "Dense" to DenseMap2D<String, String, String>(nRows, nCols)
//            "Reinventing" to WheelReinventingMap2D<String, String, String>(nRows * nCols / 3)
    )

    private val statsDir: String?

    init {
        statsDir = null
//        statsDir = "charts"
    }

    private fun measure(id: String, block: (Map2D<String, String, String>, List<Pair<String, String>>) -> Double) {
        val nReps = 10
        val ns = IntProgression.fromClosedRange(100, 1000, 100)
//        val ns = IntProgression.fromClosedRange(100, 2000, 100)
//        val ns = IntProgression.fromClosedRange(100, 500, 200)
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

//    fun `doubling map performance`() {
//        val n=100000
//
//    }
}