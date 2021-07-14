package processm.experimental.helpers

@ExperimentalUnsignedTypes
class BitIntSet(private val n:Int):MutableSet<Int> {

    private class BitIntSetIterator(private val base: BitIntSet):MutableIterator<Int> {
        private var cell = -1
        private var shift = 0
        private var cellValue = 0UL
        private var next:Int = prepareNext()

        private fun prepareNext():Int {
            while(cellValue == 0UL) {
                cell ++
                if(cell >= base.data.size)
                    return -1
                cellValue = base.data[cell]
                shift = 0
            }
            //System.out.println("cell=$cell cellValue=$cellValue shift=$shift")
//            assert(shift < ULong.SIZE_BITS) {"cellValue=$cellValue cell=$cell shift=$shift n=${base.n} data=${base.data}"}
//            assert(cellValue != 0UL)
//            assert(cell < base.data.size)
            val x = cellValue.countTrailingZeroBits() + 1
            cellValue =  if(x<ULong.SIZE_BITS) cellValue shr x else 0UL
            shift += x
//            assert(shift <= ULong.SIZE_BITS) {"x=$x cell=$cell shift=$shift n=${base.n} data=${base.data}"}
            val result =cell*ULong.SIZE_BITS + shift - 1
//            assert(0 <= result && result < base.n) {"cell=$cell shift=$shift n=${base.n} result=$result data=${base.data}"}
            return result
        }

        override fun hasNext(): Boolean = next >= 0

        override fun next(): Int {
//            check(next >= 0)
            val oldNext = next
            next = prepareNext()
            return oldNext
        }

        override fun remove() {
            TODO("Not yet implemented")
        }

    }

    private val data=ULongArray(n/ULong.SIZE_BITS + 1)
    private var mutableSize = 0
    override val size: Int
        get() = mutableSize

    companion object {
        //@JvmStatic
        private val SHIFT = 6 // log2 of ULong.SIZE_BITS
        //@JvmStatic
        private val BIT_MASK = (1 shl SHIFT)-1
    }

    private fun cell(element: Int) = element shr SHIFT
    private fun bit(element: Int) = element and BIT_MASK
    private fun mask(element: Int) = 1UL shl bit(element)

    override fun contains(element: Int): Boolean = (data[cell(element)] and mask(element)) != 0UL

    override fun containsAll(elements: Collection<Int>): Boolean {
        if(elements is BitIntSet && elements.data.size <= data.size) {
            var i=0
            while(i<elements.data.size) {
                if(data[i] and elements.data[i] != elements.data[i])
                    return false
                i++
            }
            return true
        }
        for(i in elements)
            if(!contains(i))
                return false
        return true
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): MutableIterator<Int> = BitIntSetIterator(this)

    override fun add(element: Int): Boolean {
        require(element in 0 until n)
        val cell = cell(element)
        val mask = mask(element)
        val old = data[cell]
        data[cell] = data[cell] or mask
        if((old and mask) == 0UL) {
            mutableSize ++
            return true
        } else
            return false
    }

    override fun addAll(elements: Collection<Int>): Boolean {
        if(elements is BitIntSet) {
            if(isEmpty() && elements.n == this.n) {
                elements.data.copyInto(this.data)
                mutableSize = elements.size
                return true
            } else if(elements.n <= this.n) {
                val oldSize = size
                mutableSize = 0
                for(i in elements.data.indices) {
                    data[i] = data[i] or elements.data[i]
                    mutableSize += data[i].countOneBits()
                }
                return oldSize != mutableSize
            }
        }
        var result = false
        for(e in elements)
            if(add(e))
                result = true
        return result
    }

    override fun clear() {
        data.fill(0UL)
    }

    override fun remove(element: Int): Boolean {
        if(element > n)
            return false
        val cell = cell(element)
        val mask = mask(element)
        val old = data[cell]
        data[cell] = data[cell] and mask.inv()
        return (old and mask) != 0UL
    }

    override fun removeAll(elements: Collection<Int>): Boolean {
        if(elements is BitIntSet) {
            val end = kotlin.math.min(elements.data.size, data.size)
            val oldSize = size
            mutableSize = 0
            for(i in 0 until end) {
                data[i] = data[i] and elements.data[i].inv()
                mutableSize += data[i].countOneBits()
            }
            return oldSize != mutableSize
        }
        //TODO special handling if elements is BitIntSet
        var result = false
        for(e in elements)
            if(remove(e))
                result = true
        return result
    }

    override fun retainAll(elements: Collection<Int>): Boolean {
        TODO("Not yet implemented")
    }

}
