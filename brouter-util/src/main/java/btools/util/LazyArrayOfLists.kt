package btools.util

/**
 * Behaves like an Array of list
 * with lazy list-allocation at getList
 *
 * @author ab
 */
class LazyArrayOfLists<E>(size: Int) {
    private val lists = ArrayList<ArrayList<E?>?>(size)

    init {
        for (i in 0..<size) {
            lists.add(null)
        }
    }

    fun getList(idx: Int): MutableList<E?> {
        var list = lists[idx]
        if (list == null) {
            list = ArrayList()
            lists[idx] = list
        }
        return list
    }

    fun getSize(idx: Int): Int {
        val list: MutableList<E?>? = lists[idx]
        return list?.size ?: 0
    }

    fun trimAll() {
        for (idx in lists.indices) {
            val list = lists[idx]
            list?.trimToSize()
        }
    }
}
